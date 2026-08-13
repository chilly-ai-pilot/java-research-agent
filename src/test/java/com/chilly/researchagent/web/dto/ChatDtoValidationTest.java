package com.chilly.researchagent.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatDtoValidationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void deserializesChatRequestWithNullableSessionId() throws Exception {
        ChatRequest request = objectMapper.readValue("""
                {"message":"帮我查一下 Transformer","sessionId":null,"stream":false}
                """, ChatRequest.class);

        assertThat(request.message()).isEqualTo("帮我查一下 Transformer");
        assertThat(request.sessionId()).isNull();
        assertThat(request.stream()).isFalse();
    }

    @Test
    void deserializesChatRequestWithMissingStreamAsFalse() throws Exception {
        ChatRequest request = objectMapper.readValue("""
                {"message":"你好"}
                """, ChatRequest.class);

        assertThat(request.stream()).isFalse();
    }

    @Test
    void serializesChatResponseJsonShape() throws Exception {
        ChatResponse response = new ChatResponse(
                "550e8400-e29b-41d4-a716-446655440000",
                "Transformer 是一种模型架构",
                "LLM_FINISH",
                List.of(new StepAuditDto(
                        1,
                        "call_tool",
                        "search_knowledge",
                        Map.of("query", "Transformer"))));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"sessionId\"");
        assertThat(json).contains("\"answer\"");
        assertThat(json).contains("\"terminatedReason\"");
        assertThat(json).contains("\"steps\"");
        assertThat(json).contains("search_knowledge");
        assertThat(json).doesNotContain("observation");
    }

    @Test
    void roundTripsStepAuditDto() throws Exception {
        StepAuditDto original = new StepAuditDto(
                2, "call_tool", "web_search", Map.of("query", "attention"));

        StepAuditDto restored = objectMapper.readValue(
                objectMapper.writeValueAsString(original), StepAuditDto.class);

        assertThat(restored).isEqualTo(original);
    }
}
