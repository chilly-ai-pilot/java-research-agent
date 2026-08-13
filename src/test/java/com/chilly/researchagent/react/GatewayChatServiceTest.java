package com.chilly.researchagent.react;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GatewayChatService 纯单元测试：Mock ChatModel，不启动 Spring、不连 Gateway。
 */
@ExtendWith(MockitoExtension.class)
class GatewayChatServiceTest {

    @Mock
    private OpenAiChatModel chatModel;

    private GatewayChatService gatewayChatService;

    @BeforeEach
    void setUp() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.ai.openai.base-url", "http://localhost:8080"))
                .thenReturn("http://localhost:8080");
        gatewayChatService = new GatewayChatService(chatModel, environment);
    }

    @Test
    void chatReturnsModelReply() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("mock-reply"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        assertThat(gatewayChatService.chat("你好")).isEqualTo("mock-reply");
    }
}
