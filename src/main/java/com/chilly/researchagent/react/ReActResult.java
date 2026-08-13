package com.chilly.researchagent.react;

import java.util.List;

/**
 * ReAct 循环最终输出，含完整 step 审计链。
 */
public record ReActResult(String finalAnswer, List<ReActStep> steps, TerminatedReason terminatedReason) {
}
