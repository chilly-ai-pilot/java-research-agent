package com.chilly.researchagent.tool;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聚合所有已连接 MCP Server 的 Tool 定义。mcpSyncClients 由
 * spring-ai-starter-mcp-client 根据 spring.ai.mcp.client.stdio.connections
 * 自动装配，这里只做聚合，不关心具体是哪个 Server。
 */
@Component
public class ToolRegistry {

    private final List<McpSyncClient> mcpSyncClients;

    public ToolRegistry(List<McpSyncClient> mcpSyncClients) {
        this.mcpSyncClients = mcpSyncClients;
    }

    public List<ToolDescriptor> listAllTools() {
        return mcpSyncClients.stream()
                .flatMap(client -> client.listTools().tools().stream()
                        .map(tool -> new ToolDescriptor(
                                tool.name(),
                                tool.description(),
                                client.getServerInfo().name(),
                                tool.inputSchema())))
                .toList();
    }
}
