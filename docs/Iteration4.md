版本沿用：`Boot 3.5.16` + `Spring AI 1.1.8` + `JDK 21`。

Iteration 4 拆 **5 步**，目标：**多轮对话 Memory**——滑动窗口保留最近 N 轮，让 Agent 理解「刚才提到的」这类指代。

**前置依赖**：Iteration 3（`ReActLoop` + `ReActResult`）已完成。

---

## 核心设计（本迭代不变的理论）

Memory 不做魔法：每次 LLM 调用前，把**最近 K 轮** user/assistant 消息拼进 `GatewayChatService.chat(...)` 的 `history` 参数。长期向量检索本迭代**只留接口**，不实现。

---

## Step 1 — ChatMemory 接口 + 内存实现

**做什么**
- 新建 `memory/ChatMessage.java`（`record`: role [`USER`|`ASSISTANT`|`SYSTEM`], content, timestamp）
- 新建 `memory/ChatMemory.java` 接口：
  - `void add(ChatMessage message)`
  - `List<ChatMessage> getRecent(int maxMessages)`
  - `void clear()`
- 新建 `memory/InMemoryChatMemory.java`：`ConcurrentHashMap<String, Deque<ChatMessage>>` 或单 session 版 `ArrayDeque`（本步先**单 session**，Step 2 再加 sessionId）
- 新建 `memory/ChatMemoryProperties`（`@ConfigurationProperties("agent.memory")`）：
  ```yaml
  agent:
    memory:
      max-messages: 20        # 滑动窗口：最多保留 20 条 message（约 10 轮）
      session-ttl-minutes: 60   # 可选，本迭代可不实现 TTL
  ```
- 单元测试：连续 add 25 条 USER/ASSISTANT，`getRecent(20)` 返回最后 20 条且顺序正确

**为什么单独一步**
Memory 的 bug 通常是 off-by-one 或顺序反了。纯内存单测秒级反馈，不需要 Gateway/MCP。

**验证**
```bash
mvn -q test -Dtest=InMemoryChatMemoryTest
```

---

## Step 2 — Session 隔离

**做什么**
- `ChatMemory` 方法签名改为带 `sessionId`：
  - `void add(String sessionId, ChatMessage message)`
  - `List<ChatMessage> getRecent(String sessionId, int maxMessages)`
- 新建 `memory/SessionChatMemory.java`：包装 `InMemoryChatMemory`，`sessionId` 缺省时用 `"default"`
- 单元测试：
  - session A 和 session B 各写 3 条，互不可见
  - 同一 session 滑动窗口仍正确

**为什么单独一步**
Iteration 5 的 REST 接口每个请求带 `sessionId`（或自动生成 UUID）。现在不加 session，后面 Controller 改完 Memory 全得返工。

**验证**
```bash
mvn -q test -Dtest=SessionChatMemoryTest
```

---

## Step 3 — 接入 ReActLoop

**做什么**
- `ReActLoop.run(String sessionId, String userQuestion)`（保留无 session 重载调 `default`）
- 循环开始前：`memory.getRecent(sessionId, maxMessages)` → 转为 Spring AI `Message` 列表 → 传入 `ReActStepExecutor` / `GatewayChatService`
- 循环结束后：
  - `memory.add(sessionId, USER, userQuestion)`
  - `memory.add(sessionId, ASSISTANT, finalAnswer)`
- **ReAct 中间 step 不写入 Memory**（只有最终 answer 入库）——避免 observation 把窗口撑爆；中间过程已在 `ReActResult.steps` 里

**为什么单独一步**
「Memory 存什么」是设计决策：存最终 QA 对 vs 存每步 observation，行为差异巨大。本迭代选**只存 QA 对**，和 Design.md 的「多轮对话」场景一致；若存 observation，Step 5 的「刚才提到的注意力机制」测例可能过但窗口很快爆。

**验证**
- Mock 测试：`ReActLoop` 跑完一轮后，memory 里恰好 2 条（USER + ASSISTANT）
- Mock 测试：第二轮 `getRecent` 包含第一轮 QA

```bash
mvn -q test -Dtest=ReActLoopMemoryTest
```

---

## Step 4 — 长期记忆接口（空实现）

**做什么**
- 新建 `memory/LongTermMemory.java` 接口：
  - `List<String> recall(String sessionId, String query, int topK)`
- 新建 `memory/NoOpLongTermMemory.java`：永远返回 `List.of()`
- 在 `ReActPromptBuilder.buildSystemPrompt` 预留 hook：若 `longTermMemory.recall` 非空，追加「相关历史记忆」段落
- 单元测试：NoOp 实现 recall 返回空，prompt 不含「相关历史记忆」

**为什么单独一步**
Design.md 要求「预留长期记忆接口」。空实现 + 测试锁死「默认不影响行为」，后续接向量库时只换实现类。

**验证**
```bash
mvn -q test -Dtest=NoOpLongTermMemoryTest
mvn -q test -Dtest=ReActPromptBuilderTest   # 回归：原有 case 仍绿
```

---

## Step 5 — 多轮场景验收

**做什么**
- 新建 `memory/MultiTurnScenarioTest`（`@Tag("integration")`，需 `GATEWAY_INTEGRATION` + `MCP_INTEGRATION`）：
  - **Turn 1**：sessionId=`mem-test-1`，`"帮我查一下 Transformer"`
    - 断言：调用了 `search_knowledge` 或 `generate_answer`
  - **Turn 2**：同一 sessionId，`"刚才提到的注意力机制再详细讲一下"`
    - 断言：**不强制**必须再调 tool（LLM 可能基于上一轮 answer 直接解释）
    - 断言：finalAnswer 包含「注意力」或「attention」关键词
    - 断言：Mock/Spy 验证 `GatewayChatService` 收到的 history 含 Turn 1 的 USER/ASSISTANT
- 对照组：Turn 2 换**新 sessionId**，同一问题——history 为空，行为应与 Turn 1 类似（重新检索）

**验证**

| # | 验收项 | 期望 |
|---|---|---|
| 1 | 单 session 多轮 | Turn 2 history 含 Turn 1 |
| 2 | 指代理解 | Turn 2 answer 提及注意力机制 |
| 3 | session 隔离 | 新 session 无 Turn 1 history |
| 4 | 滑动窗口 | add 25 条后只保留 20 条 |
| 5 | 单元测试 | 无外部依赖测试全绿 |
| 6 | 长期记忆默认无影响 | NoOp recall 为空 |

```bash
mvn -q test
GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn -q test -Dtest=MultiTurnScenarioTest
```

---

## 产出物

| 类 | 包 | 职责 |
|---|---|---|
| `ChatMessage` / `ChatMemory` | `memory` | 消息模型 + 接口 |
| `InMemoryChatMemory` / `SessionChatMemory` | `memory` | 内存 + session 隔离 |
| `ChatMemoryProperties` | `memory` | `agent.memory.*` 配置 |
| `LongTermMemory` / `NoOpLongTermMemory` | `memory` | 长期记忆预留 |
| `MultiTurnScenarioTest` | `test` | 多轮端到端 |

---

## application.yml 增量

```yaml
agent:
  memory:
    max-messages: 20
```

建议**每步一个 git commit**。

---

测试案例：

1. 多轮提问中，提到“上次说的概念”，检查消息历史是否生效
export GATEWAY_INTEGRATION=true
export MCP_INTEGRATION=true
mvn -q test -Dtest='ReActScenarioTest$MultiTurnScenarios#remembersConceptFromPreviousWebSearchTurn'