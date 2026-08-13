package com.chilly.researchagent.react;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 通过 Go LLM Gateway（OpenAI 兼容协议）完成单轮对话封装。
 * Iteration 3 ReAct 循环与 Iteration 5 SSE 接口均复用此类。
 */
@Service
public class GatewayChatService {

    private final OpenAiChatModel chatModel;
    private final OpenAiConnectionProperties connectionProperties;
    private final GatewayExceptionTranslator exceptionTranslator;

    /**
     * 构造注入 OpenAiChatModel、Gateway 连接配置与异常翻译器。
     */
    public GatewayChatService(
            OpenAiChatModel chatModel,
            OpenAiConnectionProperties connectionProperties,
            GatewayExceptionTranslator exceptionTranslator) {
        this.chatModel = chatModel;
        this.connectionProperties = connectionProperties;
        this.exceptionTranslator = exceptionTranslator;
    }

    /**
     * 单轮非流式对话（无 system prompt、无历史）。
     */
    public String chat(String userMessage) {
        return chat(null, List.of(), userMessage);
    }

    /**
     * 非流式对话，支持 system prompt 与历史消息（Iteration 3/4 复用）。
     */
    public String chat(String systemPrompt, List<Message> history, String userMessage) {
        try {
            ChatResponse response = chatModel.call(buildPrompt(systemPrompt, history, userMessage));
            return extractText(response);
        } catch (RuntimeException e) {
            throw exceptionTranslator.translate(e, connectionProperties.getBaseUrl());
        }
    }

    /**
     * 单轮流式对话（无 system prompt、无历史）。
     */
    public Flux<String> chatStream(String userMessage) {
        return chatStream(null, List.of(), userMessage);
    }

    /**
     * 流式对话，支持 system prompt 与历史消息（Iteration 5 SSE 复用）。
     */
    public Flux<String> chatStream(String systemPrompt, List<Message> history, String userMessage) {
        Prompt prompt = buildPrompt(systemPrompt, history, userMessage);
        return Flux.defer(() -> {
                    Flux<ChatResponse> responseFlux = chatModel.stream(prompt);
                    if (responseFlux == null) {
                        // 编程错误：不翻译为 GatewayUnavailableException，与网络/Gateway 故障区分
                        throw new IllegalStateException("Gateway stream returned null Flux");
                    }
                    return responseFlux;
                })
                .map(this::extractStreamChunk)
                .filter(text -> !text.isEmpty())
                .onErrorMap(error -> exceptionTranslator.translate(error, connectionProperties.getBaseUrl()));
    }

    /**
     * 按 system → history → user 顺序组装 Prompt。
     */
    private Prompt buildPrompt(String systemPrompt, List<Message> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(userMessage));
        return new Prompt(messages);
    }

    /**
     * 从流式 ChatResponse chunk 提取文本；中间 chunk 可能为空，返回空串而非抛异常。
     */
    private String extractStreamChunk(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    /**
     * 从非流式 ChatResponse 提取非空 assistant 文本；空响应视为编程/协议错误。
     */
    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Gateway returned empty chat response");
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Gateway returned blank chat text");
        }
        return text;
    }
}
