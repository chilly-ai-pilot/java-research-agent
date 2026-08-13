package com.chilly.researchagent.react;

/**
 * 单步 ReAct 执行超过 {@code agent.step-timeout-ms} 时抛出。
 */
public class StepTimeoutException extends RuntimeException {

    public StepTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
