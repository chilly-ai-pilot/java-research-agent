package com.chilly.researchagent.memory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

/** 将 {@link ChatMessage} 转为 Spring AI {@link Message}，供 {@link com.chilly.researchagent.react.GatewayChatService} 使用。 */
public final class ChatMessageConverter {

    private ChatMessageConverter() {
    }

    public static List<Message> toSpringAiMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream().map(ChatMessageConverter::toSpringAiMessage).toList();
    }

    private static Message toSpringAiMessage(ChatMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
            case SYSTEM -> new SystemMessage(message.content());
        };
    }
}
