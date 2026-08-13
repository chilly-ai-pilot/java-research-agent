package com.chilly.researchagent.react;

/**
 * ReAct 循环终止原因。
 */
public enum TerminatedReason {
    LLM_FINISH,
    MAX_STEPS,
    TOTAL_TIMEOUT,
    ERROR
}
