package com.chilly.researchagent.react;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void chatWithSystemPromptAndHistoryBuildsFullPrompt() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("mock-reply"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        List<Message> history = List.of(
                new UserMessage("上一轮问题"),
                new AssistantMessage("上一轮回答"));
        gatewayChatService.chat("You are helpful.", history, "新问题");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        List<Message> messages = promptCaptor.getValue().getInstructions();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(3)).getText()).isEqualTo("新问题");
    }
}
