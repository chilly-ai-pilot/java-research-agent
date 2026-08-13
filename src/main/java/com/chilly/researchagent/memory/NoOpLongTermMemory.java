package com.chilly.researchagent.memory;

import org.springframework.stereotype.Component;

import java.util.List;

/** 长期记忆空实现：始终不返回任何片段，保证默认行为与未接入时一致。 */
@Component
public class NoOpLongTermMemory implements LongTermMemory {

    @Override
    public List<String> recall(String sessionId, String query, int topK) {
        return List.of();
    }
}
