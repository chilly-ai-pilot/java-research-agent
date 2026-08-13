package com.chilly.researchagent.react;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iteration 3 Step 5：ReAct 端到端场景 A/B（检索 + 闲聊）。
 *
 * <p>集成场景需 Gateway + MCP 同时就绪：
 * {@code GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn -q test -Dtest=ReActScenarioTest}
 */
class ReActScenarioTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("mcp")
    @Tag("integration")
    @EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")
    @EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")
    class EndToEndScenarios {

        @Autowired
        private ReActLoop reActLoop;

        /** 场景 A：知识库检索，应调用 search_knowledge 或 generate_answer 并给出非空答案。 */
        @Test
        void scenarioKnowledgeSearch() {
            ReActResult result = reActLoop.run("帮我查一下知识库里有没有 Transformer 的内容");

            assertThat(ReActScenarioSupport.countToolCalls(result)).isGreaterThanOrEqualTo(1);
            assertThat(ReActScenarioSupport.usedKnowledgeTool(result)).isTrue();
            assertThat(result.finalAnswer()).isNotBlank();
        }

        /** 场景 B：闲聊，应直接 finish 且不调用任何 Tool。 */
        @Test
        void scenarioGreeting() {
            ReActResult result = reActLoop.run("你好");

            assertThat(result.terminatedReason()).isEqualTo(TerminatedReason.LLM_FINISH);
            assertThat(ReActScenarioSupport.countToolCalls(result)).isZero();
            assertThat(result.finalAnswer()).isNotBlank();
        }
    }
}
