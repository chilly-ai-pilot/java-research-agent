package com.chilly.researchagent.react;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 LLM 原始文本解析为 {@link AgentDecision}，支持 markdown json 代码块包裹。
 */
@Component
public class AgentDecisionParser {

    private static final Pattern MARKDOWN_JSON_BLOCK =
            Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    private static final Pattern LENIENT_FINISH =
            Pattern.compile("\"action\"\\s*:\\s*\"finish\"\\s*,\\s*\"answer\"\\s*:\\s*\"(.*)\"\\s*}\\s*$",
                    Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper JSON 反序列化器（会复制并启用对 LLM 常见非标准 JSON 的容错）
     */
    public AgentDecisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature());
    }

    /**
     * 解析 LLM 输出为结构化决策；失败时抛 {@link DecisionParseException}。
     */
    public AgentDecision parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new DecisionParseException("LLM output is empty", rawText, null);
        }
        String json = extractJson(rawText);
        try {
            return toDecision(readJsonMap(json), rawText);
        } catch (JsonProcessingException first) {
            try {
                return toDecision(readJsonMap(escapeControlCharsInJsonStrings(json)), rawText);
            } catch (JsonProcessingException second) {
                AgentDecision lenient = tryParseLenientFinish(json, rawText);
                if (lenient != null) {
                    return lenient;
                }
                throw new DecisionParseException("Invalid JSON in LLM output", rawText, second);
            }
        }
    }

    private Map<String, Object> readJsonMap(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    /**
     * 从原始文本中提取 JSON 字符串（去掉 markdown 包裹，再按括号匹配取首个 JSON 对象）。
     */
    private String extractJson(String rawText) {
        String trimmed = rawText.trim();
        Matcher matcher = MARKDOWN_JSON_BLOCK.matcher(trimmed);
        if (matcher.find()) {
            return extractFirstJsonObject(matcher.group(1).trim());
        }
        return extractFirstJsonObject(trimmed);
    }

    /**
     * 按括号深度匹配提取第一个完整 JSON 对象，避免 {@code lastIndexOf('}')} 在多对象/嵌套场景截错。
     */
    private String extractFirstJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return text;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }

        int end = text.lastIndexOf('}');
        if (end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * LLM 常在 answer 字符串里直接换行而未写成 {@code \\n}；将字符串内部的控制字符转义后再解析。
     */
    private static String escapeControlCharsInJsonStrings(String json) {
        StringBuilder sanitized = new StringBuilder(json.length() + 32);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    sanitized.append(c);
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    sanitized.append(c);
                    escaped = true;
                } else if (c == '"') {
                    sanitized.append(c);
                    inString = false;
                } else if (c == '\n') {
                    sanitized.append("\\n");
                } else if (c == '\r') {
                    sanitized.append("\\r");
                } else if (c == '\t') {
                    sanitized.append("\\t");
                } else {
                    sanitized.append(c);
                }
                continue;
            }
            sanitized.append(c);
            if (c == '"') {
                inString = true;
            }
        }
        return sanitized.toString();
    }

    /** 标准 JSON 解析仍失败时，尝试从 finish 决策中直接提取 answer 文本。 */
    private AgentDecision tryParseLenientFinish(String json, String rawText) {
        Matcher matcher = LENIENT_FINISH.matcher(json.trim());
        if (!matcher.matches()) {
            return null;
        }
        String answer = unescapeJsonString(matcher.group(1));
        if (answer.isBlank()) {
            throw new DecisionParseException("finish action requires non-blank 'answer'", rawText, null);
        }
        return new AgentDecision(AgentDecision.ACTION_FINISH, null, null, answer);
    }

    private static String unescapeJsonString(String value) {
        return value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
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
                        ? toStringObjectMap(paramsMap, rawText)
                        : Map.of();
                yield new AgentDecision(action, tool, params, null);
            }
            default -> throw new DecisionParseException("Unknown action: " + action, rawText, null);
        };
    }

    /**
     * 将泛型 Map 安全转为 {@code Map<String, Object>}，拒绝非 String 键。
     */
    private Map<String, Object> toStringObjectMap(Map<?, ?> paramsMap, String rawText) {
        if (paramsMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> params = LinkedHashMap.newLinkedHashMap(paramsMap.size());
        for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new DecisionParseException("params keys must be strings", rawText, null);
            }
            params.put(key, entry.getValue());
        }
        return Map.copyOf(params);
    }
}
