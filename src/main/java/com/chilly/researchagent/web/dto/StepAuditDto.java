package com.chilly.researchagent.web.dto;

import java.util.Map;

/**
 * ReAct 单步审计摘要（REST 响应不含 observation 全文）。
 */
public record StepAuditDto(
        int stepIndex,
        String action,
        String tool,
        Map<String, Object> params) {
}
