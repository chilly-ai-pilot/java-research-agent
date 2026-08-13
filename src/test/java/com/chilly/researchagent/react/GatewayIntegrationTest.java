package com.chilly.researchagent.react;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Go LLM Gateway 集成测试。Gateway 未启动时跳过（{@code GATEWAY_INTEGRATION=true} 才跑）。
 */
@SpringBootTest
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")
class GatewayIntegrationTest {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(120);

    @Autowired
    private GatewayChatService gatewayChatService;

    /** 验证非流式调用能拿到非空、无异常字样的回复。 */
    @Test
    void nonStreamingChat() {
        String reply = gatewayChatService.chat("你好，请用一句话介绍你自己");

        assertThat(reply).isNotBlank();
        assertThat(reply.length()).isGreaterThan(10);
        assertThat(reply.toLowerCase()).doesNotContain("error");
        assertThat(reply).doesNotContain("Exception");
    }

    /** 验证流式调用能逐 chunk 返回且拼接后非空。 */
    @Test
    void streamingChat() {
        List<String> chunks = gatewayChatService.chatStream("数到 5")
                .collectList()
                .block(STREAM_TIMEOUT);

        assertThat(chunks).isNotNull().isNotEmpty();

        String fullText = String.join("", chunks);
        assertThat(fullText).isNotBlank();
        assertThat(fullText.length()).isGreaterThan(3);
        assertThat(fullText.toLowerCase()).doesNotContain("error");
        assertThat(fullText).doesNotContain("Exception");
    }
}
