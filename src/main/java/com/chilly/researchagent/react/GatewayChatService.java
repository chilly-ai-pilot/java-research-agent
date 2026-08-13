package com.chilly.researchagent.react;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
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
    private final String gatewayUrl;

    public GatewayChatService(OpenAiChatModel chatModel, Environment environment) {
        this.chatModel = chatModel;
        this.gatewayUrl = environment.getProperty("spring.ai.openai.base-url", "http://localhost:8080");
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
            throw toGatewayUnavailable(e);
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
        return chatModel.stream(prompt)
                .map(this::extractText)
                .filter(text -> text != null && !text.isEmpty())
                .onErrorMap(this::toGatewayUnavailable);
    }

    String gatewayUrl() {
        return gatewayUrl;
    }

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

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }

    private GatewayUnavailableException toGatewayUnavailable(Throwable cause) {
        if (cause instanceof GatewayUnavailableException gatewayUnavailable) {
            return gatewayUnavailable;
        }
        if (!isGatewayFailure(cause)) {
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
        return new GatewayUnavailableException(
                gatewayUrl, extractHttpStatus(cause), summarizeCause(cause), cause);
    }

    private boolean isGatewayFailure(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof RestClientException
                    || current instanceof TransientAiException
                    || current instanceof NonTransientAiException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Integer extractHttpStatus(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof HttpStatusCodeException httpError) {
                return httpError.getStatusCode().value();
            }
            current = current.getCause();
        }
        return null;
    }

    private String summarizeCause(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
