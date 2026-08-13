package com.chilly.researchagent.react;

/**
 * ReAct 循环终止原因。
 */
public enum TerminatedReason {
    /** LLM 返回 finish 决策，正常结束。 */
    LLM_FINISH,
    /** 达到 agent.max-steps 上限。 */
    MAX_STEPS,
    /** 超过 agent.total-timeout-ms。 */
    TOTAL_TIMEOUT,
    /** 解析失败、重复调用、单步超时等错误。 */
    ERROR
}
