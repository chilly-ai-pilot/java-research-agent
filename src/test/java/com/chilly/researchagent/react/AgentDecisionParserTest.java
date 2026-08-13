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
        assertThat(decision.params()).containsEntry("query", "Transformer");
        assertThat(decision.params()).containsEntry("top_k", 3);
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

    /** 畸形 JSON 应抛 DecisionParseException 并附带 raw 预览。 */
    @Test
    void throwsWhenJsonMalformed() {
        assertThatThrownBy(() -> parser.parse("{not json at all"))
                .isInstanceOf(DecisionParseException.class)
                .hasMessageContaining("raw:");
    }
}
