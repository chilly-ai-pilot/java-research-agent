package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import com.chilly.researchagent.memory.ChatMemoryProperties;
import com.chilly.researchagent.memory.ChatMessage;
import com.chilly.researchagent.memory.NoOpLongTermMemory;
import com.chilly.researchagent.memory.SessionChatMemory;
import com.chilly.researchagent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.chilly.researchagent.memory.ChatMessage.Role.ASSISTANT;
import static com.chilly.researchagent.memory.ChatMessage.Role.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReActLoopMemoryTest {

    private static final String SESSION_ID = "memory-test-session";

    @Mock
    private GatewayChatService gatewayChatService;

    @Mock
    private ToolRegistry toolRegistry;

    private SessionChatMemory chatMemory;
    private ReActLoop loop;

    @BeforeEach
    void setUp() {
        ChatMemoryProperties memoryProperties = new ChatMemoryProperties(20, 60);
        chatMemory = new SessionChatMemory(memoryProperties);

        AgentDecisionParser decisionParser = new AgentDecisionParser(new ObjectMapper());
        ReActPromptBuilder promptBuilder = new ReActPromptBuilder(
                toolRegistry, new PromptTemplateLoader(defaultAgentProperties()), new NoOpLongTermMemory());
        ReActStepExecutor stepExecutor = new ReActStepExecutor(
                gatewayChatService,
                toolRegistry,
                decisionParser,
                promptBuilder,
                defaultAgentProperties(),
                ReActTraceLogger.forTests());
        loop = new ReActLoop(
                stepExecutor,
                defaultAgentProperties(),
                ReActTraceLogger.forTests(),
                chatMemory,
                memoryProperties);
        when(toolRegistry.listAllTools()).thenReturn(List.of());
    }

    /** 跑完一轮后 memory 里恰好 2 条（USER + ASSISTANT）。 */
    @Test
    void storesUserAndAssistantAfterOneRun() {
        when(gatewayChatService.chat(any(), any(), any()))
                .thenReturn("{\"action\":\"finish\",\"answer\":\"first answer\"}");

        loop.run(SESSION_ID, "hello");

        List<ChatMessage> recent = chatMemory.getRecent(SESSION_ID, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).role()).isEqualTo(USER);
        assertThat(recent.get(0).content()).isEqualTo("hello");
        assertThat(recent.get(1).role()).isEqualTo(ASSISTANT);
        assertThat(recent.get(1).content()).isEqualTo("first answer");
    }

    /** 第二轮 LLM 收到的 history 应包含第一轮 QA。 */
    @Test
    void secondRunReceivesFirstRoundHistory() {
        AtomicInteger callCount = new AtomicInteger();
        when(gatewayChatService.chat(any(), any(), any())).thenAnswer(invocation -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                @SuppressWarnings("unchecked")
                List<Message> history = invocation.getArgument(1);
                assertThat(history).isEmpty();
                return "{\"action\":\"finish\",\"answer\":\"first answer\"}";
            }
            @SuppressWarnings("unchecked")
            List<Message> history = invocation.getArgument(1);
            assertThat(history).hasSize(2);
            assertThat(history.get(0)).isInstanceOf(UserMessage.class);
            assertThat(((UserMessage) history.get(0)).getText()).isEqualTo("first question");
            assertThat(history.get(1)).isInstanceOf(AssistantMessage.class);
            assertThat(((AssistantMessage) history.get(1)).getText()).isEqualTo("first answer");
            return "{\"action\":\"finish\",\"answer\":\"second answer\"}";
        });

        loop.run(SESSION_ID, "first question");
        loop.run(SESSION_ID, "second question");

        List<ChatMessage> recent = chatMemory.getRecent(SESSION_ID, 10);
        assertThat(recent).hasSize(4);
        assertThat(recent.get(2).content()).isEqualTo("second question");
        assertThat(recent.get(3).content()).isEqualTo("second answer");
    }

    private static AgentProperties defaultAgentProperties() {
        return new AgentProperties(10, 30_000L, 90_000L, 2000, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
    }
}
