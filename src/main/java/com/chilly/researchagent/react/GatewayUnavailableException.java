package com.chilly.researchagent.react;

/**
 * Go LLM Gateway 不可达或返回错误时的明确异常，与 LLM 决策/JSON 解析错误区分。
 */
public class GatewayUnavailableException extends RuntimeException {

    private final String gatewayUrl;
    private final Integer httpStatus;

    public GatewayUnavailableException(
            String gatewayUrl, Integer httpStatus, String causeSummary, Throwable cause) {
        super(formatMessage(gatewayUrl, httpStatus, causeSummary), cause);
        this.gatewayUrl = gatewayUrl;
        this.httpStatus = httpStatus;
    }

    public String gatewayUrl() {
        return gatewayUrl;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    private static String formatMessage(String gatewayUrl, Integer httpStatus, String causeSummary) {
        StringBuilder message = new StringBuilder("Gateway unavailable at ").append(gatewayUrl);
        if (httpStatus != null) {
            message.append(" (HTTP ").append(httpStatus).append(')');
        }
        if (causeSummary != null && !causeSummary.isBlank()) {
            message.append(": ").append(causeSummary);
        }
        return message.toString();
    }
}
