package com.chilly.researchagent.react;

/**
 * ReAct 在 LLM 流式生成 finish 答案时的 token 回调（用于 SSE {@code event:token}）。
 */
@FunctionalInterface
public interface ReActTokenListener {

    void onToken(String token);
}
