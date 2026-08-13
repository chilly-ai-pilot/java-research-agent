package com.chilly.researchagent.memory;

import java.time.Instant;

/**
 * 单条对话消息，供 {@link ChatMemory} 滑动窗口存储。
 */
public record ChatMessage(Role role, String content, Instant timestamp) {

    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    public ChatMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            content = "";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
