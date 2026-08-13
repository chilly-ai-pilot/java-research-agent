package com.chilly.researchagent.react;

import com.chilly.researchagent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ReAct 循环结构化追踪日志，统一前缀 {@value #MARKER} 便于 grep。
 */
@Component
public class ReActTraceLogger {

    /** 日志检索标记，测试 Step 7 时在终端执行：{@code grep '[ReAct]' target/surefire-reports/*.txt} */
    public static final String MARKER = "[ReAct]";

    private static final Logger log = LoggerFactory.getLogger(ReActTraceLogger.class);

    private final String model;
    private final int maxSteps;

    /**
     * @param chatProperties Spring AI 配置的 model 名称
     * @param agentProperties 提供 max-steps 供日志展示
     */
    @Autowired
    public ReActTraceLogger(OpenAiChatProperties chatProperties, AgentProperties agentProperties) {
        this(resolveModel(chatProperties), agentProperties.maxSteps());
    }

    /** 单元测试专用，不经过 Spring 容器。 */
    private ReActTraceLogger(String model, int maxSteps) {
        this.model = model != null && !model.isBlank() ? model : "unknown";
        this.maxSteps = maxSteps;
    }

    /** 构造 Mock 测试用的静默追踪器（仍会写日志，model 显示为 mock）。 */
    public static ReActTraceLogger forTests() {
        return new ReActTraceLogger("mock", 10);
    }

    /**
     * 循环开始：打印用户问题与模型。
     */
    public void logRunStart(String userQuestion) {
        log.info("{} ========== 开始 ==========", MARKER);
        log.info("{} 问题: {}", MARKER, userQuestion);
        log.info("{} 模型: {}", MARKER, model);
        log.info("{} 最大步数: {}", MARKER, maxSteps);
    }

    /**
     * 单步开始：打印第几步与发给 LLM 的 step 上下文（每行带 {@link #MARKER} 便于 grep）。
     */
    public void logStepStart(int stepIndex, String stepPrompt) {
        log.info("{} ---------- 第 {} 步 / 最多 {} 步 ----------", MARKER, stepIndex, maxSteps);
        for (String line : stepPrompt.split("\n", -1)) {
            log.info("{} 上下文 | {}", MARKER, line);
        }
    }

    /**
     * LLM 原始输出与解析后的决策。
     */
    public void logLlmDecision(int stepIndex, String rawDecision, AgentDecision decision) {
        log.info("{} 第 {} 步 LLM 原始输出: {}", MARKER, stepIndex, rawDecision);
        if (decision.isFinish()) {
            log.info("{} 第 {} 步 决策: finish", MARKER, stepIndex);
            logMultiline("第 " + stepIndex + " 步 answer", decision.answer());
        } else {
            log.info("{} 第 {} 步 决策: call_tool → tool={}, params={}",
                    MARKER, stepIndex, decision.tool(), decision.params());
        }
    }

    /**
     * Tool 调用返回的 observation（即下一步 context 的来源）；多行内容逐行打印。
     */
    public void logToolObservation(int stepIndex, String tool, String observation) {
        int length = observation != null ? observation.length() : 0;
        log.info("{} 第 {} 步 Tool 返回 [{}] （{} 字符）", MARKER, stepIndex, tool, length);
        logMultiline("第 " + stepIndex + " 步 observation", observation);
    }

    /**
     * 循环结束：终止原因、步数、调用的 Tool 摘要、最终答案。
     */
    public void logRunEnd(ReActResult result, long durationMs) {
        log.info("{} 终止原因: {}", MARKER, result.terminatedReason());
        log.info("{} 执行步数: {}", MARKER, result.steps().size());
        logToolsSummary(result);
        logMultiline("最终答案", result.finalAnswer());
        log.info("{} 总耗时: {}ms", MARKER, durationMs);
        log.info("{} ========== 结束 ==========", MARKER);
    }

    /** 汇总本轮回合实际调用过的 Tool，便于 grep {@code 调用过的 Tool} 快速确认。 */
    private void logToolsSummary(ReActResult result) {
        List<String> tools = result.steps().stream()
                .filter(step -> AgentDecision.ACTION_CALL_TOOL.equals(step.action()))
                .map(ReActStep::tool)
                .toList();
        if (tools.isEmpty()) {
            log.info("{} 调用过的 Tool: （无，直接 finish）", MARKER);
            return;
        }
        log.info("{} 调用过的 Tool: {}", MARKER, tools);
        for (ReActStep step : result.steps()) {
            if (!AgentDecision.ACTION_CALL_TOOL.equals(step.action())) {
                continue;
            }
            log.info("{} 审计 Step {} | tool={} | params={} | observation前200字={}",
                    MARKER,
                    step.stepIndex(),
                    step.tool(),
                    step.params(),
                    previewObservation(step.observation()));
        }
    }

    private static String previewObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return "";
        }
        int limit = 200;
        return observation.length() > limit ? observation.substring(0, limit) + "..." : observation;
    }

    /** 多行文本逐行输出，每行带 {@link #MARKER}，避免 grep 漏掉换行后的内容。 */
    private void logMultiline(String label, String text) {
        if (text == null || text.isBlank()) {
            log.info("{} {} | （空）", MARKER, label);
            return;
        }
        for (String line : text.split("\n", -1)) {
            log.info("{} {} | {}", MARKER, label, line);
        }
    }

    /**
     * 异常终止时的简要说明。
     */
    public void logRunError(int stepIndex, String message) {
        log.warn("{} 第 {} 步 异常终止: {}", MARKER, stepIndex, message);
    }

    private static String resolveModel(OpenAiChatProperties chatProperties) {
        if (chatProperties == null || chatProperties.getOptions() == null) {
            return "unknown";
        }
        return chatProperties.getOptions().getModel();
    }
}
