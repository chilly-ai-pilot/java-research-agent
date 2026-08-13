package com.chilly.researchagent.react;

import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 循环执行过程中的可变上下文。
 */
public class ReActContext {

    private final String userQuestion;
    private final List<Message> conversationHistory;
    private final String sessionId;
    private final ReActTokenListener tokenListener;
    private final List<ReActStep> steps = new ArrayList<>();
    private int loopStep;

    /**
     * @param userQuestion 用户原始问题，贯穿整个循环
     */
    public ReActContext(String userQuestion) {
        this(userQuestion, List.of(), null, null);
    }

    /**
     * @param userQuestion         用户原始问题，贯穿整个循环
     * @param conversationHistory  本轮之前的多轮对话（不含当前 userQuestion）
     */
    public ReActContext(String userQuestion, List<Message> conversationHistory) {
        this(userQuestion, conversationHistory, null, null);
    }

    /**
     * @param userQuestion         用户原始问题，贯穿整个循环
     * @param conversationHistory  本轮之前的多轮对话（不含当前 userQuestion）
     * @param sessionId            会话 ID，供长期记忆 recall 使用
     */
    public ReActContext(String userQuestion, List<Message> conversationHistory, String sessionId) {
        this(userQuestion, conversationHistory, sessionId, null);
    }

    /**
     * @param tokenListener 非空时在 finish 步流式推送 LLM answer token（SSE 用）
     */
    public ReActContext(
            String userQuestion,
            List<Message> conversationHistory,
            String sessionId,
            ReActTokenListener tokenListener) {
        this.userQuestion = userQuestion;
        this.conversationHistory = conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
        this.sessionId = sessionId;
        this.tokenListener = tokenListener;
    }

    /** 返回 finish 步 LLM token 回调（可能为 null）。 */
    public ReActTokenListener tokenListener() {
        return tokenListener;
    }

    /** 返回会话 ID（可能为 null，例如单元测试未设置时）。 */
    public String sessionId() {
        return sessionId;
    }

    /** 返回本轮之前的多轮对话历史。 */
    public List<Message> conversationHistory() {
        return conversationHistory;
    }

    /** 设置当前循环步序号（由 {@link ReActLoop} 在每步执行前设置）。 */
    public void setLoopStep(int loopStep) {
        this.loopStep = loopStep;
    }

    /** 返回当前循环步序号（从 1 开始）。 */
    public int loopStep() {
        return loopStep;
    }

    /** 返回用户原始问题。 */
    public String userQuestion() {
        return userQuestion;
    }

    /** 返回已执行步骤的不可变快照。 */
    public List<ReActStep> steps() {
        return List.copyOf(steps);
    }

    /** 追加一步执行记录。 */
    public void addStep(ReActStep step) {
        steps.add(step);
    }

    /** 返回下一步的序号（从 1 开始）。 */
    public int nextStepIndex() {
        return steps.size() + 1;
    }
}
