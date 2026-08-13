package com.chilly.researchagent.react;

/**
 * LLM 连续返回相同的 tool + params 决策时抛出。
 */
public class DuplicateToolCallException extends RuntimeException {

    /**
     * @param message 错误说明（应含「重复调用」便于测试与日志检索）
     */
    public DuplicateToolCallException(String message) {
        super(message);
    }
}
