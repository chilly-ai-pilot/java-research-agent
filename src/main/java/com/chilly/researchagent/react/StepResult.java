package com.chilly.researchagent.react;

/**
 * 单步 ReAct 执行结果：结束循环或携带 observation 继续。
 */
public sealed interface StepResult {

    /** LLM 决定结束循环并给出最终答案。 */
    record Finished(String answer) implements StepResult {
    }

    /** LLM 决定调用工具，携带 Tool 返回的 observation 供下一步引用。 */
    record Continue(String observation, AgentDecision decision) implements StepResult {
    }

    /** 构造 finish 结果。 */
    static StepResult finished(String answer) {
        return new Finished(answer);
    }

    /** 构造 continue 结果。 */
    static StepResult continueWith(String observation, AgentDecision decision) {
        return new Continue(observation, decision);
    }
}
