package com.chilly.researchagent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.chilly.researchagent.memory.ChatMessage.Role.ASSISTANT;
import static com.chilly.researchagent.memory.ChatMessage.Role.USER;
import static org.assertj.core.api.Assertions.assertThat;

class SessionChatMemoryTest {

    private SessionChatMemory memory;

    @BeforeEach
    void setUp() {
        memory = new SessionChatMemory(new ChatMemoryProperties(20, 60));
    }

    /** session A 与 session B 各自写入 3 条，互不可见。 */
    @Test
    void sessionsAreIsolatedFromEachOther() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 3; i++) {
            memory.add("session-a", new ChatMessage(USER, "a-" + i, base.plusSeconds(i)));
            memory.add("session-b", new ChatMessage(USER, "b-" + i, base.plusSeconds(i)));
        }

        assertThat(memory.getRecent("session-a", 10)).extracting(ChatMessage::content)
                .containsExactly("a-0", "a-1", "a-2");
        assertThat(memory.getRecent("session-b", 10)).extracting(ChatMessage::content)
                .containsExactly("b-0", "b-1", "b-2");
    }

    /** 同一 session 仍遵守滑动窗口。 */
    @Test
    void slidingWindowStillWorksWithinSession() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        memory = new SessionChatMemory(new ChatMemoryProperties(5, 60));
        for (int i = 0; i < 8; i++) {
            ChatMessage.Role role = i % 2 == 0 ? USER : ASSISTANT;
            memory.add("session-a", new ChatMessage(role, "msg-" + i, base.plusSeconds(i)));
        }

        List<ChatMessage> recent = memory.getRecent("session-a", 5);

        assertThat(recent).extracting(ChatMessage::content)
                .containsExactly("msg-3", "msg-4", "msg-5", "msg-6", "msg-7");
    }

    /** 缺省或空白 sessionId 应映射到 default。 */
    @Test
    void blankSessionIdUsesDefaultSession() {
        memory.add(null, new ChatMessage(USER, "hello", Instant.now()));
        memory.add("  ", new ChatMessage(ASSISTANT, "hi", Instant.now()));

        assertThat(memory.getRecent(SessionChatMemory.DEFAULT_SESSION_ID, 10)).hasSize(2);
        assertThat(memory.getRecent(null, 10)).hasSize(2);
    }

    /** clear 只清空指定 session。 */
    @Test
    void clearOnlyRemovesTargetSession() {
        memory.add("session-a", new ChatMessage(USER, "a", Instant.now()));
        memory.add("session-b", new ChatMessage(USER, "b", Instant.now()));

        memory.clear("session-a");

        assertThat(memory.getRecent("session-a", 10)).isEmpty();
        assertThat(memory.getRecent("session-b", 10)).hasSize(1);
    }
}
