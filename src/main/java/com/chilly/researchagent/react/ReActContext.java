package com.chilly.researchagent.react;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 循环执行过程中的可变上下文。
 */
public class ReActContext {

    private final String userQuestion;
    private final List<ReActStep> steps = new ArrayList<>();

    public ReActContext(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public String userQuestion() {
        return userQuestion;
    }

    public List<ReActStep> steps() {
        return List.copyOf(steps);
    }

    public void addStep(ReActStep step) {
        steps.add(step);
    }

    public int nextStepIndex() {
        return steps.size() + 1;
    }
}
