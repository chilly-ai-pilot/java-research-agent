package com.chilly.researchagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxSteps,
        long stepTimeoutMs,
        long totalTimeoutMs,
        int maxObservationChars,
        String systemPromptPath) {

    public static final String DEFAULT_SYSTEM_PROMPT_PATH = "prompts/system-react.txt";

    public AgentProperties {
        if (systemPromptPath == null || systemPromptPath.isBlank()) {
            systemPromptPath = DEFAULT_SYSTEM_PROMPT_PATH;
        }
    }
}
