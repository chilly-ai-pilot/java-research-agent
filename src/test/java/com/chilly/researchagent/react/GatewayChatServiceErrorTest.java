package com.chilly.researchagent.react;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gateway 不可达时的错误路径测试，不依赖真实 Gateway 运行。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.openai.base-url=http://localhost:59999",
        "spring.ai.retry.max-attempts=1"
})
class GatewayChatServiceErrorTest {

    @Autowired
    private GatewayChatService gatewayChatService;

    @Test
    void chatThrowsGatewayUnavailableExceptionWhenGatewayDown() {
        assertThatThrownBy(() -> gatewayChatService.chat("你好"))
                .isInstanceOf(GatewayUnavailableException.class)
                .hasMessageContaining("localhost:59999");
    }
}
