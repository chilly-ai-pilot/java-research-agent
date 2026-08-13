package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import com.chilly.researchagent.memory.NoOpLongTermMemory;
import com.chilly.researchagent.tool.ToolDescriptor;
import com.chilly.researchagent.tool.ToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReActPromptBuilderTest {

    @Mock
    private ToolRegistry toolRegistry;

    private ReActPromptBuilder promptBuilder;

    /** 每个测试前构造 PromptBuilder。 */
    @BeforeEach
    void setUp() {
        promptBuilder = new ReActPromptBuilder(
                toolRegistry, new PromptTemplateLoader(defaultAgentProperties()), new NoOpLongTermMemory());
    }

    /** system prompt 应包含静态规则与动态 Tool 名。 */
    @Test
    void buildSystemPromptIncludesBaseRulesAndDynamicTools() {
        when(toolRegistry.listAllTools()).thenReturn(List.of(
                new ToolDescriptor("search_knowledge", "检索知识库", "rag-mcp", emptySchema()),
                new ToolDescriptor("web_search", "上网搜索", "search-mcp", emptySchema())));

        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("你是一个 AI 研究助手");
        assertThat(prompt).contains("决策规则");
        assertThat(prompt).contains("search_knowledge: 检索知识库");
        assertThat(prompt).contains("web_search: 上网搜索");
    }

    /** 无可用 Tool 时应提示 LLM 直接回答。 */
    @Test
    void buildSystemPromptWhenNoToolsAvailable() {
        when(toolRegistry.listAllTools()).thenReturn(List.of());

        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("当前没有可用的工具，直接回答用户问题。");
        assertThat(prompt).doesNotContain("当前已连接的工具");
    }

    /** Tool 描述为空时应显示占位文案。 */
    @Test
    void buildSystemPromptUsesPlaceholderWhenToolDescriptionMissing() {
        when(toolRegistry.listAllTools()).thenReturn(List.of(
                new ToolDescriptor("search_knowledge", null, "rag-mcp", emptySchema()),
                new ToolDescriptor("web_search", "   ", "search-mcp", emptySchema())));

        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("search_knowledge: 无描述");
        assertThat(prompt).contains("web_search: 无描述");
    }

    /** step prompt 应按顺序包含各步 observation。 */
    @Test
    void buildStepPromptIncludesStepsInOrder() {
        List<ReActStep> steps = List.of(
                new ReActStep(
                        1,
                        AgentDecision.ACTION_CALL_TOOL,
                        "search_knowledge",
                        Map.of("query", "Transformer"),
                        "找到 3 条相关文档",
                        Instant.parse("2026-08-13T10:00:00Z")),
                new ReActStep(
                        2,
                        AgentDecision.ACTION_CALL_TOOL,
                        "web_search",
                        Map.of("query", "Transformer 最新"),
                        "找到 5 条网页结果",
                        Instant.parse("2026-08-13T10:01:00Z")));

        String prompt = promptBuilder.buildStepPrompt(steps, "帮我查 Transformer");

        assertThat(prompt).contains("用户问题：帮我查 Transformer");
        assertThat(prompt.indexOf("找到 3 条相关文档"))
                .isLessThan(prompt.indexOf("找到 5 条网页结果"));
        assertThat(prompt).contains("--- Step 1 ---");
        assertThat(prompt).contains("--- Step 2 ---");
        assertThat(prompt).contains("tool: search_knowledge");
        assertThat(prompt).contains("tool: web_search");
        assertThat(prompt).contains("请根据以上信息，输出下一步 JSON 决策");
    }

    /** Tool 数量较多时每行格式应稳定：name: description。 */
    @Test
    void buildSystemPromptHandlesManyTools() {
        List<ToolDescriptor> tools = IntStream.range(0, 20)
                .mapToObj(i -> new ToolDescriptor("tool" + i, "desc" + i, "server", emptySchema()))
                .toList();
        when(toolRegistry.listAllTools()).thenReturn(tools);

        String prompt = promptBuilder.buildSystemPrompt();

        assertThat(prompt).contains("tool0: desc0");
        assertThat(prompt).contains("tool19: desc19");
        assertThat(prompt).doesNotContain("tool20");
    }

    /** step params 含引号等特殊字符时应完整出现在 prompt 中。 */
    @Test
    void buildStepPromptIncludesParamsWithSpecialCharacters() {
        List<ReActStep> steps = List.of(new ReActStep(
                1,
                AgentDecision.ACTION_CALL_TOOL,
                "search_knowledge",
                Map.of("query", "什么是\"MCP\"？"),
                "找到结果",
                Instant.parse("2026-08-13T10:00:00Z")));

        String prompt = promptBuilder.buildStepPrompt(steps, "查 MCP");

        assertThat(prompt).contains("params:");
        assertThat(prompt).contains("什么是\"MCP\"？");
        assertThat(prompt).contains("请根据以上信息，输出下一步 JSON 决策");
    }

    /** 无历史 step 时 prompt 只含用户问题与决策指令。 */
    @Test
    void buildStepPromptWithEmptyHistory() {
        String prompt = promptBuilder.buildStepPrompt(List.of(), "你好");

        assertThat(prompt).contains("用户问题：你好");
        assertThat(prompt).doesNotContain("--- Step");
        assertThat(prompt).contains("请根据以上信息，输出下一步 JSON 决策");
    }

    /** NoOp 长期记忆 recall 为空时，prompt 不应包含「相关历史记忆」。 */
    @Test
    void buildSystemPromptOmitsLongTermSectionWhenRecallEmpty() {
        when(toolRegistry.listAllTools()).thenReturn(List.of());

        String prompt = promptBuilder.buildSystemPrompt("session-1", "Transformer");

        assertThat(prompt).doesNotContain("相关历史记忆");
    }

    /** MCP SDK 0.18.3: type, properties, required, additionalProperties, defs, definitions */
    private static McpSchema.JsonSchema emptySchema() {
        return new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null);
    }

    private static AgentProperties defaultAgentProperties() {
        return new AgentProperties(10, 30_000L, 90_000L, 2000, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
    }
}
