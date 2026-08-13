package com.chilly.researchagent.web.dto;

/**
 * SSE {@code event: done}  payload。
 */
public record StreamDoneEvent(String sessionId, String terminatedReason) {
}
