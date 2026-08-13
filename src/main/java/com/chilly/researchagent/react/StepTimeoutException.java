package com.chilly.researchagent.react;

/**
 * 单步 ReAct 执行超过 {@code agent.step-timeout-ms} 时抛出。
 */
public class StepTimeoutException extends RuntimeException {

    /**
     * @param message 超时说明（含 step-timeout-ms）
     * @param cause   底层 {@link java.util.concurrent.TimeoutException}
     */
    public StepTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
