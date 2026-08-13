package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 加载 ReAct 静态 prompt 模板。
 */
@Component
public class PromptTemplateLoader {

    public static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个 AI 研究助手，负责帮助用户检索知识、搜索信息、生成内容。
            每次回复必须是 JSON 格式。
            """;

    private final AgentProperties agentProperties;
    private volatile String systemPrompt;

    public PromptTemplateLoader(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 首次调用时从 classpath 加载模板并缓存，避免拖慢应用启动。
     */
    public String loadSystemPrompt() {
        String cached = systemPrompt;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (systemPrompt == null) {
                systemPrompt = loadFromClasspathOrDefault(
                        agentProperties.systemPromptPath(), DEFAULT_SYSTEM_PROMPT);
            }
            return systemPrompt;
        }
    }

    public static String loadFromClasspathOrDefault(String path, String fallback) {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            String content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            return content.isBlank() ? fallback : content;
        } catch (IOException e) {
            return fallback;
        }
    }
}
