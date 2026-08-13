package com.chilly.researchagent.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /api/agent/chat 请求体。
 *
 * @param message   用户消息（必填）
 * @param sessionId 会话 ID；空则服务端生成 UUID
 * @param stream    是否 SSE 流式响应，默认 false
 */
public record ChatRequest(
        String message,
        String sessionId,
        @JsonProperty(defaultValue = "false") boolean stream) {
}
