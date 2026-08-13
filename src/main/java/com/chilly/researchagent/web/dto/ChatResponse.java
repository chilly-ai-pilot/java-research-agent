package com.chilly.researchagent.web.dto;

import java.util.List;

/**
 * POST /api/agent/chat 非流式响应。
 */
public record ChatResponse(
        String sessionId,
        String answer,
        String terminatedReason,
        List<StepAuditDto> steps) {
}
