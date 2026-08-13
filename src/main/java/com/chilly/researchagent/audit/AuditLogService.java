package com.chilly.researchagent.audit;

import com.chilly.researchagent.config.AgentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将 {@link AuditRecord} 以单行 JSON 写入 INFO 日志，便于 grep / 接入 ELK。
 */
@Service
public class AuditLogService {

    static final String AUDIT_MARKER = "[Audit]";

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final ObjectMapper objectMapper;
    private final int maxObservationChars;

    public AuditLogService(ObjectMapper objectMapper, AgentProperties agentProperties) {
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.maxObservationChars = agentProperties.maxObservationChars();
    }

    /**
     * 记录一次 Agent 请求审计；observation 在写入前按配置截断。
     */
    public void log(AuditRecord record) {
        AuditRecord sanitized = sanitize(record);
        try {
            log.info("{} {}", AUDIT_MARKER, objectMapper.writeValueAsString(sanitized));
        } catch (JsonProcessingException e) {
            log.warn("{} Failed to serialize audit record requestId={}", AUDIT_MARKER, record.requestId(), e);
        }
    }

    /** 供测试读取截断上限。 */
    int maxObservationChars() {
        return maxObservationChars;
    }

    private AuditRecord sanitize(AuditRecord record) {
        if (record.steps() == null || record.steps().isEmpty()) {
            return record;
        }
        var steps = record.steps().stream()
                .map(step -> new AuditRecord.AuditStepEntry(
                        step.stepIndex(),
                        step.action(),
                        step.tool(),
                        step.params(),
                        truncate(step.observationPreview())))
                .toList();
        return new AuditRecord(
                record.requestId(),
                record.sessionId(),
                record.userMessage(),
                steps,
                record.finalAnswer(),
                record.terminatedReason(),
                record.startedAt(),
                record.durationMs());
    }

    private String truncate(String observation) {
        if (observation == null || observation.isBlank()) {
            return observation;
        }
        if (observation.length() <= maxObservationChars) {
            return observation;
        }
        return observation.substring(0, maxObservationChars) + "...";
    }
}
