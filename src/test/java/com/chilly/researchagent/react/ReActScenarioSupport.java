package com.chilly.researchagent.react;

import java.util.List;
import java.util.Map;

/**
 * ReAct 场景测试的共享断言工具。
 */
final class ReActScenarioSupport {

    private ReActScenarioSupport() {
    }

    /** 返回所有 call_tool 步骤。 */
    static List<ReActStep> toolCallSteps(ReActResult result) {
        return result.steps().stream()
                .filter(step -> AgentDecision.ACTION_CALL_TOOL.equals(step.action()))
                .toList();
    }

    /** 统计 call_tool 步骤数量。 */
    static long countToolCalls(ReActResult result) {
        return toolCallSteps(result).size();
    }

    /** 是否调用过指定 Tool。 */
    static boolean usedTool(ReActResult result, String toolName) {
        return toolCallSteps(result).stream().anyMatch(step -> toolName.equals(step.tool()));
    }

    /** 是否调用过 search_knowledge 或 generate_answer。 */
    static boolean usedKnowledgeTool(ReActResult result) {
        return usedTool(result, "search_knowledge") || usedTool(result, "generate_answer");
    }

    /** params 的字符串形式是否包含期望片段（忽略大小写）。 */
    static boolean paramsContain(ReActStep step, String expected) {
        Map<String, Object> params = step.params();
        if (params == null || params.isEmpty()) {
            return false;
        }
        return params.toString().toLowerCase().contains(expected.toLowerCase());
    }
}
