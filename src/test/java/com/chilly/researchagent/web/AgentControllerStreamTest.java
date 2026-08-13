package com.chilly.researchagent.web;

import com.chilly.researchagent.web.dto.ChatRequest;
import com.chilly.researchagent.web.dto.StepAuditDto;
import com.chilly.researchagent.web.dto.StreamDoneEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentService agentService;

    @Test
    void streamTrueReturnsEventStreamWithEvents() throws Exception {
        when(agentService.streamChat(any(ChatRequest.class))).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter(5_000L);
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("step")
                            .data(new StepAuditDto(1, "call_tool", "web_search", Map.of("query", "test")),
                                    MediaType.APPLICATION_JSON));
                    emitter.send(SseEmitter.event().name("token").data("hello"));
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(new StreamDoneEvent("session-1", "LLM_FINISH"), MediaType.APPLICATION_JSON));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        });

        var mvcResult = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"你好","stream":true}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:step")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }
}
