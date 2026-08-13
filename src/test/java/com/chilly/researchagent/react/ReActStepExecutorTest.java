package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import com.chilly.researchagent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReActStepExecutorTest {

    @Mock
    private GatewayChatService gatewayChatService;

    @Mock
    private ToolRegistry toolRegistry;

    private AgentDecisionParser decisionParser;
    private ReActPromptBuilder promptBuilder;
    private ReActStepExecutor executor;

    @BeforeEach
    void setUp() {
        decisionParser = new AgentDecisionParser(new ObjectMapper());
        promptBuilder = new ReActPromptBuilder(toolRegistry, new PromptTemplateLoader(defaultAgentProperties()));
        executor = new ReActStepExecutor(
                gatewayChatService,
                toolRegistry,
                decisionParser,
                promptBuilder,
                defaultAgentProperties(),
                ReActTraceLogger.forTests());
    }

    /** LLM 返回 finish 时应直接结束。 */
    @Test
    void returnsFinishedWhenLlmDecidesToFinish() {
        when(toolRegistry.listAllTools()).thenReturn(List.of());
        when(gatewayChatService.chat(any(), any(), any()))
                .thenReturn("{\"action\":\"finish\",\"answer\":\"ok\"}");

        StepResult result = executor.executeOneStep(new ReActContext("hello"));

        assertThat(result).isInstanceOf(StepResult.Finished.class);
        assertThat(((StepResult.Finished) result).answer()).isEqualTo("ok");
    }

    /** LLM 返回 call_tool 时应调用 Tool 并截断 observation。 */
    @Test
    void returnsContinueWithTruncatedObservationWhenCallingTool() {
        AgentProperties properties = new AgentProperties(10, 30_000L, 90_000L, 10, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
        executor = new ReActStepExecutor(
                gatewayChatService, toolRegistry, decisionParser, promptBuilder, properties,
                ReActTraceLogger.forTests());

        when(toolRegistry.listAllTools()).thenReturn(List.of());
        when(gatewayChatService.chat(any(), any(), any())).thenReturn("""
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"Transformer"}}
                """);
        when(toolRegistry.callTool(eq("search_knowledge"), any())).thenReturn("12345678901234567890");

        StepResult result = executor.executeOneStep(new ReActContext("查 Transformer"));

        assertThat(result).isInstanceOf(StepResult.Continue.class);
        StepResult.Continue continued = (StepResult.Continue) result;
        assertThat(continued.observation()).hasSize(10);
        assertThat(continued.decision().tool()).isEqualTo("search_knowledge");
    }

    /** Tool 调用超过 step-timeout-ms 时应抛 StepTimeoutException。 */
    @Test
    void throwsStepTimeoutWhenToolCallExceedsTimeout() {
        AgentProperties properties = new AgentProperties(10, 100L, 90_000L, 2000, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
        executor = new ReActStepExecutor(
                gatewayChatService, toolRegistry, decisionParser, promptBuilder, properties,
                ReActTraceLogger.forTests());

        when(toolRegistry.listAllTools()).thenReturn(List.of());
        when(gatewayChatService.chat(any(), any(), any())).thenReturn("""
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"Transformer"}}
                """);
        when(toolRegistry.callTool(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(2_000);
            return "slow result";
        });

        assertThatThrownBy(() -> executor.executeOneStep(new ReActContext("slow")))
                .isInstanceOf(StepTimeoutException.class)
                .hasMessageContaining("100ms");
    }

    private static AgentProperties defaultAgentProperties() {
        return new AgentProperties(10, 30_000L, 90_000L, 2000, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
    }
}
