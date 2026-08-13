package com.chilly.researchagent.react;

import java.time.Instant;
import java.util.Map;

/**
 * ReAct 循环中单步的执行记录，供 Prompt 组装与 Iteration 5 审计复用。
 */
public record ReActStep(
        int stepIndex,
        String action,
        String tool,
        Map<String, Object> params,
        String observation,
        Instant timestamp) {
}
