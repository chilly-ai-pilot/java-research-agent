package com.chilly.researchagent.react;

/**
 * Go LLM Gateway 不可达或返回错误时的明确异常，与 LLM 决策/JSON 解析错误区分。
 */
public class GatewayUnavailableException extends RuntimeException {

    private final String gatewayUrl;
    private final Integer httpStatus;

    /**
     * @param message    已格式化的错误消息
     * @param gatewayUrl 请求发送到的 Gateway 地址
     * @param httpStatus HTTP 状态码，连接失败时为 null
     * @param cause      原始异常
     */
    public GatewayUnavailableException(
            String message, String gatewayUrl, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.gatewayUrl = gatewayUrl;
        this.httpStatus = httpStatus;
    }

    /** 返回 Gateway 地址。 */
    public String gatewayUrl() {
        return gatewayUrl;
    }

    /** 返回 HTTP 状态码，连接层失败时为 null。 */
    public Integer httpStatus() {
        return httpStatus;
    }
}
