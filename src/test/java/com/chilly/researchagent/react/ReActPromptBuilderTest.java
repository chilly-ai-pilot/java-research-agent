package com.chilly.researchagent.react;

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
        promptBuilder = new ReActPromptBuilder(toolRegistry);
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

    /** 无历史 step 时 prompt 只含用户问题与决策指令。 */
    @Test
    void buildStepPromptWithEmptyHistory() {
        String prompt = promptBuilder.buildStepPrompt(List.of(), "你好");

        assertThat(prompt).contains("用户问题：你好");
        assertThat(prompt).doesNotContain("--- Step");
        assertThat(prompt).contains("请根据以上信息，输出下一步 JSON 决策");
    }

    private static McpSchema.JsonSchema emptySchema() {
        return new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null);
    }
}
