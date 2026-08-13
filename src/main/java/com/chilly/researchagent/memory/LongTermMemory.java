package com.chilly.researchagent.memory;

import java.util.List;

/**
 * 长期记忆检索接口；本迭代仅预留，默认由 {@link NoOpLongTermMemory} 空实现。
 */
public interface LongTermMemory {

    List<String> recall(String sessionId, String query, int topK);
}
