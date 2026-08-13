package com.chilly.researchagent.react;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 将 Spring AI / RestClient 异常翻译为 {@link GatewayUnavailableException}，
 * 与 LLM 业务逻辑解耦。
 */
@Component
public class GatewayExceptionTranslator {

    /**
     * 将底层异常翻译为 Gateway 语义异常；非 Gateway 故障则原样保留 RuntimeException 类型。
     */
    public RuntimeException translate(Throwable cause, String gatewayUrl) {
        if (cause instanceof GatewayUnavailableException gatewayUnavailable) {
            return gatewayUnavailable;
        }
        if (!isGatewayFailure(cause)) {
            return cause instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(cause);
        }
        Integer httpStatus = extractHttpStatus(cause);
        String causeSummary = summarizeCause(cause);
        return new GatewayUnavailableException(
                buildMessage(gatewayUrl, httpStatus, causeSummary),
                gatewayUrl,
                httpStatus,
                cause);
    }

    /**
     * 组装人类可读的 Gateway 不可用消息。
     */
    private String buildMessage(String gatewayUrl, Integer httpStatus, String causeSummary) {
        StringBuilder message = new StringBuilder("Gateway unavailable at ").append(gatewayUrl);
        if (httpStatus != null) {
            message.append(" (HTTP ").append(httpStatus).append(')');
        }
        if (causeSummary != null && !causeSummary.isBlank()) {
            message.append(": ").append(causeSummary);
        }
        return message.toString();
    }

    /**
     * 判断异常链是否属于 Gateway 网络/HTTP 故障。
     */
    private boolean isGatewayFailure(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof RestClientException
                    || current instanceof WebClientRequestException
                    || current instanceof WebClientResponseException
                    || current instanceof TransientAiException
                    || current instanceof NonTransientAiException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 从异常链中提取 HTTP 状态码（如有）。
     */
    private Integer extractHttpStatus(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof HttpStatusCodeException httpError) {
                return httpError.getStatusCode().value();
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 提取根因消息摘要，避免异常 message 过长。
     */
    private String summarizeCause(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) + "..." : message;
    }
}
