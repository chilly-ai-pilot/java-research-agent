package com.chilly.researchagent.react;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Gateway 错误路径单元测试：Mock 连接失败，不依赖端口不可达等外部事实。
 */
@ExtendWith(MockitoExtension.class)
class GatewayChatServiceErrorTest {

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private OpenAiConnectionProperties connectionProperties;

    private final GatewayExceptionTranslator exceptionTranslator = new GatewayExceptionTranslator();

    private GatewayChatService gatewayChatService;

    /** 每个测试前构造带 Mock 依赖的 GatewayChatService。 */
    @BeforeEach
    void setUp() {
        when(connectionProperties.getBaseUrl()).thenReturn("http://gateway.test");
        gatewayChatService = new GatewayChatService(chatModel, connectionProperties, exceptionTranslator);
    }

    /** ResourceAccessException 应被翻译为 GatewayUnavailableException。 */
    @Test
    void chatThrowsGatewayUnavailableExceptionWhenConnectionFails() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ResourceAccessException("I/O error on POST request for gateway"));

        GatewayUnavailableException ex = catchThrowableOfType(
                () -> gatewayChatService.chat("你好"), GatewayUnavailableException.class);

        assertThat(ex.getMessage()).contains("gateway.test");
        assertThat(ex.gatewayUrl()).isEqualTo("http://gateway.test");
        assertThat(ex.httpStatus()).isNull();
    }
}
