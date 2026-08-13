package com.chilly.researchagent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDecisionParserTest {

    private AgentDecisionParser parser;

    @BeforeEach
    void setUp() {
        parser = new AgentDecisionParser(new ObjectMapper());
    }

    /** finish 决策应解析出 answer。 */
    @Test
    void parsesFinishAction() {
        AgentDecision decision = parser.parse("""
                {"action":"finish","answer":"你好，我是研究助手"}
                """);

        assertThat(decision.isFinish()).isTrue();
        assertThat(decision.answer()).isEqualTo("你好，我是研究助手");
        assertThat(decision.tool()).isNull();
    }

    /** call_tool 决策应解析出 tool 与 params。 */
    @Test
    void parsesCallToolAction() {
        AgentDecision decision = parser.parse("""
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"Transformer","top_k":3}}
                """);

        assertThat(decision.isCallTool()).isTrue();
        assertThat(decision.tool()).isEqualTo("search_knowledge");
        assertThat(decision.paramAsString("query")).contains("Transformer");
        assertThat(decision.paramAsInt("top_k")).contains(3);
    }

    /** call_tool 缺少 params 时应默认为空 Map。 */
    @Test
    void parsesCallToolWithMissingParamsAsEmptyMap() {
        AgentDecision decision = parser.parse("""
                {"action":"call_tool","tool":"search_knowledge"}
                """);

        assertThat(decision.isCallTool()).isTrue();
        assertThat(decision.params()).isEmpty();
    }

    /** markdown json 代码块包裹的内容应能解析。 */
    @Test
    void parsesJsonWrappedInMarkdownCodeBlock() {
        AgentDecision decision = parser.parse("""
                好的，我来调用工具：
                ```json
                {"action":"finish","answer":"done"}
                ```
                """);

        assertThat(decision.isFinish()).isTrue();
        assertThat(decision.answer()).isEqualTo("done");
    }

    /** 缺少 action 字段应抛 DecisionParseException。 */
    @Test
    void throwsWhenActionMissing() {
        assertThatThrownBy(() -> parser.parse("{\"tool\":\"search_knowledge\"}"))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("action");
    }

    /** 非法 action 值应抛 DecisionParseException。 */
    @Test
    void throwsWhenActionInvalid() {
        assertThatThrownBy(() -> parser.parse("{\"action\":\"invoke_magic\"}"))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("Unknown action");
    }

    /** finish 缺少 answer 应抛 DecisionParseException。 */
    @Test
    void throwsWhenFinishMissingAnswer() {
        assertThatThrownBy(() -> parser.parse("{\"action\":\"finish\"}"))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("answer");
    }

    /** call_tool 缺少 tool 应抛 DecisionParseException。 */
    @Test
    void throwsWhenCallToolMissingToolName() {
        assertThatThrownBy(() -> parser.parse("{\"action\":\"call_tool\",\"params\":{}}"))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("tool");
    }

    /** 畸形 JSON 应抛 DecisionParseException，message 不含原文但保留预览字段。 */
    @Test
    void throwsWhenJsonMalformed() {
        assertThatThrownBy(() -> parser.parse("{not json at all"))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("raw length=")
                .hasMessageContaining("preview={not json at all");
    }

    /** 多个 JSON 对象时应提取第一个完整对象。 */
    @Test
    void extractsFirstJsonObjectWhenMultiplePresent() {
        AgentDecision decision = parser.parse("""
                {"action":"finish","answer":"first"}
                以及另一个对象 {"action":"finish","answer":"second"}
                """);

        assertThat(decision.answer()).isEqualTo("first");
    }

    /** params 含嵌套对象或字符串中的括号时应正确解析。 */
    @Test
    void parsesCallToolWithNestedParams() {
        AgentDecision decision = parser.parse("""
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"{a:b}","nested":{"k":"v"}}}
                """);

        assertThat(decision.isCallTool()).isTrue();
        assertThat(decision.paramAsString("query")).contains("{a:b}");
        assertThat(decision.params()).containsKey("nested");
    }

    /** params 中含 JSON 转义双引号时应正确解析。 */
    @Test
    void parsesJsonWithEscapedQuotesInParams() {
        AgentDecision decision = parser.parse("""
                {"action":"call_tool","tool":"search_knowledge","params":{"query":"什么是\\"MCP\\"？"}}
                """);

        assertThat(decision.paramAsString("query")).contains("什么是\"MCP\"？");
    }

    /** finish 的 answer 含未转义换行（LLM 常见输出）应能解析。 */
    @Test
    void parsesFinishWithUnescapedNewlinesInAnswer() {
        AgentDecision decision = parser.parse("""
                {"action":"finish","answer":"根据搜索结果，我为你整理了关于**注意力机制**：

                ## 什么是注意力机制

                注意力机制是一种让模型动态关注重要部分的技术。"}
                """);

        assertThat(decision.isFinish()).isTrue();
        assertThat(decision.answer()).contains("注意力机制");
        assertThat(decision.answer()).contains("## 什么是注意力机制");
    }

    /** finish 的 answer 含 markdown 与多段落时应完整保留。 */
    @Test
    void parsesFinishWithMarkdownMultilineAnswer() {
        String raw = """
                {"action":"finish","answer":"## 标题\\n\\n- 要点一\\n- 要点二"}
                """;
        AgentDecision decision = parser.parse(raw.replace("\\n", "\n"));

        assertThat(decision.answer()).contains("要点一");
        assertThat(decision.answer()).contains("要点二");
    }

    /** 超长原始文本在 getRawTextPreview() 中应截断到 500 字符。 */
    @Test
    void truncatesRawTextTo500CharsInException() {
        String longRaw = "x".repeat(1000);

        assertThatThrownBy(() -> parser.parse(longRaw))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("raw length=1000")
                .satisfies(ex -> {
                    String preview = ((DecisionParseException) ex).getRawTextPreview();
                    assertThat(preview).hasSize(DecisionConstants.RAW_TEXT_PREVIEW_LIMIT + 3);
                    assertThat(preview).isEqualTo("x".repeat(DecisionConstants.RAW_TEXT_PREVIEW_LIMIT) + "...");
                });
    }
}
