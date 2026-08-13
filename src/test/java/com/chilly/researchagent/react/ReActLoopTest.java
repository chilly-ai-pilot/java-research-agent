package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import com.chilly.researchagent.memory.ChatMemoryProperties;
import com.chilly.researchagent.memory.NoOpLongTermMemory;
import com.chilly.researchagent.memory.SessionChatMemory;
import com.chilly.researchagent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReActLoopTest {

    @Mock
    private GatewayChatService gatewayChatService;

    @Mock
    private ToolRegistry toolRegistry;

    private ReActLoop loop;

    @BeforeEach
    void setUp() {
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
                new SessionChatMemory(new ChatMemoryProperties(20, 60)),
                new ChatMemoryProperties(20, 60));
        when(toolRegistry.listAllTools()).thenReturn(List.of());
    }

    /** 第一步 call_tool、第二步 finish 时应正常结束。 */
    @Test
    void finishesWhenLlmCallsToolThenFinishes() {
        when(gatewayChatService.chat(any(), any(), any()))
                .thenReturn("{\"action\":\"call_tool\",\"tool\":\"search_knowledge\",\"params\":{\"query\":\"Transformer\"}}")
                .thenReturn("{\"action\":\"finish\",\"answer\":\"done\"}");
        when(toolRegistry.callTool(eq("search_knowledge"), any())).thenReturn("found docs");

        ReActResult result = loop.run("查 Transformer");

        assertThat(result.terminatedReason()).isEqualTo(TerminatedReason.LLM_FINISH);
        assertThat(result.finalAnswer()).isEqualTo("done");
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().getFirst().tool()).isEqualTo("search_knowledge");
        assertThat(result.steps().get(1).action()).isEqualTo(AgentDecision.ACTION_FINISH);
    }

    /** LLM 持续 call_tool 且 params 各不相同时应触达 max-steps。 */
    @Test
    void stopsAtMaxStepsWhenLlmKeepsCallingTools() {
        AtomicInteger counter = new AtomicInteger();
        when(gatewayChatService.chat(any(), any(), any())).thenAnswer(invocation -> """
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"q%s"}}
                """.formatted(counter.getAndIncrement()));
        when(toolRegistry.callTool(eq("search_knowledge"), any())).thenReturn("observation");

        ReActResult result = loop.run("keep going");

        assertThat(result.terminatedReason()).isEqualTo(TerminatedReason.MAX_STEPS);
        assertThat(result.finalAnswer()).contains("最大步数");
        assertThat(result.steps()).hasSize(10);
    }

    /** 连续两次相同 (tool, params) 决策时应以 ERROR 终止。 */
    @Test
    void stopsWhenSameToolCallRepeats() {
        String sameDecision = """
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"Transformer"}}
                """;
        when(gatewayChatService.chat(any(), any(), any()))
                .thenReturn(sameDecision)
                .thenReturn(sameDecision);
        when(toolRegistry.callTool(eq("search_knowledge"), any())).thenReturn("found docs");

        ReActResult result = loop.run("test");

        assertThat(result.terminatedReason()).isEqualTo(TerminatedReason.ERROR);
        assertThat(result.finalAnswer()).contains("重复调用");
        assertThat(result.steps()).hasSize(1);
    }

    private static AgentProperties defaultAgentProperties() {
        return new AgentProperties(10, 30_000L, 90_000L, 2000, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
    }
}
