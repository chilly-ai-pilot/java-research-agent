package com.chilly.researchagent.react;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinishAnswerStreamExtractorTest {

    @Test
    void extractsAnswerFromSingleChunk() {
        FinishAnswerStreamExtractor extractor = new FinishAnswerStreamExtractor();

        String part = extractor.consume("{\"action\":\"finish\",\"answer\":\"你好世界\"}");

        assertThat(part).isEqualTo("你好世界");
        assertThat(extractor.consume("}")).isEmpty();
    }

    @Test
    void extractsAnswerAcrossChunks() {
        FinishAnswerStreamExtractor extractor = new FinishAnswerStreamExtractor();

        assertThat(extractor.consume("{\"action\":\"finish\",\"ans")).isEmpty();
        assertThat(extractor.consume("wer\":\"")).isEmpty();
        assertThat(extractor.consume("Hel")).isEqualTo("Hel");
        assertThat(extractor.consume("lo\"}")).isEqualTo("lo");
    }

    @Test
    void ignoresCallToolJson() {
        FinishAnswerStreamExtractor extractor = new FinishAnswerStreamExtractor();

        String json = "{\"action\":\"call_tool\",\"tool\":\"web_search\",\"params\":{\"query\":\"x\"}}";

        assertThat(extractor.consume(json)).isEmpty();
    }

    @Test
    void handlesEscapedCharacters() {
        FinishAnswerStreamExtractor extractor = new FinishAnswerStreamExtractor();

        String part = extractor.consume("""
                {"action":"finish","answer":"第一行\\n第二行"}
                """);

        assertThat(part).isEqualTo("第一行\n第二行");
    }
}
