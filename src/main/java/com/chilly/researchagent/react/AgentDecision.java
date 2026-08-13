package com.chilly.researchagent.react;

import java.util.Map;

/**
 * LLM 单步 ReAct 决策：调用工具或结束并给出答案。
 */
public record AgentDecision(String action, String tool, Map<String, Object> params, String answer) {

    /** 调用 MCP Tool。 */
    public static final String ACTION_CALL_TOOL = "call_tool";

    /** 结束循环并返回最终答案。 */
    public static final String ACTION_FINISH = "finish";

    /**
     * 是否为结束决策。
     */
    public boolean isFinish() {
        return ACTION_FINISH.equals(action);
    }

    /**
     * 是否为工具调用决策。
     */
    public boolean isCallTool() {
        return ACTION_CALL_TOOL.equals(action);
    }
}
