package com.chilly.researchagent.tool;

import io.modelcontextprotocol.spec.McpSchema;

public record ToolDescriptor(String name, String description, String serverName, McpSchema.JsonSchema inputSchema) {
}
