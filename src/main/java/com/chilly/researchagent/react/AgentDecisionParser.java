package com.chilly.researchagent.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 LLM 原始文本解析为 {@link AgentDecision}，支持 markdown json 代码块包裹。
 */
@Component
public class AgentDecisionParser {

    private static final Pattern MARKDOWN_JSON_BLOCK =
            Pattern.compile("```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public AgentDecisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 LLM 输出为结构化决策；失败时抛 {@link DecisionParseException}。
     */
    public AgentDecision parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new DecisionParseException("LLM output is empty", rawText, null);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(extractJson(rawText), new TypeReference<>() {});
            return toDecision(map, rawText);
        } catch (JsonProcessingException e) {
            throw new DecisionParseException("Invalid JSON in LLM output", rawText, e);
        }
    }

    /**
     * 从原始文本中提取 JSON 字符串（去掉 markdown 包裹或首尾空白）。
     */
    private String extractJson(String rawText) {
        String trimmed = rawText.trim();
        Matcher matcher = MARKDOWN_JSON_BLOCK.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 校验字段并构造 {@link AgentDecision}。
     */
    private AgentDecision toDecision(Map<String, Object> map, String rawText) {
        Object actionObj = map.get("action");
        if (!(actionObj instanceof String action) || action.isBlank()) {
            throw new DecisionParseException("Missing or invalid 'action' field", rawText, null);
        }

        return switch (action) {
            case AgentDecision.ACTION_FINISH -> {
                Object answerObj = map.get("answer");
                if (!(answerObj instanceof String answer) || answer.isBlank()) {
                    throw new DecisionParseException("finish action requires non-blank 'answer'", rawText, null);
                }
                yield new AgentDecision(action, null, null, answer);
            }
            case AgentDecision.ACTION_CALL_TOOL -> {
                Object toolObj = map.get("tool");
                if (!(toolObj instanceof String tool) || tool.isBlank()) {
                    throw new DecisionParseException("call_tool action requires non-blank 'tool'", rawText, null);
                }
                Object paramsObj = map.get("params");
                Map<String, Object> params = paramsObj instanceof Map<?, ?> paramsMap
                        ? castParams(paramsMap)
                        : Map.of();
                yield new AgentDecision(action, tool, params, null);
            }
            default -> throw new DecisionParseException("Unknown action: " + action, rawText, null);
        };
    }

    /**
     * 将泛型 Map 转为 {@code Map<String, Object>}。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castParams(Map<?, ?> paramsMap) {
        return (Map<String, Object>) paramsMap;
    }
}
