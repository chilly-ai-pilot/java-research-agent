package com.chilly.researchagent.config;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;

import java.util.function.Consumer;

/**
 * Gateway 连接参数快照，供启动日志与异常消息复用。
 */
public record GatewaySettings(String baseUrl, String model, Double temperature) {

    /**
     * 从 Spring AI 配置与 ChatModel 生效选项解析 Gateway 参数。
     * effective 值为空时回退到 yml 配置，并通过 {@code warnCallback} 通知调用方。
     */
    public static GatewaySettings resolve(
            OpenAiConnectionProperties connectionProperties,
            OpenAiChatProperties chatProperties,
            ChatOptions effectiveOptions,
            Consumer<String> warnCallback) {
        String configuredModel = chatProperties.getOptions().getModel();
        Double configuredTemperature = chatProperties.getOptions().getTemperature();

        String model = effectiveOptions.getModel();
        if (model == null) {
            warn("Effective model is null, falling back to configured model: " + configuredModel, warnCallback);
            model = configuredModel;
        }

        Double temperature = effectiveOptions.getTemperature();
        if (temperature == null) {
            warn("Effective temperature is null, falling back to configured temperature: " + configuredTemperature,
                    warnCallback);
            temperature = configuredTemperature;
        }

        return new GatewaySettings(connectionProperties.getBaseUrl(), model, temperature);
    }

    /**
     * 有回调时发出降级警告，无回调则静默回退。
     */
    private static void warn(String message, Consumer<String> warnCallback) {
        if (warnCallback != null) {
            warnCallback.accept(message);
        }
    }
}
