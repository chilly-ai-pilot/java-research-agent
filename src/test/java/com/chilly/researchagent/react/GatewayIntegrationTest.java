package com.chilly.researchagent.react;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Go LLM Gateway 集成测试。Gateway 未启动时跳过（{@code GATEWAY_INTEGRATION=true} 才跑）。
 */
@SpringBootTest
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")
class GatewayIntegrationTest {

    @Autowired
    private GatewayChatService gatewayChatService;

    @Test
    void nonStreamingChat() {
        String reply = gatewayChatService.chat("你好，请用一句话介绍你自己");

        assertThat(reply).isNotBlank();
        assertThat(reply.length()).isGreaterThan(10);
        assertThat(reply.toLowerCase()).doesNotContain("error");
        assertThat(reply).doesNotContain("Exception");
    }
}
