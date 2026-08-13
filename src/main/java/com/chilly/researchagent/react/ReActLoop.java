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
    private final ReActTraceLogger traceLogger;

    /**
     * @param stepExecutor      单步执行器
     * @param agentProperties   max-steps 与 total-timeout 配置
     * @param traceLogger       结构化追踪日志
     */
    public ReActLoop(
            ReActStepExecutor stepExecutor,
            AgentProperties agentProperties,
            ReActTraceLogger traceLogger) {
        this.stepExecutor = stepExecutor;
        this.agentProperties = agentProperties;
        this.traceLogger = traceLogger;
    }

    /**
     * 运行 ReAct 循环直到 LLM finish、触达停止条件或出错。
     */
    public ReActResult run(String userQuestion) {
        ReActContext context = new ReActContext(userQuestion);
        long startedAt = System.currentTimeMillis();
        traceLogger.logRunStart(userQuestion);

        for (int step = 1; step <= agentProperties.maxSteps(); step++) {
            if (System.currentTimeMillis() - startedAt > agentProperties.totalTimeoutMs()) {
                ReActResult result = new ReActResult(TOTAL_TIMEOUT_MESSAGE, context.steps(), TerminatedReason.TOTAL_TIMEOUT);
                traceLogger.logRunEnd(result, System.currentTimeMillis() - startedAt);
                return result;
            }

            context.setLoopStep(step);
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
                    ReActResult reactResult = new ReActResult(finished.answer(), context.steps(), TerminatedReason.LLM_FINISH);
                    traceLogger.logRunEnd(reactResult, System.currentTimeMillis() - startedAt);
                    return reactResult;
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
                traceLogger.logRunError(step, e.getMessage());
                ReActResult result = new ReActResult(e.getMessage(), context.steps(), TerminatedReason.ERROR);
                traceLogger.logRunEnd(result, System.currentTimeMillis() - startedAt);
                return result;
            }
        }

        ReActResult result = new ReActResult(MAX_STEPS_MESSAGE, context.steps(), TerminatedReason.MAX_STEPS);
        traceLogger.logRunEnd(result, System.currentTimeMillis() - startedAt);
        return result;
    }
}
