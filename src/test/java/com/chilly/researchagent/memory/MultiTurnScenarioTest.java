package com.chilly.researchagent.memory;

import com.chilly.researchagent.react.GatewayChatService;
import com.chilly.researchagent.react.ReActLoop;
import com.chilly.researchagent.react.ReActResult;
import com.chilly.researchagent.react.ReActScenarioSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

/**
 * Iteration 4 Step 5：多轮对话 Memory 端到端验收。
 *
 * <p>需 Gateway + MCP 同时就绪：
 * {@code GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn -q test -Dtest=MultiTurnScenarioTest}
 */
@SpringBootTest
@ActiveProfiles("mcp")
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")
@EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")
class MultiTurnScenarioTest {

    private static final String SESSION_ID = "mem-test-1";
    private static final String TURN1_QUESTION = "帮我查一下 Transformer";
    private static final String TURN2_QUESTION = "刚才提到的注意力机制再详细讲一下";

    @Autowired
    private ReActLoop reActLoop;

    @Autowired
    private ChatMemory chatMemory;

    @SpyBean
    private GatewayChatService gatewayChatService;

    @BeforeEach
    void setUp() {
        chatMemory.clear(SESSION_ID);
        chatMemory.clear("mem-test-2");
        clearInvocations(gatewayChatService);
    }

    /** Turn 1 检索 + Turn 2 指代追问：history 含 Turn 1 QA，答案提及注意力机制。 */
    @Test
    void sameSessionTurn2UsesTurn1HistoryAndExplainsAttention() {
        ReActResult turn1 = reActLoop.run(SESSION_ID, TURN1_QUESTION);
        assertThat(ReActScenarioSupport.usedKnowledgeTool(turn1)).isTrue();
        assertThat(turn1.finalAnswer()).isNotBlank();

        clearInvocations(gatewayChatService);

        ReActResult turn2 = reActLoop.run(SESSION_ID, TURN2_QUESTION);
        assertThat(turn2.finalAnswer()).isNotBlank();
        assertThat(turn2.finalAnswer().toLowerCase())
                .satisfiesAnyOf(
                        answer -> assertThat(answer).contains("注意力"),
                        answer -> assertThat(answer).contains("attention"));

        ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(gatewayChatService, atLeastOnce()).chat(any(), historyCaptor.capture(), eq(TURN2_QUESTION));

        List<Message> turn2History = historyCaptor.getAllValues().getFirst();
        assertThat(turn2History).hasSize(2);
        assertThat(turn2History.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) turn2History.get(0)).getText()).isEqualTo(TURN1_QUESTION);
        assertThat(turn2History.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) turn2History.get(1)).getText()).isEqualTo(turn1.finalAnswer());
    }

    /** 对照组：新 session 的 Turn 2 不应携带 Turn 1 history。 */
    @Test
    void newSessionTurn2StartsWithEmptyHistory() {
        reActLoop.run(SESSION_ID, TURN1_QUESTION);

        clearInvocations(gatewayChatService);

        reActLoop.run("mem-test-2", TURN2_QUESTION);

        ArgumentCaptor<List<Message>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(gatewayChatService, atLeastOnce()).chat(any(), historyCaptor.capture(), eq(TURN2_QUESTION));

        assertThat(historyCaptor.getAllValues().getFirst()).isEmpty();
    }
}
