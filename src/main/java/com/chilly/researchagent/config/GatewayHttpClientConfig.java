package com.chilly.researchagent.config;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.http.client.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorBuilderCustomizer;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.reactive.JdkClientHttpConnectorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Go Gateway 的上游 Provider 不接受 JDK HttpClient 默认发出的 h2c Upgrade 头，
 * 会导致 502。此处强制 RestClient / WebClient 使用 HTTP/1.1。
 */
@Configuration
public class GatewayHttpClientConfig {

    /**
     * 定制非流式调用使用的 RestClient，强制 HTTP/1.1。
     */
    @Bean
    ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> gatewayRestClientHttp11Customizer() {
        return builder -> builder.withHttpClientCustomizer(GatewayHttpClientConfig::forceHttp11);
    }

    /**
     * 定制流式调用使用的 WebClient，强制 HTTP/1.1。
     */
    @Bean
    ClientHttpConnectorBuilderCustomizer<JdkClientHttpConnectorBuilder> gatewayWebClientHttp11Customizer() {
        return builder -> builder.withHttpClientCustomizer(GatewayHttpClientConfig::forceHttp11);
    }

    /**
     * 将 JDK HttpClient 版本锁定为 HTTP/1.1，避免 h2c 升级头。
     */
    private static void forceHttp11(HttpClient.Builder httpClientBuilder) {
        httpClientBuilder.version(HttpClient.Version.HTTP_1_1);
    }
}
