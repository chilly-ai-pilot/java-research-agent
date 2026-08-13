package com.chilly.researchagent.react;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayExceptionTranslatorTest {

    private final GatewayExceptionTranslator translator = new GatewayExceptionTranslator();

    /** 连接失败应包装为 GatewayUnavailableException 并带上 gatewayUrl。 */
    @Test
    void translateWrapsResourceAccessException() {
        RuntimeException translated = translator.translate(
                new ResourceAccessException("connection refused"), "http://gateway.test");

        assertThat(translated).isInstanceOf(GatewayUnavailableException.class);
        assertThat(translated.getMessage())
                .contains("http://gateway.test")
                .contains("connection refused");
    }

    /** 非 Gateway 故障的 RuntimeException 应原样返回。 */
    @Test
    void translatePreservesNonGatewayRuntimeException() {
        IllegalArgumentException original = new IllegalArgumentException("bad input");

        RuntimeException translated = translator.translate(original, "http://gateway.test");

        assertThat(translated).isSameAs(original);
    }

    /** HTTP 状态码应写入异常 message 与 httpStatus 字段。 */
    @Test
    void translateIncludesHttpStatusWhenPresent() {
        HttpStatusCodeException httpError = mock(HttpStatusCodeException.class);
        when(httpError.getStatusCode()).thenReturn(org.springframework.http.HttpStatus.BAD_GATEWAY);
        when(httpError.getMessage()).thenReturn("502 Bad Gateway");

        RuntimeException translated = translator.translate(httpError, "http://gateway.test");

        assertThat(translated).isInstanceOf(GatewayUnavailableException.class);
        assertThat(translated.getMessage())
                .contains("http://gateway.test")
                .contains("HTTP 502")
                .contains("502 Bad Gateway");
        assertThat(((GatewayUnavailableException) translated).httpStatus()).isEqualTo(502);
    }
}
