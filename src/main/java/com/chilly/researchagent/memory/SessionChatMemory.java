package com.chilly.researchagent.memory;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 sessionId 隔离的对话记忆；缺省或空白 sessionId 映射为 {@value #DEFAULT_SESSION_ID}。
 */
@Component
public class SessionChatMemory implements ChatMemory {

    public static final String DEFAULT_SESSION_ID = "default";

    private final ChatMemoryProperties properties;
    private final ConcurrentMap<String, InMemoryChatMemory> sessions = new ConcurrentHashMap<>();

    public SessionChatMemory(ChatMemoryProperties properties) {
        this.properties = properties;
    }

    @Override
    public void add(String sessionId, ChatMessage message) {
        sessionFor(sessionId).add(message);
    }

    @Override
    public List<ChatMessage> getRecent(String sessionId, int maxMessages) {
        return sessionFor(sessionId).getRecent(maxMessages);
    }

    @Override
    public void clear(String sessionId) {
        sessionFor(sessionId).clear();
    }

    private InMemoryChatMemory sessionFor(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        return sessions.computeIfAbsent(normalizedSessionId, ignored -> new InMemoryChatMemory(properties));
    }

    public static String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return DEFAULT_SESSION_ID;
        }
        return sessionId;
    }
}
