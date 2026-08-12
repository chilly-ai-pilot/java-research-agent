package com.chilly.researchagent.tool;

import com.chilly.researchagent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 聚合所有已连接 MCP Server 的 Tool 定义，并提供统一的调用入口。
 * mcpSyncClients 由 spring-ai-starter-mcp-client 根据
 * spring.ai.mcp.client.stdio.connections 自动装配，这里只做聚合，不关心具体是哪个 Server。
 */
@Component
public class ToolRegistry {

    private final List<McpSyncClient> mcpSyncClients;
    private final ObjectMapper objectMapper;
    private final long stepTimeoutMs;

    public ToolRegistry(List<McpSyncClient> mcpSyncClients, ObjectMapper objectMapper, AgentProperties agentProperties) {
        this.mcpSyncClients = mcpSyncClients;
        this.objectMapper = objectMapper;
        this.stepTimeoutMs = agentProperties.stepTimeoutMs();
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

    /**
     * 按 name 路由到对应 Server 并调用，返回结果的 JSON 字符串。
     * 超时用 agent.step-timeout-ms 兜底，而不是依赖 MCP client 全局的
     * request-timeout（那是针对单次传输往返的，不是「这一步该等多久」）。
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        McpSyncClient client = findClientForTool(toolName);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
        try {
            McpSchema.CallToolResult result = CompletableFuture.supplyAsync(() -> client.callTool(request))
                    .get(stepTimeoutMs, TimeUnit.MILLISECONDS);
            return objectMapper.writeValueAsString(result);
        } catch (TimeoutException e) {
            throw new IllegalStateException("MCP tool call timed out after " + stepTimeoutMs + "ms: " + toolName, e);
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("MCP tool call failed: " + toolName, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize result of tool: " + toolName, e);
        }
    }

    private McpSyncClient findClientForTool(String toolName) {
        return mcpSyncClients.stream()
                .filter(client -> client.listTools().tools().stream().anyMatch(tool -> tool.name().equals(toolName)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No connected MCP server exposes tool: " + toolName));
    }
}
