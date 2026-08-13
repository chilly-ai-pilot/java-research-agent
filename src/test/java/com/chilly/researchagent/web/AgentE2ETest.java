package com.chilly.researchagent.web;

import com.chilly.researchagent.memory.ChatMemory;
import com.chilly.researchagent.web.dto.ChatRequest;
import com.chilly.researchagent.web.dto.ChatResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iteration 5 Step 6：REST 端到端验收（需 Gateway + MCP）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("mcp")
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")
@EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")
class AgentE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private ChatMemory chatMemory;

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofMinutes(3))
                .build();
    }

    @Test
    void nonStreamingChatReturnsAnswerAndSteps() {
        ChatResponse response = webTestClient()
                .post()
                .uri("/api/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("你好", null, false))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.terminatedReason()).isNotBlank();
        assertThat(response.steps()).isNotNull();
    }

    @Test
    void streamingChatReturnsSseEventsIncludingDone() {
        List<String> body = webTestClient()
                .post()
                .uri("/api/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("你好", null, true))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block(Duration.ofMinutes(3));

        assertThat(body).isNotNull();
        String joined = String.join("\n", body);
        assertThat(joined).contains("LLM_FINISH");
        assertThat(joined).isNotBlank();
    }

    @Test
    void sameSessionIdCarriesHistoryAcrossTwoTurns() {
        String sessionId = "e2e-" + UUID.randomUUID();

        chatMemory.clear(sessionId);

        ChatResponse turn1 = webTestClient()
                .post()
                .uri("/api/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("帮我上网搜一下注意力机制", sessionId, false))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(turn1).isNotNull();
        assertThat(turn1.sessionId()).isEqualTo(sessionId);
        assertThat(turn1.answer()).isNotBlank();

        ChatResponse turn2 = webTestClient()
                .post()
                .uri("/api/agent/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ChatRequest("刚才提到的再详细讲一下", sessionId, false))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(turn2).isNotNull();
        assertThat(turn2.answer()).isNotBlank();
        assertThat(turn2.answer().toLowerCase())
                .satisfiesAnyOf(
                        answer -> assertThat(answer).contains("注意力"),
                        answer -> assertThat(answer).contains("attention"));

        assertThat(chatMemory.getRecent(sessionId, 10)).hasSizeGreaterThanOrEqualTo(4);
    }
}
