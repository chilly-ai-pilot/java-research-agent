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
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GatewayChatService 纯单元测试：Mock ChatModel，不启动 Spring、不连 Gateway。
 */
@ExtendWith(MockitoExtension.class)
class GatewayChatServiceTest {

    @Mock
    private OpenAiChatModel chatModel;

    @Mock
    private OpenAiConnectionProperties connectionProperties;

    private final GatewayExceptionTranslator exceptionTranslator = new GatewayExceptionTranslator();

    private GatewayChatService gatewayChatService;

    /** 每个测试前构造带 Mock 依赖的 GatewayChatService。 */
    @BeforeEach
    void setUp() {
        lenient().when(connectionProperties.getBaseUrl()).thenReturn("http://localhost:8080");
        gatewayChatService = new GatewayChatService(chatModel, connectionProperties, exceptionTranslator);
    }

    /** Mock call 返回固定文本，验证 chat 透传结果。 */
    @Test
    void chatReturnsModelReply() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("mock-reply"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        assertThat(gatewayChatService.chat("你好")).isEqualTo("mock-reply");
    }

    /** 验证 system prompt 与 history 按正确顺序写入 Prompt。 */
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
        assertThat(((SystemMessage) messages.get(0)).getText()).isEqualTo("You are helpful.");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(1)).getText()).isEqualTo("上一轮问题");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) messages.get(2)).getText()).isEqualTo("上一轮回答");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(3)).getText()).isEqualTo("新问题");
    }

    /** 空 ChatResponse 应抛 IllegalStateException 而非返回空串。 */
    @Test
    void chatThrowsWhenResponseIsEmpty() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        assertThatThrownBy(() -> gatewayChatService.chat("你好"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty chat response");
    }
}
