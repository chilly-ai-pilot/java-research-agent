package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import com.chilly.researchagent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 执行 ReAct 单步：LLM 决策 → 解析 → finish 或 call_tool。
 */
@Component
public class ReActStepExecutor {

    private final GatewayChatService gatewayChatService;
    private final ToolRegistry toolRegistry;
    private final AgentDecisionParser decisionParser;
    private final ReActPromptBuilder promptBuilder;
    private final AgentProperties agentProperties;
    private final ReActTraceLogger traceLogger;

    /**
     * @param gatewayChatService  LLM 调用入口
     * @param toolRegistry        MCP Tool 调用入口
     * @param decisionParser      LLM 输出 JSON 解析器
     * @param promptBuilder       Prompt 组装器
     * @param agentProperties     step 超时与 observation 截断配置
     * @param traceLogger         结构化追踪日志
     */
    public ReActStepExecutor(
            GatewayChatService gatewayChatService,
            ToolRegistry toolRegistry,
            AgentDecisionParser decisionParser,
            ReActPromptBuilder promptBuilder,
            AgentProperties agentProperties,
            ReActTraceLogger traceLogger) {
        this.gatewayChatService = gatewayChatService;
        this.toolRegistry = toolRegistry;
        this.decisionParser = decisionParser;
        this.promptBuilder = promptBuilder;
        this.agentProperties = agentProperties;
        this.traceLogger = traceLogger;
    }

    /**
     * 执行一步 ReAct；整步耗时超过 {@code agent.step-timeout-ms} 则抛 {@link StepTimeoutException}。
     */
    public StepResult executeOneStep(ReActContext context) {
        try {
            return CompletableFuture.supplyAsync(() -> doExecuteOneStep(context))
                    .get(agentProperties.stepTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new StepTimeoutException(
                    "Step exceeded " + agentProperties.stepTimeoutMs() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Step execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Step execution failed", cause);
        }
    }

    /** 执行单步核心逻辑：调 LLM、解析决策、必要时调 Tool。 */
    private StepResult doExecuteOneStep(ReActContext context) {
        String stepPrompt = promptBuilder.buildStepPrompt(context.steps(), context.userQuestion());
        traceLogger.logStepStart(context.loopStep(), stepPrompt);

        String systemPrompt = promptBuilder.buildSystemPrompt(context.sessionId(), context.userQuestion());
        String rawDecision = gatewayChatService.chat(systemPrompt, context.conversationHistory(), stepPrompt);
        AgentDecision decision = decisionParser.parse(rawDecision);
        traceLogger.logLlmDecision(context.loopStep(), rawDecision, decision);

        if (decision.isFinish()) {
            return StepResult.finished(decision.answer());
        }

        if (isDuplicateOfLastStep(context, decision)) {
            throw new DuplicateToolCallException("检测到重复调用同一工具，任务已终止。");
        }

        String observation = toolRegistry.callTool(decision.tool(), decision.params());
        String truncated = truncateObservation(observation);
        traceLogger.logToolObservation(context.loopStep(), decision.tool(), truncated);
        return StepResult.continueWith(truncated, decision);
    }

    /** 检测 LLM 是否连续返回与上一步相同的 tool + params。 */
    private boolean isDuplicateOfLastStep(ReActContext context, AgentDecision decision) {
        if (context.steps().isEmpty()) {
            return false;
        }
        ReActStep lastStep = context.steps().get(context.steps().size() - 1);
        return decision.tool().equals(lastStep.tool())
                && decision.params().equals(lastStep.params());
    }

    /** 将 Tool observation 截断到 {@code agent.max-observation-chars}。 */
    private String truncateObservation(String observation) {
        int maxChars = agentProperties.maxObservationChars();
        if (observation == null || observation.length() <= maxChars) {
            return observation;
        }
        return observation.substring(0, maxChars);
    }
}
