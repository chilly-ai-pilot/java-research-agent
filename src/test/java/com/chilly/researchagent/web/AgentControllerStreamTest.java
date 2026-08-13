package com.chilly.researchagent.web;

import com.chilly.researchagent.web.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentService agentService;

    @Test
    void streamTrueReturnsEventStreamWithEvents() throws Exception {
        when(agentService.streamChatBody(any(ChatRequest.class))).thenReturn(outputStream -> {
            SseEventWriter.send(outputStream, "step", "{\"tool\":\"web_search\"}");
            SseEventWriter.send(outputStream, "token", "hello");
            SseEventWriter.send(outputStream, "done", "{\"sessionId\":\"session-1\",\"terminatedReason\":\"LLM_FINISH\"}");
        });

        mockMvc.perform(post("/api/agent/chat")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"你好","stream":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:step")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }
}
