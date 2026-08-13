package com.chilly.researchagent.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code agent.memory.*} 配置：滑动窗口大小与 session TTL（TTL 本迭代暂未实现）。
 */
@ConfigurationProperties(prefix = "agent.memory")
public record ChatMemoryProperties(int maxMessages, int sessionTtlMinutes) {

    public static final int DEFAULT_MAX_MESSAGES = 20;
    public static final int DEFAULT_SESSION_TTL_MINUTES = 60;

    public ChatMemoryProperties {
        if (maxMessages <= 0) {
            maxMessages = DEFAULT_MAX_MESSAGES;
        }
        if (sessionTtlMinutes <= 0) {
            sessionTtlMinutes = DEFAULT_SESSION_TTL_MINUTES;
        }
    }
}
