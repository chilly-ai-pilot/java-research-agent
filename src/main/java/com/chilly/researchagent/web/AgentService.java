package com.chilly.researchagent.web;

import com.chilly.researchagent.audit.AuditLogService;
import com.chilly.researchagent.audit.AuditRecord;
import com.chilly.researchagent.react.AgentDecision;
import com.chilly.researchagent.react.ReActLoop;
import com.chilly.researchagent.react.ReActResult;
import com.chilly.researchagent.react.ReActStep;
import com.chilly.researchagent.web.dto.ChatRequest;
import com.chilly.researchagent.web.dto.ChatResponse;
import com.chilly.researchagent.web.dto.StepAuditDto;
import com.chilly.researchagent.web.dto.StreamDoneEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 编排 ReAct 循环、会话 ID 与审计日志；Controller 仅负责 HTTP 映射。
 */
@Service
public class AgentService {

    private final ReActLoop reActLoop;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AgentService(ReActLoop reActLoop, AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.reActLoop = reActLoop;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /** 非流式对话：跑完 ReAct 后返回完整 JSON 响应。 */
    public ChatResponse chat(ChatRequest request) {
        AgentRun run = execute(request);
        return toChatResponse(run.sessionId(), run.result());
    }

    /** 流式对话：手写 SSE 并 flush，step 在 ReAct 过程中实时推送。 */
    public StreamingResponseBody streamChatBody(ChatRequest request) {
        return outputStream -> streamChatToOutput(request, outputStream);
    }

    private void streamChatToOutput(ChatRequest request, OutputStream outputStream) throws IOException {
        String requestId = UUID.randomUUID().toString();
        String sessionId = resolveSessionId(request.sessionId());
        Instant startedAt = Instant.now();
        long startedMs = System.currentTimeMillis();

        try {
            java.util.concurrent.atomic.AtomicBoolean tokensSent = new java.util.concurrent.atomic.AtomicBoolean(false);

            ReActResult result = reActLoop.run(
                    sessionId,
                    request.message(),
                    step -> {
                        if (!AgentDecision.ACTION_CALL_TOOL.equals(step.action())) {
                            return;
                        }
                        try {
                            String json = objectMapper.writeValueAsString(toStepAuditDto(step));
                            SseEventWriter.send(outputStream, "step", json);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    },
                    token -> {
                        try {
                            tokensSent.set(true);
                            SseEventWriter.send(outputStream, "token", token);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

            auditLogService.log(new AuditRecord(
                    requestId,
                    sessionId,
                    request.message(),
                    toAuditSteps(result.steps()),
                    result.finalAnswer(),
                    result.terminatedReason().name(),
                    startedAt,
                    System.currentTimeMillis() - startedMs));

            if (!tokensSent.get() && result.finalAnswer() != null && !result.finalAnswer().isBlank()) {
                SseEventWriter.send(outputStream, "token", result.finalAnswer());
            }

            StreamDoneEvent done = new StreamDoneEvent(sessionId, result.terminatedReason().name());
            SseEventWriter.send(outputStream, "done", objectMapper.writeValueAsString(done));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private AgentRun execute(ChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        String sessionId = resolveSessionId(request.sessionId());
        Instant startedAt = Instant.now();
        long startedMs = System.currentTimeMillis();

        ReActResult result = reActLoop.run(sessionId, request.message());

        auditLogService.log(new AuditRecord(
                requestId,
                sessionId,
                request.message(),
                toAuditSteps(result.steps()),
                result.finalAnswer(),
                result.terminatedReason().name(),
                startedAt,
                System.currentTimeMillis() - startedMs));

        return new AgentRun(sessionId, result);
    }

    private static String resolveSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private static ChatResponse toChatResponse(String sessionId, ReActResult result) {
        List<StepAuditDto> steps = result.steps().stream()
                .map(AgentService::toStepAuditDto)
                .toList();
        return new ChatResponse(
                sessionId,
                result.finalAnswer(),
                result.terminatedReason().name(),
                steps);
    }

    private static StepAuditDto toStepAuditDto(ReActStep step) {
        return new StepAuditDto(
                step.stepIndex(),
                step.action(),
                step.tool(),
                step.params() != null ? step.params() : java.util.Map.of());
    }

    private static List<AuditRecord.AuditStepEntry> toAuditSteps(List<ReActStep> steps) {
        return steps.stream()
                .map(step -> new AuditRecord.AuditStepEntry(
                        step.stepIndex(),
                        step.action(),
                        step.tool(),
                        step.params(),
                        step.observation()))
                .toList();
    }

    private record AgentRun(String sessionId, ReActResult result) {
    }
}
