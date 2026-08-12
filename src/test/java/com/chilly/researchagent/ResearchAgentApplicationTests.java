package com.chilly.researchagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iteration 0 冒烟测试：验证 OpenAI starter 装配成功但零外部依赖。
 *
 * <p>断言 OpenAiChatModel bean 存在，证明 Gateway 连接配置装配无误；
 * 断言上下文中没有 MCP client bean（mcpSyncClients/mcpAsyncClients），
 * 证明 {@code spring.ai.mcp.client.enabled: false} 确实生效。
 * 两者叠加起来，Iteration 2 一旦调 Gateway 失败，就能排除是 Spring
 * 装配问题——问题只会在 Gateway 侧或网络。
 */
@SpringBootTest
class ResearchAgentApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    void openAiChatModelBeanExists() {
        assertThat(context.getBean(OpenAiChatModel.class)).isNotNull();
    }

    @Test
    void mcpClientBeansAreAbsent() {
        assertThat(context.containsBean("mcpSyncClients")).isFalse();
        assertThat(context.containsBean("mcpAsyncClients")).isFalse();
        assertThat(context.containsBean("mcpSyncToolCallbacks")).isFalse();
        assertThat(context.containsBean("mcpAsyncToolCallbacks")).isFalse();
    }
}
