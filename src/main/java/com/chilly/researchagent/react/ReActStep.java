package com.chilly.researchagent.react;

import java.time.Instant;
import java.util.Map;

/**
 * ReAct 循环中单步的执行记录，供 Prompt 组装与 Iteration 5 审计复用。
 */
public record ReActStep(
        /** 步骤序号，从 1 开始递增。 */
        int stepIndex,
        /** LLM 决策的 action，如 {@code call_tool} 或 {@code finish}。 */
        String action,
        /** 调用的 MCP Tool 名称；finish 步骤为 null。 */
        String tool,
        /** Tool 调用参数；finish 步骤为 null 或空 Map。 */
        Map<String, Object> params,
        /** Tool 执行结果或中间观察，供下一步 Prompt 引用。 */
        String observation,
        /** 该步骤完成的时间戳。 */
        Instant timestamp) {
}
