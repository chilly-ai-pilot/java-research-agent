package com.chilly.researchagent.tool;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iteration 1 Step 6：端到端验收，需三个真实 MCP Server + MySQL 就绪，
 * 本地手动跑：{@code MCP_INTEGRATION=true mvn -q test}。CI / 默认
 * {@code mvn test} 不跑（不装 Server 也不装 MySQL）。
 *
 * <p>Design.md / Iteration1.md 写的是 create_flashcard/list_cards
 * （snake_case），真实部署的 java-flashcard-mcp 的 Tool 名是
 * createFlashcard/listCards（camelCase）——这里断言的是真实能调通的
 * 名字，见 McpConnectivityRunner 的同名说明。
 *
 * <p>已知问题：{@code searchKnowledgeReturnsNonEmptyResult} 目前会失败——
 * rag-mcp 首次加载 embedding 模型时把日志打到 stdout 而非 stderr，
 * 污染 JSON-RPC 流，且该连接一旦被污染就永久失效（已用重试验证过，
 * 不是一次性问题）。这是 rag-mcp 自身的 bug，不在本仓库范围内修，
 * 详见 commit dcb0aba。
 *
 * <p>连带影响：{@code McpConnectivityRunner} 是 {@code @Profile("mcp")} 的
 * ApplicationRunner，本类用 {@code @ActiveProfiles("mcp")} 加载上下文时它会
 * 自动跑一遍（含 search_knowledge），于是 rag-mcp 连接在任何测试方法执行前
 * 就已被污染。这会连带拖垮 {@code listAllToolsReturnsAllFiveTools}——它调用
 * listTools() 会遍历所有 client，卡在同一个坏连接上直到 30s 超时——即使
 * "列出 Tool" 这个功能本身没有问题。
 */
@SpringBootTest
@ActiveProfiles("mcp")
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")
class McpConnectivityIntegrationTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void listAllToolsReturnsAllFiveTools() {
        List<ToolDescriptor> tools = toolRegistry.listAllTools();
        assertThat(tools).hasSize(5);
        assertThat(tools.stream().map(ToolDescriptor::name)).containsExactlyInAnyOrder(
                "search_knowledge", "generate_answer", "web_search", "createFlashcard", "listCards");
    }

    @Test
    void searchKnowledgeReturnsNonEmptyResult() {
        String result = toolRegistry.callTool("search_knowledge", Map.of("query", "测试查询", "top_k", 3));
        assertThat(result).isNotBlank();
    }

    @Test
    void webSearchReturnsNonEmptyResult() {
        String result = toolRegistry.callTool("web_search", Map.of("query", "Spring AI MCP", "max_results", 3));
        assertThat(result).isNotBlank();
    }
}
