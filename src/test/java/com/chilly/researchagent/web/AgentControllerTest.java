package com.chilly.researchagent.web;

import com.chilly.researchagent.web.dto.ChatRequest;
import com.chilly.researchagent.web.dto.ChatResponse;
import com.chilly.researchagent.web.dto.StepAuditDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentService agentService;

    @Test
    void postValidBodyReturns200WithJsonStructure() throws Exception {
        when(agentService.chat(any(ChatRequest.class))).thenReturn(new ChatResponse(
                "session-1",
                "hello answer",
                "LLM_FINISH",
                List.of(new StepAuditDto(1, "call_tool", "search_knowledge", Map.of("query", "Transformer")))));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"帮我查一下 Transformer","sessionId":null,"stream":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.answer").value("hello answer"))
                .andExpect(jsonPath("$.terminatedReason").value("LLM_FINISH"))
                .andExpect(jsonPath("$.steps[0].tool").value("search_knowledge"));
    }

    @Test
    void missingMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"s1","stream":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"   ","stream":false}
                                """))
                .andExpect(status().isBadRequest());
    }
}
