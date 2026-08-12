package com.chilly.researchagent.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * mcp profile 装配辅助。本身不声明任何 MCP client bean——那些由
 * spring-ai-starter-mcp-client 的自动配置根据 spring.ai.mcp.client.*
 * 属性生成，这里只在 profile 生效时打一条日志，方便确认 profile 是否被
 * 正确激活。
 */
@Configuration
@Profile("mcp")
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    @PostConstruct
    void logProfileActive() {
        log.debug("mcp profile is active — MCP client beans will be assembled by spring-ai-starter-mcp-client");
    }
}
