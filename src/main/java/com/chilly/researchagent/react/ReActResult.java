package com.chilly.researchagent.react;

import java.util.List;

/**
 * ReAct 循环最终输出，含完整 step 审计链。
 *
 * @param finalAnswer       返回给用户的最终答案或停止说明
 * @param steps             完整执行步骤（含 finish 步骤）
 * @param terminatedReason  循环终止原因
 */
public record ReActResult(String finalAnswer, List<ReActStep> steps, TerminatedReason terminatedReason) {
}
