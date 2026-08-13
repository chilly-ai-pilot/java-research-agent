package com.chilly.researchagent.react;

import com.chilly.researchagent.memory.LongTermMemory;
import com.chilly.researchagent.tool.ToolDescriptor;
import com.chilly.researchagent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组装 ReAct 循环所需的 system prompt 与 step history prompt。
 */
@Component
public class ReActPromptBuilder {

    private static final int LONG_TERM_RECALL_TOP_K = 3;
    private static final String LONG_TERM_MEMORY_SECTION = "相关历史记忆";

    private final ToolRegistry toolRegistry;
    private final PromptTemplateLoader templateLoader;
    private final LongTermMemory longTermMemory;

    /**
     * @param toolRegistry    动态 Tool 列表来源
     * @param templateLoader  静态 system prompt 模板加载器
     * @param longTermMemory  长期记忆检索（默认可为空实现）
     */
    public ReActPromptBuilder(
            ToolRegistry toolRegistry,
            PromptTemplateLoader templateLoader,
            LongTermMemory longTermMemory) {
        this.toolRegistry = toolRegistry;
        this.templateLoader = templateLoader;
        this.longTermMemory = longTermMemory;
    }

    /**
     * 读取 system prompt 模板并动态拼接当前可用 Tool 列表（不含长期记忆）。
     */
    public String buildSystemPrompt() {
        return buildSystemPrompt(null, null);
    }

    /**
     * 读取 system prompt；若长期记忆 recall 非空则追加「相关历史记忆」段落。
     */
    public String buildSystemPrompt(String sessionId, String query) {
        StringBuilder prompt = new StringBuilder(buildBaseSystemPrompt());
        appendLongTermMemories(prompt, sessionId, query);
        return prompt.toString();
    }

    private String buildBaseSystemPrompt() {
        StringBuilder prompt = new StringBuilder(templateLoader.loadSystemPrompt().trim());

        List<ToolDescriptor> tools = toolRegistry.listAllTools();
        if (tools.isEmpty()) {
            prompt.append("\n\n当前没有可用的工具，直接回答用户问题。");
            return prompt.toString();
        }

        prompt.append("\n\n当前已连接的工具（以 MCP 实时列表为准）：\n");
        for (ToolDescriptor tool : tools) {
            prompt.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(formatToolDescription(tool.description()))
                    .append('\n');
        }
        return prompt.toString();
    }

    private void appendLongTermMemories(StringBuilder prompt, String sessionId, String query) {
        if (sessionId == null || sessionId.isBlank() || query == null || query.isBlank()) {
            return;
        }
        List<String> memories = longTermMemory.recall(sessionId, query, LONG_TERM_RECALL_TOP_K);
        if (memories.isEmpty()) {
            return;
        }
        prompt.append("\n\n").append(LONG_TERM_MEMORY_SECTION).append("：\n");
        for (String memory : memories) {
            prompt.append("- ").append(memory).append('\n');
        }
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

    /** Tool 描述为空时返回占位文案。 */
    private static String formatToolDescription(String description) {
        return description != null && !description.isBlank() ? description : "无描述";
    }
}
