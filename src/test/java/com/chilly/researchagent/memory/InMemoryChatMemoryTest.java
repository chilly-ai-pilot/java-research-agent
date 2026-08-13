package com.chilly.researchagent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.chilly.researchagent.memory.ChatMessage.Role.ASSISTANT;
import static com.chilly.researchagent.memory.ChatMessage.Role.USER;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryChatMemoryTest {

    private InMemoryChatMemory memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryChatMemory(new ChatMemoryProperties(20, 60));
    }

    /** 连续 add 25 条后，getRecent(20) 应返回最后 20 条且顺序从旧到新。 */
    @Test
    void getRecentReturnsLastTwentyMessagesInOrderAfterTwentyFiveAdds() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 25; i++) {
            ChatMessage.Role role = i % 2 == 0 ? USER : ASSISTANT;
            memory.add(new ChatMessage(role, "msg-" + i, base.plusSeconds(i)));
        }

        List<ChatMessage> recent = memory.getRecent(20);

        assertThat(recent).hasSize(20);
        assertThat(recent.getFirst().content()).isEqualTo("msg-5");
        assertThat(recent.getLast().content()).isEqualTo("msg-24");
        for (int i = 0; i < recent.size(); i++) {
            assertThat(recent.get(i).content()).isEqualTo("msg-" + (i + 5));
            assertThat(recent.get(i).role()).isEqualTo((i + 5) % 2 == 0 ? USER : ASSISTANT);
        }
    }

    /** getRecent 请求条数小于窗口大小时，只返回末尾若干条。 */
    @Test
    void getRecentReturnsSubsetWhenAskedForFewerThanStored() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            memory.add(new ChatMessage(USER, "msg-" + i, base.plusSeconds(i)));
        }

        List<ChatMessage> recent = memory.getRecent(3);

        assertThat(recent).extracting(ChatMessage::content)
                .containsExactly("msg-7", "msg-8", "msg-9");
    }

    /** clear 后应无历史消息。 */
    @Test
    void clearRemovesAllMessages() {
        memory.add(new ChatMessage(USER, "hello", Instant.now()));
        memory.clear();

        assertThat(memory.getRecent(20)).isEmpty();
    }
}
