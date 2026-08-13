版本沿用：`Boot 3.5.16` + `Spring AI 1.1.8` + `JDK 21`。

Iteration 3 拆 **7 步**，目标：**手写 ReAct 循环**——LLM 通过 JSON 决策调用 MCP Tool 或结束，代码强制执行 `max-steps` 停止条件。

**前置依赖**：Iteration 1（`ToolRegistry.callTool`）+ Iteration 2（`GatewayChatService`）均已完成。

---

## Step 1 — 系统提示词 + 决策 JSON 模型

**做什么**
- 新建 `src/main/resources/prompts/system-react.txt`：写入 Design.md 中的系统提示词（工具表 + 5 条决策规则）
- 新建 `react/AgentDecision.java`：
  ```java
  // action: "call_tool" | "finish"
  record AgentDecision(String action, String tool, Map<String,Object> params, String answer) {}
  ```
- 新建 `react/AgentDecisionParser.java`：
  - 输入：LLM 原始字符串
  - 输出：`AgentDecision`
  - 支持 LLM 输出被 \`\`\`json 包裹的情况
  - 解析失败抛 `DecisionParseException`，携带原始文本前 500 字符

**为什么单独一步**
ReAct 最难 debug 的是「LLM 没按 JSON 说话」。单独测 Parser，用**纯字符串单元测试**覆盖：`finish`、合法 `call_tool`、markdown 包裹、缺字段、非法 action——全部不连 Gateway、不连 MCP。

**验证**
```bash
mvn -q test -Dtest=AgentDecisionParserTest
# 至少 6 个 case，全绿
```

---

## Step 2 — Prompt 组装器

**做什么**
- 新建 `react/ReActPromptBuilder.java`：
  - `buildSystemPrompt()` → 读取 `system-react.txt` + 动态拼接当前可用 Tool 列表（从 `ToolRegistry.listAllTools()` 取 name + description）
  - `buildStepPrompt(List<ReActStep> steps, String userQuestion)` → 把历史 step（thought / action / observation）格式化为 LLM 可读的文本块
- 新建 `react/ReActStep.java`（`record`: stepIndex, action, tool, params, observation, timestamp）
- 单元测试：给定 2 个 mock step，断言 prompt 中包含 observation 文本且顺序正确

**为什么单独一步**
Iteration 4 Memory 改的是「messages 列表」，Iteration 3 改的是「ReAct step 列表」——先把 step 格式化逻辑钉死，后面加 Memory 只需决定 step 和 chat history 怎么合并，不会重写 Prompt 组装。

**验证**
```bash
mvn -q test -Dtest=ReActPromptBuilderTest
```

---

## Step 3 — 单步执行器（不含循环）

**做什么**
- 新建 `react/ReActStepExecutor.java`：
  - 依赖 `GatewayChatService`、`ToolRegistry`、`AgentDecisionParser`、`ReActPromptBuilder`、`AgentProperties`
  - `executeOneStep(ReActContext ctx)`：
    1. 调 LLM 拿到原始文本
    2. 解析为 `AgentDecision`
    3. 若 `finish` → 返回 `StepResult.finished(answer)`
    4. 若 `call_tool` → 调 `toolRegistry.callTool(...)`，observation 截断到 `agent.max-observation-chars`，返回 `StepResult.continueWith(observation)`
  - 单步耗时超过 `agent.step-timeout-ms` 则抛 `StepTimeoutException`

**为什么单独一步**
循环 = 单步 × N。单步不通时开循环只会得到「第几次死掉的」这一层信息，仍然不知道 LLM 解析、Tool 调用、超时哪一段有问题。

**验证**
- Mock 测试：`GatewayChatService` 返回固定 JSON `{"action":"finish","answer":"ok"}` → 断言 `StepResult.finished`
- Mock 测试：返回 `call_tool` JSON + Mock `ToolRegistry` → 断言 observation 写入且被截断

```bash
mvn -q test -Dtest=ReActStepExecutorTest
```

---

## Step 4 — ReAct 主循环 + 强制停止

**做什么**
- 新建 `react/ReActLoop.java`：
  - `run(String userQuestion)` → `ReActResult`（`record`: finalAnswer, steps, terminatedReason）
  - `terminatedReason` 枚举：`LLM_FINISH` | `MAX_STEPS` | `TOTAL_TIMEOUT` | `ERROR`
  - 循环：`for (step = 1; step <= agent.maxSteps(); step++)` 调用 `ReActStepExecutor`
  - 总耗时超过 `agent.total-timeout-ms` → 强制停止
  - **禁止**同一 `(tool, params)` 连续重复调用（简单 dedup，对应 Design 规则 2）
- 新建 `react/ReActLoopTest`（Mock 全程）：
  - LLM 第 1 步 `call_tool`，第 2 步 `finish` → `terminatedReason=LLM_FINISH`，steps.size()==2
  - LLM 永远 `call_tool` → 第 10 步后 `terminatedReason=MAX_STEPS`

**为什么单独一步**
Design.md 明确要求：**停止条件必须在代码里写死**。这一步只验证控制流，LLM 行为全靠 Mock——不花 token、不依赖 MCP。

**验证**
```bash
mvn -q test -Dtest=ReActLoopTest
```

---

## Step 5 — 场景 A/B：检索 + 闲聊（真实 LLM + MCP）

**做什么**
- 新建 `react/ReActScenarioRunner.java`（`@Profile("react")` + `ApplicationRunner`）或集成测试：
  - **场景 A**：输入 `"帮我查一下知识库里有没有 Transformer 的内容"`
    - 期望：至少 1 步 `call_tool`，tool 为 `search_knowledge` 或 `generate_answer`
    - 最终 answer 非空
  - **场景 B**：输入 `"你好"`
    - 期望：`terminatedReason=LLM_FINISH`，**零** `call_tool` step

**为什么单独一步**
这是 Design.md 验收的前两个场景。先跑通「需要工具」和「不需要工具」两类，再测 web_search / flashcard，避免四个场景同时失败时无法定位。

**验证**（Gateway + MCP profile 同时就绪）
```bash
export GATEWAY_INTEGRATION=true
export MCP_INTEGRATION=true
mvn -q test -Dtest=ReActScenarioTest#scenarioKnowledgeSearch
mvn -q test -Dtest=ReActScenarioTest#scenarioGreeting
```

---

## Step 6 — 场景 C/D：web_search + create_flashcard

**做什么**
- **场景 C**：输入 `"知识库里关于 XYZabc123 没有内容，帮我上网搜一下"`
  - 期望：出现 `web_search` 调用（允许先 `search_knowledge` 再 `web_search`，但同一轮最多一个 tool——由 LLM 分步完成）
- **场景 D**：输入 `"把'Transformer 是自注意力机制'做成复习卡片，标题叫 Transformer 基础"`
  - 期望：出现 `create_flashcard` 调用，params 含 title / content

**验证**
```bash
export GATEWAY_INTEGRATION=true
export MCP_INTEGRATION=true
mvn -q test -Dtest=ReActScenarioTest#scenarioWebSearch
mvn -q test -Dtest=ReActScenarioTest#scenarioCreateFlashcard
```

---

## Step 7 — max-steps 强制停止 + 完整验收

**做什么**
- **场景 E**（Mock 或 `@MockBean ToolRegistry` 返回固定 observation）：LLM 被 stub 成永远 `call_tool`
  - 期望：`terminatedReason=MAX_STEPS`，finalAnswer 含「任务未完成」或等价文案
- 审计：`ReActResult.steps` 完整保留每步 tool/params/observation，供 Iteration 5 审计模块直接消费
- 跑完整验收表

**验证**

| # | 场景 | 输入 | 期望 |
|---|---|---|---|
| 1 | 知识库检索 | 查 Transformer | 调 `search_knowledge` 或 `generate_answer`，有结果 |
| 2 | 上网搜索 | 知识库没有 XXX | 调 `web_search` |
| 3 | 创建卡片 | 做成复习卡片 | 调 `create_flashcard` |
| 4 | 闲聊 | 你好 | 不调工具，直接回答 |
| 5 | 死循环保护 | Mock 永远 call_tool | 10 步后强制停止 |
| 6 | 单元测试 | 无外部依赖 | `AgentDecisionParserTest` + `ReActLoopTest` 全绿 |

```bash
# 默认 CI
mvn -q test

# 完整链路（本地）
GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn -q test -Dtest=ReActScenarioTest
```

---

## 产出物

| 类 | 包 | 职责 |
|---|---|---|
| `system-react.txt` | `resources/prompts` | 系统提示词 |
| `AgentDecision` / `AgentDecisionParser` | `react` | LLM 决策 JSON 模型 |
| `ReActPromptBuilder` / `ReActStep` | `react` | Prompt 组装 |
| `ReActStepExecutor` | `react` | 单步 LLM + Tool |
| `ReActLoop` | `react` | 主循环 + 停止条件 |
| `ReActResult` | `react` | 循环输出（含 steps 审计链） |
| `ReActScenarioTest` | `test` | 可选端到端场景 |

---

## 刻意不做的事（留给后续迭代）

- **不做** Spring AI 内置 `ToolCallingAgent`——Design.md 选手写循环是为了停止条件可控
- **不加** Memory（Iteration 4）
- **不加** REST 接口（Iteration 5）
- **不做** 流式 ReAct（Iteration 5 SSE 时再考虑逐步流式输出 final answer）

建议**每步一个 git commit**。

---

## 流程总结
用户问题 → ReActPromptBuilder 组装 prompt → GatewayChatService 调用 LLM
→ AgentDecisionParser 解析 LLM 输出 → AgentDecision
→ 如果 call_tool：执行工具 → 记录 ReActStep → 回到 PromptBuilder
→ 如果 finish：返回 answer

ReActLoop.run()
├── 创建 ReActContext
├── for 循环（最多 maxSteps 次）
│     ├── 检查 total-timeout
│     ├── 调 ReActStepExecutor.executeOneStep(context)
│     │     ├── 内部调 GatewayChatService（LLM）
│     │     ├── 内部调 AgentDecisionParser（解析）
│     │     ├── 内部调 ToolRegistry（工具执行）
│     │     └── 返回 StepResult.Finished 或 Continue
│     ├── 根据 StepResult 类型决定：结束或追加 ReActStep 继续循环
│     └── catch 异常 → ERROR 终止
└── 循环结束未完成 → MAX_STEPS

ReActLoop
├── 依赖 ReActStepExecutor（执行单步）
└── 依赖 AgentProperties（maxSteps、totalTimeoutMs）

ReActStepExecutor
├── 依赖 GatewayChatService（调 LLM）
├── 依赖 ToolRegistry（调 MCP Tool）
├── 依赖 AgentDecisionParser（解析 LLM 输出）
├── 依赖 ReActPromptBuilder（组装 prompt）
└── 依赖 AgentProperties（stepTimeoutMs、maxObservationChars）

---

测试案例：

1. 普通问候
export GATEWAY_INTEGRATION=true
export MCP_INTEGRATION=true
cd /Users/chilly/go/src/github.com/chilly-ai-pilot/java-research-agent
mvn test -Dtest='ReActScenarioTest$EndToEndScenarios#scenarioGreeting' 2>&1 | tee /tmp/greeting.log
grep '\[ReAct\]' /tmp/greeting.log

2. 知识库检索
   export GATEWAY_INTEGRATION=true
   export MCP_INTEGRATION=true
   cd /Users/chilly/go/src/github.com/chilly-ai-pilot/java-research-agent
   mvn test -Dtest='ReActScenarioTest$EndToEndScenarios#scenarioKnowledgeSearch' 2>&1 | tee /tmp/scenario5.log
   grep '\[ReAct\]' /tmp/scenario5.log

3. 知识库搜索+上网搜索
   export GATEWAY_INTEGRATION=true
   export MCP_INTEGRATION=true
   mvn test -Dtest='ReActScenarioTest$EndToEndScenarios#scenarioWebSearch' 2>&1 | tee /tmp/scenario6.log
   grep '\[ReAct\]' /tmp/scenario6.log

4. 上网搜索+创建卡片
   export GATEWAY_INTEGRATION=true
   export MCP_INTEGRATION=true
   mvn test -Dtest='ReActScenarioTest$EndToEndScenarios#scenarioCreateFlashcard' 2>&1 | tee /tmp/scenario6d.log
   grep '\[ReAct\] 调用过的 Tool' /tmp/scenario6d.log
   grep '\[ReAct\] Tool 返回 \[createFlashcard\]' /tmp/scenario6d.log

---