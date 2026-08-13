package com.chilly.researchagent.react;

import com.chilly.researchagent.memory.ChatMemory;
import com.chilly.researchagent.memory.ChatMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Iteration 3 Step 5–6：ReAct 端到端场景 A–D。
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

        /** 场景 C：知识库无内容时上网搜索，应出现 web_search 调用。 */
        @Test
        void scenarioWebSearch() {
            ReActResult result = reActLoop.run("知识库里关于 XYZabc123 没有内容，帮我上网搜一下");

            assertThat(ReActScenarioSupport.usedTool(result, "web_search")).isTrue();
            assertThat(result.finalAnswer()).isNotBlank();
        }

        /** 场景 D：创建复习卡片，应调用 createFlashcard 且 params 含标题与内容。 */
        @Test
        void scenarioCreateFlashcard() {
            ReActResult result = reActLoop.run("把'Transformer 是自注意力机制'做成复习卡片，标题叫 Transformer 基础");

            List<ReActStep> flashcardSteps = ReActScenarioSupport.toolCallSteps(result).stream()
                    .filter(step -> "createFlashcard".equals(step.tool()))
                    .toList();
            assertThat(flashcardSteps).isNotEmpty();

            ReActStep flashcardStep = flashcardSteps.getFirst();
            assertThat(ReActScenarioSupport.paramsContain(flashcardStep, "Transformer")).isTrue();
            assertThat(ReActScenarioSupport.paramsContain(flashcardStep, "基础")
                    || ReActScenarioSupport.paramsContain(flashcardStep, "title")).isTrue();
            assertThat(result.finalAnswer()).isNotBlank();
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("mcp")
    @Tag("integration")
    @EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")
    @EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")
    class MultiTurnScenarios {

        @Autowired
        private ReActLoop reActLoop;

        @Autowired
        private ChatMemory chatMemory;

        /** 先 web_search「Transformer的概念」，再问「刚才问的概念是什么」应能回忆。 */
        @Test
        void remembersConceptFromPreviousWebSearchTurn() {
            String sessionId = "mem-transformer-concept";
            chatMemory.clear(sessionId);

            ReActResult turn1 = reActLoop.run(sessionId, "帮我上网搜一下 Transformer的概念");
            assertThat(ReActScenarioSupport.usedTool(turn1, "web_search")).isTrue();
            assertThat(turn1.finalAnswer()).isNotBlank();

            ReActResult turn2 = reActLoop.run(sessionId, "刚才问的概念是什么");
            assertThat(turn2.finalAnswer()).isNotBlank();
            assertThat(turn2.finalAnswer()).containsIgnoringCase("Transformer");

            List<ChatMessage> history = chatMemory.getRecent(sessionId, 10);
            assertThat(history).hasSize(4);
            assertThat(history.get(0).content()).contains("Transformer的概念");
            assertThat(history.get(2).content()).isEqualTo("刚才问的概念是什么");
        }
    }
}
