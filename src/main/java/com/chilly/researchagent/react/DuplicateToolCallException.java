package com.chilly.researchagent.react;

/**
 * LLM 连续返回相同的 tool + params 决策时抛出。
 */
public class DuplicateToolCallException extends RuntimeException {

    public DuplicateToolCallException(String message) {
        super(message);
    }
}
