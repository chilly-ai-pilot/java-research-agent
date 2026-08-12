package com.chilly.researchagent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动探针：列出三个 MCP Server 聚合后的全部 Tool。「能列出 Tool」和
 * 「能调用 Tool」是两层问题——先证明列表非空、名字对得上，Iteration 1
 * Step 4/5 再验证真正的调用链路。
 */
@Component
@Profile("mcp")
public class McpStartupProbe implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpStartupProbe.class);

    private final ToolRegistry toolRegistry;

    public McpStartupProbe(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ToolDescriptor> tools = toolRegistry.listAllTools();
        log.info("MCP tool discovery: {} tool(s) found", tools.size());
        for (ToolDescriptor tool : tools) {
            log.info("tool={} server={} description={}", tool.name(), tool.serverName(), tool.description());
        }
    }
}
