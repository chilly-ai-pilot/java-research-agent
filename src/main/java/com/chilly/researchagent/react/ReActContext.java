package com.chilly.researchagent.react;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 循环执行过程中的可变上下文。
 */
public class ReActContext {

    private final String userQuestion;
    private final List<ReActStep> steps = new ArrayList<>();
    private int loopStep;

    /**
     * @param userQuestion 用户原始问题，贯穿整个循环
     */
    public ReActContext(String userQuestion) {
        this.userQuestion = userQuestion;
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
