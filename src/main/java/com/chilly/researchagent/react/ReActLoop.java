package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import com.chilly.researchagent.memory.ChatMemory;
import com.chilly.researchagent.memory.ChatMemoryProperties;
import com.chilly.researchagent.memory.ChatMessage;
import com.chilly.researchagent.memory.ChatMessageConverter;
import com.chilly.researchagent.memory.SessionChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

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
    private final ChatMemory chatMemory;
    private final ChatMemoryProperties chatMemoryProperties;

    /**
     * @param stepExecutor          单步执行器
     * @param agentProperties       max-steps 与 total-timeout 配置
     * @param traceLogger           结构化追踪日志
     * @param chatMemory            多轮对话记忆
     * @param chatMemoryProperties  滑动窗口大小
     */
    public ReActLoop(
            ReActStepExecutor stepExecutor,
            AgentProperties agentProperties,
            ReActTraceLogger traceLogger,
            ChatMemory chatMemory,
            ChatMemoryProperties chatMemoryProperties) {
        this.stepExecutor = stepExecutor;
        this.agentProperties = agentProperties;
        this.traceLogger = traceLogger;
        this.chatMemory = chatMemory;
        this.chatMemoryProperties = chatMemoryProperties;
    }

    /**
     * 运行 ReAct 循环直到 LLM finish、触达停止条件或出错（使用 default session）。
     */
    public ReActResult run(String userQuestion) {
        return run(SessionChatMemory.DEFAULT_SESSION_ID, userQuestion);
    }

    /**
     * 运行 ReAct 循环；循环开始前加载 session 历史，结束后只写入最终 QA 对。
     */
    public ReActResult run(String sessionId, String userQuestion) {
        return run(sessionId, userQuestion, null);
    }

    /**
     * 运行 ReAct 循环；{@code stepListener} 非空时每添加一步即回调（流式 SSE 用）。
     */
    public ReActResult run(String sessionId, String userQuestion, ReActStepListener stepListener) {
        return run(sessionId, userQuestion, stepListener, null);
    }

    /**
     * 运行 ReAct 循环；{@code tokenListener} 非空时 finish 步通过 LLM 流式推送 answer token。
     */
    public ReActResult run(
            String sessionId,
            String userQuestion,
            ReActStepListener stepListener,
            ReActTokenListener tokenListener) {
        String normalizedSessionId = SessionChatMemory.normalizeSessionId(sessionId);
        List<Message> history = ChatMessageConverter.toSpringAiMessages(
                chatMemory.getRecent(normalizedSessionId, chatMemoryProperties.maxMessages()));
        ReActContext context = new ReActContext(userQuestion, history, normalizedSessionId, tokenListener);
        ReActResult result = runLoop(context, stepListener);
        persistConversation(normalizedSessionId, userQuestion, result.finalAnswer());
        return result;
    }

    private ReActResult runLoop(ReActContext context) {
        return runLoop(context, null);
    }

    private ReActResult runLoop(ReActContext context, ReActStepListener stepListener) {
        long startedAt = System.currentTimeMillis();
        traceLogger.logRunStart(context.userQuestion());

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
                    ReActStep finishStep = new ReActStep(
                            context.nextStepIndex(),
                            AgentDecision.ACTION_FINISH,
                            null,
                            null,
                            finished.answer(),
                            Instant.now());
                    context.addStep(finishStep);
                    notifyStep(stepListener, finishStep);
                    ReActResult reactResult = new ReActResult(finished.answer(), context.steps(), TerminatedReason.LLM_FINISH);
                    traceLogger.logRunEnd(reactResult, System.currentTimeMillis() - startedAt);
                    return reactResult;
                }

                StepResult.Continue continued = (StepResult.Continue) result;
                ReActStep toolStep = new ReActStep(
                        context.nextStepIndex(),
                        continued.decision().action(),
                        continued.decision().tool(),
                        continued.decision().params(),
                        continued.observation(),
                        Instant.now());
                context.addStep(toolStep);
                notifyStep(stepListener, toolStep);
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

    private static void notifyStep(ReActStepListener stepListener, ReActStep step) {
        if (stepListener != null) {
            stepListener.onStep(step);
        }
    }

    private void persistConversation(String sessionId, String userQuestion, String finalAnswer) {
        chatMemory.add(sessionId, new ChatMessage(ChatMessage.Role.USER, userQuestion, Instant.now()));
        chatMemory.add(sessionId, new ChatMessage(ChatMessage.Role.ASSISTANT, finalAnswer, Instant.now()));
    }
}
