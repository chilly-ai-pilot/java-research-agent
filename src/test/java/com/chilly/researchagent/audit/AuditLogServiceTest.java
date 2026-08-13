package com.chilly.researchagent.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chilly.researchagent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {

    private static final int MAX_OBS_CHARS = 100;

    private ListAppender<ILoggingEvent> appender;
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLogService.class);
        logger.addAppender(appender);

        AgentProperties properties = new AgentProperties(10, 30_000L, 90_000L, MAX_OBS_CHARS, "prompts/system-react.txt");
        auditLogService = new AuditLogService(new ObjectMapper().registerModule(new JavaTimeModule()), properties);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLogService.class);
        logger.detachAppender(appender);
    }

    @Test
    void logsStructuredJsonWithToolNameAndTruncatedObservation() {
        String longObservation = "x".repeat(MAX_OBS_CHARS + 500);
        AuditRecord record = new AuditRecord(
                "req-1",
                "session-1",
                "帮我查 Transformer",
                List.of(new AuditRecord.AuditStepEntry(
                        1,
                        "call_tool",
                        "search_knowledge",
                        Map.of("query", "Transformer"),
                        longObservation)),
                "final answer",
                "LLM_FINISH",
                Instant.parse("2026-08-14T00:00:00Z"),
                42L);

        auditLogService.log(record);

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).startsWith(AuditLogService.AUDIT_MARKER);
        assertThat(message).contains("search_knowledge");
        assertThat(message).contains("\"query\":\"Transformer\"");
        assertThat(message).contains("...");
        assertThat(message).doesNotContain(longObservation);
    }

    @Test
    void preservesParamsWhileTruncatingObservation() {
        AuditRecord record = new AuditRecord(
                "req-2",
                "session-2",
                "question",
                List.of(new AuditRecord.AuditStepEntry(
                        1,
                        "call_tool",
                        "web_search",
                        Map.of("query", "attention mechanism"),
                        "short observation")),
                "answer",
                "LLM_FINISH",
                Instant.now(),
                10L);

        auditLogService.log(record);

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("attention mechanism");
        assertThat(message).contains("short observation");
    }
}
