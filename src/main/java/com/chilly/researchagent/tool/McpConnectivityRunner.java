package com.chilly.researchagent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动后手动 smoke 三个 Server 各一条真实调用链路，跑在 McpStartupProbe（列 Tool）
 * 之后：先证明「Tool 存在」，再证明「Tool 能调通」，两层问题分开定位。
 *
 * <p>Design.md / Iteration1.md 写的是 create_flashcard/list_cards（snake_case），
 * 但真实部署的 java-flashcard-mcp 的 @Tool 方法名是 createFlashcard/listCards
 * （camelCase，无显式 name= 覆盖，Spring AI 直接用 Java 方法名）——这里用的是
 * 实际能调通的真实名字，不是文档里写的名字。
 */
@Component
@Profile("mcp")
@Order(2)
public class McpConnectivityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpConnectivityRunner.class);
    private static final int SUMMARY_MAX_CHARS = 200;
    private static final String FLASHCARD_TEST_TITLE_PREFIX = "iteration1-test-";

    private final ToolRegistry toolRegistry;

    public McpConnectivityRunner(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        callAndLog("search_knowledge", Map.of("query", "测试查询", "top_k", 3));
        callAndLog("web_search", Map.of("query", "Spring AI MCP", "max_results", 3));

        String testTitle = FLASHCARD_TEST_TITLE_PREFIX + System.currentTimeMillis();
        callAndLog("createFlashcard", Map.of("title", testTitle, "content", "验证 MCP 连通"));
        callAndLog("listCards", Map.of());
    }

    private void callAndLog(String toolName, Map<String, Object> arguments) {
        try {
            String result = toolRegistry.callTool(toolName, arguments);
            log.info("mcp call ok tool={} result={}", toolName, truncate(result));
        } catch (RuntimeException e) {
            log.error("mcp call failed tool={}", toolName, e);
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= SUMMARY_MAX_CHARS) {
            return text;
        }
        return text.substring(0, SUMMARY_MAX_CHARS) + "...";
    }
}
