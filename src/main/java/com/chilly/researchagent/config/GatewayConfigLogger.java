package com.chilly.researchagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时打印 OpenAiChatModel 实际生效的 Gateway 连接参数。
 */
@Component
public class GatewayConfigLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigLogger.class);

    private final OpenAiChatModel chatModel;
    private final OpenAiConnectionProperties connectionProperties;
    private final OpenAiChatProperties chatProperties;

    /**
     * 注入 Spring AI 自动装配的连接参数与 ChatModel。
     */
    public GatewayConfigLogger(
            OpenAiChatModel chatModel,
            OpenAiConnectionProperties connectionProperties,
            OpenAiChatProperties chatProperties) {
        this.chatModel = chatModel;
        this.connectionProperties = connectionProperties;
        this.chatProperties = chatProperties;
    }

    /**
     * 应用启动完成后打印 Gateway 生效配置。
     */
    @Override
    public void run(ApplicationArguments args) {
        GatewaySettings settings = GatewaySettings.resolve(
                connectionProperties, chatProperties, chatModel.getDefaultOptions(), log::warn);
        log.info(
                "Gateway effective → baseUrl={}, model={}, temperature={}",
                settings.baseUrl(),
                settings.model(),
                settings.temperature());
    }
}
