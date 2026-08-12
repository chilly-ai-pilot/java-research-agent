package com.chilly.researchagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 打印 Environment 中实际生效的配置值，而不是 application.yml 的文件内容。
 * 环境变量覆盖、profile 覆盖导致运行值与预期不符，靠这一行日志在启动阶段
 * 就能发现，不必等到 Iteration 3 调试 ReAct 循环时才意外撞上。
 */
@Component
public class StartupBanner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupBanner.class);

    private final Environment environment;
    private final AgentProperties agentProperties;

    public StartupBanner(Environment environment, AgentProperties agentProperties) {
        this.environment = environment;
        this.agentProperties = agentProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String gatewayUrl = environment.getProperty("spring.ai.openai.base-url");
        String model = environment.getProperty("spring.ai.openai.chat.options.model");
        String temperature = environment.getProperty("spring.ai.openai.chat.options.temperature");
        boolean mcpClientEnabled = environment.getProperty(
                "spring.ai.mcp.client.enabled", Boolean.class, false);

        log.info(
                "Agent config → gateway={}, model={}, temp={}, maxSteps={}, "
                        + "stepTimeout={}ms, totalTimeout={}ms, maxObservationChars={}, mcpClient={}",
                gatewayUrl,
                model,
                temperature,
                agentProperties.maxSteps(),
                agentProperties.stepTimeoutMs(),
                agentProperties.totalTimeoutMs(),
                agentProperties.maxObservationChars(),
                mcpClientEnabled ? "ENABLED" : "DISABLED");
    }
}
