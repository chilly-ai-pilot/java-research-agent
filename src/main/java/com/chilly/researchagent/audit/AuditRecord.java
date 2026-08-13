package com.chilly.researchagent.audit;

import com.chilly.researchagent.web.dto.StepAuditDto;

import java.time.Instant;
import java.util.List;

/**
 * 单次 Agent 请求的审计记录（写入 SLF4J 结构化 JSON）。
 */
public record AuditRecord(
        String requestId,
        String sessionId,
        String userMessage,
        List<AuditStepEntry> steps,
        String finalAnswer,
        String terminatedReason,
        Instant startedAt,
        long durationMs) {

    /** 审计日志中的单步摘要，observation 为截断预览。 */
    public record AuditStepEntry(
            int stepIndex,
            String action,
            String tool,
            Object params,
            String observationPreview) {
    }

    /** 从 REST 用 StepAuditDto 构造审计步（无 observation）。 */
    public static AuditStepEntry fromStepAudit(StepAuditDto step, String observationPreview) {
        return new AuditStepEntry(
                step.stepIndex(),
                step.action(),
                step.tool(),
                step.params(),
                observationPreview);
    }
}
