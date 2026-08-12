package com.chilly.researchagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 三个 MCP Server 的可执行路径/参数/环境变量强类型绑定，镶在 {@code mcp.servers.connections}
 * 下，与 {@code spring.ai.mcp.client.stdio.connections} 使用同一组占位符取值，
 * 供应用内代码（诊断日志、启动校验）引用，而不必解析 Spring AI 的内部属性类。
 */
@ConfigurationProperties(prefix = "mcp.servers")
public record McpServerProperties(Map<String, Connection> connections) {

    public record Connection(String command, List<String> args, Map<String, String> env) {
    }
}
