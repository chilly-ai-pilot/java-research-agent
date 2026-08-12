package com.chilly.researchagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iteration 1 Step 2：enabled=true 时的对称反证。Iteration 0 证明了
 * enabled=false → MCP bean 不存在；这里证明 enabled=true → MCP bean
 * 必须出现，且不影响 OpenAiChatModel 装配。command 指向假路径，只测装配，
 * 不测真实连通性。
 */
@SpringBootTest
@ActiveProfiles("mcp-test")
class McpClientBeanTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void mcpSyncClientsBeanExists() {
        assertThat(context.containsBean("mcpSyncClients")).isTrue();
    }

    @Test
    void openAiChatModelBeanStillExists() {
        assertThat(context.getBean(OpenAiChatModel.class)).isNotNull();
    }
}
