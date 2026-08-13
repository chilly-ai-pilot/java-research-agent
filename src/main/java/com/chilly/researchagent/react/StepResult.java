package com.chilly.researchagent.react;

/**
 * 单步 ReAct 执行结果：结束循环或携带 observation 继续。
 */
public sealed interface StepResult {

    record Finished(String answer) implements StepResult {
    }

    record Continue(String observation, AgentDecision decision) implements StepResult {
    }

    static StepResult finished(String answer) {
        return new Finished(answer);
    }

    static StepResult continueWith(String observation, AgentDecision decision) {
        return new Continue(observation, decision);
    }
}
