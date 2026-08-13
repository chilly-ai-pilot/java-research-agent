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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 编排 ReAct 循环、会话 ID 与审计日志；Controller 仅负责 HTTP 映射。
 */
@Service
public class AgentService {

    private static final long SSE_TIMEOUT_MS = 120_000L;
    private static final int STREAM_CHUNK_SIZE = 80;

    private final ReActLoop reActLoop;
    private final AuditLogService auditLogService;

    public AgentService(ReActLoop reActLoop, AuditLogService auditLogService) {
        this.reActLoop = reActLoop;
        this.auditLogService = auditLogService;
    }

    /** 非流式对话：跑完 ReAct 后返回完整 JSON 响应。 */
    public ChatResponse chat(ChatRequest request) {
        AgentRun run = execute(request);
        return toChatResponse(run.sessionId(), run.result());
    }

    /** 流式对话：先推送 step 事件，再分块推送 answer，最后 done。 */
    public SseEmitter streamChat(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        CompletableFuture.runAsync(() -> streamChatAsync(request, emitter));
        return emitter;
    }

    private void streamChatAsync(ChatRequest request, SseEmitter emitter) {
        try {
            AgentRun run = execute(request);
            ReActResult result = run.result();

            for (ReActStep step : result.steps()) {
                if (AgentDecision.ACTION_CALL_TOOL.equals(step.action())) {
                    StepAuditDto dto = toStepAuditDto(step);
                    emitter.send(SseEmitter.event().name("step").data(dto, MediaType.APPLICATION_JSON));
                }
            }

            for (String chunk : chunkText(result.finalAnswer(), STREAM_CHUNK_SIZE)) {
                emitter.send(SseEmitter.event().name("token").data(chunk));
            }

            StreamDoneEvent done = new StreamDoneEvent(run.sessionId(), result.terminatedReason().name());
            emitter.send(SseEmitter.event().name("done").data(done, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        } catch (RuntimeException e) {
            try {
                emitter.completeWithError(e);
            } catch (Exception ignored) {
                // emitter may already be closed
            }
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

    private static List<String> chunkText(String text, int chunkSize) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return chunks;
    }

    private record AgentRun(String sessionId, ReActResult result) {
    }
}
