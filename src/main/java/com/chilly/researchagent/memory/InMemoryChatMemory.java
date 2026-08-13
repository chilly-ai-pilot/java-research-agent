package com.chilly.researchagent.memory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 单 session 内存存储：{@link ArrayDeque} 按时间顺序存消息，超出 {@link ChatMemoryProperties#maxMessages()} 时丢弃最旧条目。
 * 由 {@link SessionChatMemory} 按 sessionId 组合多个实例。
 */
class InMemoryChatMemory {

    private final ChatMemoryProperties properties;
    private final Deque<ChatMessage> messages = new ArrayDeque<>();

    InMemoryChatMemory(ChatMemoryProperties properties) {
        this.properties = properties;
    }

    synchronized void add(ChatMessage message) {
        messages.addLast(message);
        trimToMaxMessages();
    }

    synchronized List<ChatMessage> getRecent(int maxMessages) {
        if (maxMessages <= 0) {
            return List.of();
        }
        int size = messages.size();
        if (size <= maxMessages) {
            return List.copyOf(messages);
        }
        return messages.stream()
                .skip((long) size - maxMessages)
                .toList();
    }

    synchronized void clear() {
        messages.clear();
    }

    private void trimToMaxMessages() {
        while (messages.size() > properties.maxMessages()) {
            messages.removeFirst();
        }
    }
}
