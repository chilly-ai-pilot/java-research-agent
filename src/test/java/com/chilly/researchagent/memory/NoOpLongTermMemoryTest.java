package com.chilly.researchagent.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpLongTermMemoryTest {

    private final NoOpLongTermMemory longTermMemory = new NoOpLongTermMemory();

    /** NoOp 实现应始终返回空列表。 */
    @Test
    void recallAlwaysReturnsEmpty() {
        assertThat(longTermMemory.recall("session-1", "Transformer", 5)).isEmpty();
    }
}
