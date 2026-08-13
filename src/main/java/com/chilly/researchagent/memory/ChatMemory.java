package com.chilly.researchagent.memory;

import java.util.List;

/**
 * 对话记忆：按 session 滑动窗口保留最近若干条消息。
 */
public interface ChatMemory {

    void add(String sessionId, ChatMessage message);

    List<ChatMessage> getRecent(String sessionId, int maxMessages);

    void clear(String sessionId);
}
