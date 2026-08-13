package com.chilly.researchagent.react;

/**
 * 从 LLM 流式 JSON 输出中提取 {@code "answer":"..."} 字段内容，供 SSE 实时推送。
 */
final class FinishAnswerStreamExtractor {

    private enum Phase {
        SCAN,
        AFTER_ANSWER_LITERAL,
        AFTER_COLON,
        IN_ANSWER,
        DONE
    }

    private Phase phase = Phase.SCAN;
    private int answerMatchIndex;
    private boolean escape;

    /** 消费一段 LLM 流式输出，返回本次新解析出的 answer 文本（可能为空）。 */
    String consume(String chunk) {
        if (chunk == null || chunk.isEmpty() || phase == Phase.DONE) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < chunk.length(); i++) {
            appendFromChar(chunk.charAt(i), out);
            if (phase == Phase.DONE) {
                break;
            }
        }
        return out.toString();
    }

    private void appendFromChar(char c, StringBuilder out) {
        switch (phase) {
            case SCAN -> scan(c);
            case AFTER_ANSWER_LITERAL -> afterAnswerLiteral(c);
            case AFTER_COLON -> afterColon(c);
            case IN_ANSWER -> readAnswerChar(c, out);
            case DONE -> { /* no-op */ }
        }
    }

    private void scan(char c) {
        if (c == "\"answer\"".charAt(answerMatchIndex)) {
            answerMatchIndex++;
            if (answerMatchIndex == "\"answer\"".length()) {
                phase = Phase.AFTER_ANSWER_LITERAL;
            }
            return;
        }
        answerMatchIndex = (c == '"') ? 1 : 0;
    }

    private void afterAnswerLiteral(char c) {
        if (Character.isWhitespace(c)) {
            return;
        }
        if (c == ':') {
            phase = Phase.AFTER_COLON;
            return;
        }
        resetScan(c);
    }

    private void afterColon(char c) {
        if (Character.isWhitespace(c)) {
            return;
        }
        if (c == '"') {
            phase = Phase.IN_ANSWER;
            return;
        }
        resetScan(c);
    }

    private void readAnswerChar(char c, StringBuilder out) {
        if (escape) {
            out.append(unescape(c));
            escape = false;
            return;
        }
        if (c == '\\') {
            escape = true;
            return;
        }
        if (c == '"') {
            phase = Phase.DONE;
            return;
        }
        out.append(c);
    }

    private static char unescape(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            default -> c;
        };
    }

    private void resetScan(char c) {
        phase = Phase.SCAN;
        answerMatchIndex = (c == '"') ? 1 : 0;
    }
}
