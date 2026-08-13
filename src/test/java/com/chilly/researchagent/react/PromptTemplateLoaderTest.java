package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateLoaderTest {

    /** classpath 模板存在时应加载真实内容，而非 fallback。 */
    @Test
    void loadsSystemPromptFromClasspath() {
        assertThat(new ClassPathResource(AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH).exists()).isTrue();

        PromptTemplateLoader loader = new PromptTemplateLoader(defaultAgentProperties());
        String prompt = loader.loadSystemPrompt();

        assertThat(prompt).contains("决策规则");
        assertThat(prompt).isNotEqualTo(PromptTemplateLoader.DEFAULT_SYSTEM_PROMPT);
    }

    /** 模板缺失时应回退到默认 prompt，而不是抛异常。 */
    @Test
    void fallsBackToDefaultWhenTemplateMissing() {
        String prompt = PromptTemplateLoader.loadFromClasspathOrDefault(
                "prompts/does-not-exist.txt",
                PromptTemplateLoader.DEFAULT_SYSTEM_PROMPT);

        assertThat(prompt).isEqualTo(PromptTemplateLoader.DEFAULT_SYSTEM_PROMPT);
    }

    /** 模板存在但内容为空白时应回退到默认 prompt。 */
    @Test
    void fallsBackWhenTemplateIsBlank() {
        String prompt = PromptTemplateLoader.loadFromClasspathOrDefault(
                "prompts/blank.txt",
                PromptTemplateLoader.DEFAULT_SYSTEM_PROMPT);

        assertThat(prompt).isEqualTo(PromptTemplateLoader.DEFAULT_SYSTEM_PROMPT);
    }

    /** 多次调用应返回相同内容。 */
    @Test
    void returnsSameContentAcrossCalls() {
        PromptTemplateLoader loader = new PromptTemplateLoader(defaultAgentProperties());
        String first = loader.loadSystemPrompt();
        String second = loader.loadSystemPrompt();

        assertThat(first).isEqualTo(second);
    }

    private static AgentProperties defaultAgentProperties() {
        return new AgentProperties(10, 30_000L, 90_000L, 2000, AgentProperties.DEFAULT_SYSTEM_PROMPT_PATH);
    }
}
