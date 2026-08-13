package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * ReAct 主循环：逐步调用 {@link ReActStepExecutor}，强制执行 max-steps / total-timeout 停止条件。
 */
@Component
public class ReActLoop {

    private static final String MAX_STEPS_MESSAGE = "任务未完成：已达到最大步数限制。";
    private static final String TOTAL_TIMEOUT_MESSAGE = "任务未完成：总耗时超过限制。";

    private final ReActStepExecutor stepExecutor;
    private final AgentProperties agentProperties;

    public ReActLoop(ReActStepExecutor stepExecutor, AgentProperties agentProperties) {
        this.stepExecutor = stepExecutor;
        this.agentProperties = agentProperties;
    }

    /**
     * 运行 ReAct 循环直到 LLM finish、触达停止条件或出错。
     */
    public ReActResult run(String userQuestion) {
        ReActContext context = new ReActContext(userQuestion);
        long startedAt = System.currentTimeMillis();

        for (int step = 1; step <= agentProperties.maxSteps(); step++) {
            if (System.currentTimeMillis() - startedAt > agentProperties.totalTimeoutMs()) {
                return new ReActResult(TOTAL_TIMEOUT_MESSAGE, context.steps(), TerminatedReason.TOTAL_TIMEOUT);
            }

            try {
                StepResult result = stepExecutor.executeOneStep(context);
                if (result instanceof StepResult.Finished finished) {
                    context.addStep(new ReActStep(
                            context.nextStepIndex(),
                            AgentDecision.ACTION_FINISH,
                            null,
                            null,
                            finished.answer(),
                            Instant.now()));
                    return new ReActResult(finished.answer(), context.steps(), TerminatedReason.LLM_FINISH);
                }

                StepResult.Continue continued = (StepResult.Continue) result;
                context.addStep(new ReActStep(
                        context.nextStepIndex(),
                        continued.decision().action(),
                        continued.decision().tool(),
                        continued.decision().params(),
                        continued.observation(),
                        Instant.now()));
            } catch (StepTimeoutException | DecisionParseException | DuplicateToolCallException e) {
                return new ReActResult(e.getMessage(), context.steps(), TerminatedReason.ERROR);
            }
        }

        return new ReActResult(MAX_STEPS_MESSAGE, context.steps(), TerminatedReason.MAX_STEPS);
    }
}
