package com.chilly.researchagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * 确认 OpenAiChatModel 实际生效的 Gateway 连接参数与 application.yml 一致。
 * Iteration 0 的 auto-config 已足够装配 bean，此类只做启动日志对照。
 */
@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    private final OpenAiChatModel chatModel;
    private final Environment environment;

    public GatewayConfig(OpenAiChatModel chatModel, Environment environment) {
        this.chatModel = chatModel;
        this.environment = environment;
    }

    @PostConstruct
    void logEffectiveGatewaySettings() {
        ChatOptions options = chatModel.getDefaultOptions();
        String configuredBaseUrl = environment.getProperty("spring.ai.openai.base-url");
        String configuredModel = environment.getProperty("spring.ai.openai.chat.options.model");
        String configuredTemperature = environment.getProperty("spring.ai.openai.chat.options.temperature");

        log.info(
                "GatewayChatModel → baseUrl={}, model={}, temperature={} "
                        + "(yml: baseUrl={}, model={}, temperature={})",
                configuredBaseUrl,
                options.getModel(),
                options.getTemperature(),
                configuredBaseUrl,
                configuredModel,
                configuredTemperature);
    }
}
