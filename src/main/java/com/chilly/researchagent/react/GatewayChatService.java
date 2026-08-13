package com.chilly.researchagent.react;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
     * 单轮非流式对话。
     */
    public String chat(String userMessage) {
        Prompt prompt = new Prompt(new UserMessage(userMessage));
        ChatResponse response = chatModel.call(prompt);
        return extractText(response);
    }

    /**
     * 单轮流式对话，逐 chunk 返回文本片段。
     */
    public Flux<String> chatStream(String userMessage) {
        Prompt prompt = new Prompt(new UserMessage(userMessage));
        return chatModel.stream(prompt)
                .map(this::extractText)
                .filter(text -> text != null && !text.isEmpty());
    }

    String gatewayUrl() {
        return gatewayUrl;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text != null ? text : "";
    }
}
