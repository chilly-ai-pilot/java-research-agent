package com.chilly.researchagent.react;

/**
 * LLM 输出无法解析为 {@link AgentDecision} 时抛出。
 */
public class DecisionParseException extends RuntimeException {

    private final String rawTextPreview;

    /**
     * @param message  错误说明
     * @param rawText  LLM 原始输出（message 含短预览；完整预览见 {@link #getRawTextPreview()}）
     * @param cause    底层解析异常（可为 null）
     */
    public DecisionParseException(String message, String rawText, Throwable cause) {
        super(formatMessage(message, rawText), cause);
        this.rawTextPreview = preview(rawText);
    }

    /**
     * 原始 LLM 输出的截断预览，供调试使用。
     */
    public String getRawTextPreview() {
        return rawTextPreview;
    }

    /** 组装含长度与短预览的错误消息，避免完整原文进入日志。 */
    private static String formatMessage(String message, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return message;
        }
        String shortPreview = truncate(rawText, DecisionConstants.MESSAGE_PREVIEW_LIMIT);
        return message + " (raw length=" + rawText.length() + ", preview=" + shortPreview + ")";
    }

    /** 生成供 {@link #getRawTextPreview()} 返回的较长预览。 */
    private static String preview(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        return truncate(rawText, DecisionConstants.RAW_TEXT_PREVIEW_LIMIT);
    }

    /** 截断文本并在超出 limit 时追加省略号。 */
    private static String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "..." : text;
    }
}
