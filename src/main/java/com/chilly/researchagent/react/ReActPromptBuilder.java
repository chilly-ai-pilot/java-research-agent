package com.chilly.researchagent.react;

import com.chilly.researchagent.tool.ToolDescriptor;
import com.chilly.researchagent.tool.ToolRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 组装 ReAct 循环所需的 system prompt 与 step history prompt。
 */
@Component
public class ReActPromptBuilder {

    private static final String SYSTEM_PROMPT_PATH = "prompts/system-react.txt";

    private final ToolRegistry toolRegistry;
    private final String baseSystemPrompt;

    public ReActPromptBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.baseSystemPrompt = loadBaseSystemPrompt();
    }

    /**
     * 读取 system-react.txt 并动态拼接当前可用 Tool 列表。
     */
    public String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder(baseSystemPrompt.trim());
        prompt.append("\n\n当前已连接的工具（以 MCP 实时列表为准）：\n");
        for (ToolDescriptor tool : toolRegistry.listAllTools()) {
            prompt.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description() != null ? tool.description() : "")
                    .append('\n');
        }
        return prompt.toString();
    }

    /**
     * 将历史 step 与用户问题格式化为 LLM 可读的单轮 user 消息。
     */
    public String buildStepPrompt(List<ReActStep> steps, String userQuestion) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题：").append(userQuestion).append("\n\n");

        if (steps != null && !steps.isEmpty()) {
            prompt.append("已执行的步骤：\n");
            for (ReActStep step : steps) {
                prompt.append("--- Step ").append(step.stepIndex()).append(" ---\n");
                prompt.append("action: ").append(step.action()).append('\n');
                if (step.tool() != null) {
                    prompt.append("tool: ").append(step.tool()).append('\n');
                }
                if (step.params() != null && !step.params().isEmpty()) {
                    prompt.append("params: ").append(step.params()).append('\n');
                }
                if (step.observation() != null) {
                    prompt.append("observation: ").append(step.observation()).append('\n');
                }
                prompt.append('\n');
            }
        }

        prompt.append("请根据以上信息，输出下一步 JSON 决策。");
        return prompt.toString();
    }

    /**
     * 从 classpath 加载静态 system prompt 模板。
     */
    private String loadBaseSystemPrompt() {
        try (InputStream inputStream = new ClassPathResource(SYSTEM_PROMPT_PATH).getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + SYSTEM_PROMPT_PATH, e);
        }
    }
}
