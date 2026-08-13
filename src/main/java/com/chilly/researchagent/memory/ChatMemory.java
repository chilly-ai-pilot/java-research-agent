package com.chilly.researchagent.memory;

import java.util.List;

/**
 * 对话记忆：滑动窗口保留最近若干条消息。Step 2 将扩展为按 sessionId 隔离。
 */
public interface ChatMemory {

    void add(ChatMessage message);

    List<ChatMessage> getRecent(int maxMessages);

    void clear();
}
