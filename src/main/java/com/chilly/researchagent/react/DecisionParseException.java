package com.chilly.researchagent.react;

/**
 * LLM 输出无法解析为 {@link AgentDecision} 时抛出。
 */
public class DecisionParseException extends RuntimeException {

    private static final int RAW_TEXT_PREVIEW_LIMIT = 500;

    /**
     * @param message  错误说明
     * @param rawText  LLM 原始输出（message 中会附带前 500 字符预览）
     * @param cause    底层解析异常（可为 null）
     */
    public DecisionParseException(String message, String rawText, Throwable cause) {
        super(formatMessage(message, rawText), cause);
    }

    /**
     * 组装含原始文本预览的错误消息。
     */
    private static String formatMessage(String message, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return message;
        }
        String preview = rawText.length() > RAW_TEXT_PREVIEW_LIMIT
                ? rawText.substring(0, RAW_TEXT_PREVIEW_LIMIT) + "..."
                : rawText;
        return message + " | raw: " + preview;
    }
}
