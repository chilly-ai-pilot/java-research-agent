package com.chilly.researchagent.react;

import java.util.Map;
import java.util.Optional;

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

    /**
     * 类型安全地读取 string 类型的 params 字段。
     */
    public Optional<String> paramAsString(String key) {
        if (params == null) {
            return Optional.empty();
        }
        Object value = params.get(key);
        return value instanceof String stringValue ? Optional.of(stringValue) : Optional.empty();
    }

    /**
     * 类型安全地读取整数类型的 params 字段。
     */
    public Optional<Integer> paramAsInt(String key) {
        if (params == null) {
            return Optional.empty();
        }
        Object value = params.get(key);
        return value instanceof Number number ? Optional.of(number.intValue()) : Optional.empty();
    }
}
