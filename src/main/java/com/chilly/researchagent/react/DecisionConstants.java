package com.chilly.researchagent.react;

/**
 * ReAct 决策解析相关的共享常量。
 */
public final class DecisionConstants {

    /** LLM 原始输出在异常/日志中允许预览的最大字符数。 */
    public static final int RAW_TEXT_PREVIEW_LIMIT = 500;

    /** 异常 message 中附带的短预览字符数。 */
    public static final int MESSAGE_PREVIEW_LIMIT = 50;

    /** 禁止实例化。 */
    private DecisionConstants() {
    }
}
