package com.chilly.researchagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxSteps,
        long stepTimeoutMs,
        long totalTimeoutMs,
        int maxObservationChars) {
}
