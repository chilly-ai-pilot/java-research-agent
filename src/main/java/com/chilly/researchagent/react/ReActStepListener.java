package com.chilly.researchagent.react;

/**
 * ReAct 每完成一步时的回调（用于 SSE 流式推送 step 事件）。
 */
@FunctionalInterface
public interface ReActStepListener {

    void onStep(ReActStep step);
}
