版本沿用：`Boot 3.5.16` + `Spring AI 1.1.8` + `JDK 21`，Agent 端口 **8081**（Design.md 写 8080 是笔误，以 Iteration 0 为准）。

Iteration 5 拆 **6 步**，目标：**REST 接口 + 审计日志 + 技术选型备忘录**——把 Agent 变成可被企业 Java 系统调用的服务。

**前置依赖**：Iteration 3（`ReActLoop`）+ Iteration 4（`sessionId` + Memory）均已完成。

---

## Step 1 — 请求/响应 DTO

**做什么**
- 新建 `web/dto/ChatRequest.java`：
  ```java
  record ChatRequest(
      String message,
      @Nullable String sessionId,   // 空则服务端生成 UUID
      boolean stream                // 默认 false
  ) {}
  ```
- 新建 `web/dto/ChatResponse.java`：
  ```java
  record ChatResponse(
      String sessionId,
      String answer,
      String terminatedReason,
      List<StepAuditDto> steps
  ) {}
  ```
- 新建 `web/dto/StepAuditDto.java`（与 `ReActStep` 字段对齐，不含完整 observation——见 Step 4）
- 新建 `ChatDtoValidationTest`：message 为空 → 400 场景由 Controller 测；DTO 序列化/反序列化 JSON 测

**为什么单独一步**
REST 层的契约（字段名、是否 nullable）一旦发布就不该反复改。先锁 DTO + JSON 样例，前端/调用方可以并行对接。

**验证**
```bash
mvn -q test -Dtest=ChatDtoValidationTest
```

**JSON 样例**
```json
// POST /api/agent/chat
{ "message": "帮我查一下 Transformer", "sessionId": null, "stream": false }

// 200 响应
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "answer": "...",
  "terminatedReason": "LLM_FINISH",
  "steps": [
    { "stepIndex": 1, "tool": "search_knowledge", "params": {"query": "Transformer"} }
  ]
}
```

---

## Step 2 — 非流式 REST 接口

**做什么**
- 新建 `web/AgentController.java`：
  - `POST /api/agent/chat`
  - 注入 `ReActLoop`、`SessionChatMemory`（或 facade `AgentService`）
  - sessionId 为空 → `UUID.randomUUID().toString()`
  - 调 `reactLoop.run(sessionId, message)` → 映射为 `ChatResponse`
- 新建 `web/AgentService.java`（可选 facade）：Controller 只负责 HTTP，Service 负责 session + ReAct
- 新建 `AgentControllerTest`（`@WebMvcTest` + Mock `AgentService`）：
  - POST 合法 body → 200 + JSON 结构
  - message 缺失 → 400

**为什么单独一步**
`@WebMvcTest` 不启 MCP 子进程、不调 Gateway——毫秒级验证路由、状态码、JSON 字段。真实 ReAct 留给 Step 6。

**验证**
```bash
mvn -q test -Dtest=AgentControllerTest
```

---

## Step 3 — SSE 流式接口

**做什么**
- 同一 `POST /api/agent/chat`，当 `stream=true`：
  - 返回 `Content-Type: text/event-stream`
  - 事件类型：
    - `event: step` → 每步 ReAct 完成时推送 `{tool, params}` 摘要
    - `event: token` → 最终 answer 的 chunk（复用 Iteration 2 的 `GatewayChatService.chatStream`，或先推送 step 再流式 answer）
    - `event: done` → `{sessionId, terminatedReason}`
- 新建 `AgentControllerStreamTest`（MockMvc + 异步）：
  - `stream=true` → Content-Type 含 `text/event-stream`
  - 响应体含至少一个 `event:` 行

**为什么单独一步**
SSE 和 JSON 同步响应的 Spring MVC 配置不同（`SseEmitter` / `Flux`）。分开后流式 bug 不会把非流式接口一起搞挂。

**验证**
```bash
mvn -q test -Dtest=AgentControllerStreamTest
```

手动（Gateway + MCP 就绪）：
```bash
curl -N -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"你好","stream":true}'
```

---

## Step 4 — 审计日志

**做什么**
- 新建 `audit/AuditRecord.java`（`record`: requestId, sessionId, userMessage, steps, finalAnswer, terminatedReason, startedAt, durationMs）
- 新建 `audit/AuditLogService.java`：
  - `log(AuditRecord record)` → SLF4J INFO，结构化 JSON 一行（方便 grep / 后续接 ELK）
  - observation **截断**到 `agent.max-observation-chars`，params 原样
- 在 `AgentService` 每次请求结束调用 `auditLogService.log(...)`
- 新建 `AuditLogServiceTest`：
  - 给定含 2 step 的 `ReActResult`，断言日志 JSON 含 tool 名、**不含**超长 observation 全文

**为什么单独一步**
Design.md 验收：「追溯某次请求调用了哪些工具」。审计是独立横切关注点——先保证日志正确，再和 REST 联调。

**验证**
```bash
mvn -q test -Dtest=AuditLogServiceTest
```

启动后 grep 示例：
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mcp &
curl -s -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"帮我查一下 Transformer"}' | jq .
# 日志中应出现一条 audit JSON，steps 含 search_knowledge
```

---

## Step 5 — 技术选型备忘录

**做什么**
- 新建 `docs/tech-choice-java-agent.md`（**本步唯一文档产出**），结构：
  1. 为什么编排层选 Java（Spring 生态、企业集成、类型安全、与 flashcard-mcp 同栈）
  2. 为什么 LLM 不调 Provider 直连而走 Go Gateway（统一鉴权、限流、可观测）
  3. 为什么 Tool 走 MCP 而不是 Java 内嵌（语言异构、独立部署、RAG Python / Search Go 各取所长）
  4. 为什么手写 ReAct 而不用 Spring AI ToolCallingAgent（停止条件、JSON 决策可审计）
  5. Memory 为什么应用层注入而不是框架黑盒
- 每条 3–5 句，附「如果选另一方案会怎样」一句反证

**为什么单独一步**
这是面试/汇报用的**独立交付物**，不混在代码 commit 里。写完对照 Iteration 0–5 实际实现，确保没有吹嘘未实现的能力。

**验证**
人工：通读一遍，确认 5 个问题都有回答，且端口/版本号与项目一致。

---

## Step 6 — 端到端验收

**做什么**
- 新建 `AgentE2ETest`（`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Tag("integration")`）：
  - 非流式：POST chat → 200，answer 非空，steps 非 null
  - 流式：`stream=true` → 收到 `done` 事件
  - 同 sessionId 两轮：第二轮 history 生效（可 Spy Memory）
- 跑完整验收清单

**验证**

| # | 验收项 | 命令 / 期望 |
|---|---|---|
| 1 | 非流式 REST | `curl -X POST http://localhost:8081/api/agent/chat -H 'Content-Type: application/json' -d '{"message":"帮我查一下 Transformer"}'` → JSON 含 answer |
| 2 | 流式 REST | `curl -N ... "stream":true` → 逐 chunk / SSE event |
| 3 | 审计可追溯 | 日志含 requestId + steps 工具名 + params |
| 4 | session 延续 | 同一 sessionId 两轮对话，Turn 2 理解指代 |
| 5 | 单元/Web 测试 | `mvn -q test` 默认全绿 |
| 6 | 技术备忘录 | `docs/tech-choice-java-agent.md` 存在且五项齐全 |
| 7 | 健康检查仍可用 | `curl -s localhost:8081/actuator/health` → UP |

```bash
# 本地完整验收
source scripts/use-java21.sh
# 启动 Gateway + 三个 MCP Server
mvn spring-boot:run -Dspring-boot.run.profiles=mcp

GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn -q test -Dtest=AgentE2ETest
```

---

## 产出物

| 类 / 文件 | 包 / 路径 | 职责 |
|---|---|---|
| `ChatRequest` / `ChatResponse` / `StepAuditDto` | `web.dto` | REST 契约 |
| `AgentController` | `web` | HTTP 入口 |
| `AgentService` | `web` | 编排 ReAct + Memory |
| `AuditRecord` / `AuditLogService` | `audit` | 请求级审计 |
| `tech-choice-java-agent.md` | `docs` | 技术选型说明 |
| `AgentE2ETest` | `test` | 端到端 |

---

## 安全与运维（本迭代最小集）

- **不做** 鉴权（企业内网假设）；若需要，在 `AgentController` 前加 Spring Security filter——单独开 Iteration 6，不挤在本轮
- **建议** 生产关闭 `McpConnectivityRunner` / `ReActScenarioRunner`（仅 `dev` profile），避免每次启动打 MCP smoke

建议**每步一个 git commit**。Iteration 5 完成后，整个 `java-research-agent` v1 闭环交付。

---

## 测试案例
已完成。提交了两笔：

1. **`b998aba`** — fix: ReAct 决策 JSON 解析 + 多轮测试稳定性
2. **`419a448`** — feat: Iteration 5 REST API、SSE、审计日志与 E2E

---

## Iteration 5 交付物

| 组件 | 说明 |
|---|---|
| `POST /api/agent/chat` | 非流式 JSON / 流式 SSE（`stream=true`） |
| `AgentService` | sessionId 生成、ReAct 编排、审计 |
| `AuditLogService` | 单行 `[Audit]` JSON 日志，observation 截断 |
| `docs/tech-choice-java-agent.md` | 技术选型备忘录（5 项） |

SSE 事件：`step` → `token` → `done`

---

## 怎么测试

### 1. 单元 / Web 测试（无需 Gateway）

```bash
source scripts/use-java21.sh
mvn test
```

或分步：

```bash
mvn test -Dtest=ChatDtoValidationTest
mvn test -Dtest=AgentControllerTest,AgentControllerStreamTest
mvn test -Dtest=AuditLogServiceTest
```

### 2. 启动服务（需 Gateway + 三个 MCP）

```bash
source scripts/use-java21.sh
mvn spring-boot:run -Dspring-boot.run.profiles=mcp
```

健康检查：

```bash
curl -s http://localhost:8081/actuator/health
```

### 3. 非流式 REST

```bash
curl -s -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"帮我上网搜一下注意力机制","stream":false}' | jq .
```

期望：JSON 含 `sessionId`、`answer`、`terminatedReason`、`steps`。

### 4. 流式 SSE

```bash
curl -N -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"你好","stream":true}'
```

期望：依次出现 `event:step`（若有工具调用）、`event:token`、`event:done`。

### 5. 多轮 session

```bash
# Turn 1 — 记下返回的 sessionId
curl -s -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"帮我上网搜一下注意力机制","stream":false}' | jq -r .sessionId

# Turn 2 — 带上同一 sessionId
curl -s -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"刚才提到的再详细讲一下","sessionId":"<上一步的sessionId>","stream":false}' | jq .
```

### 6. 审计日志

启动后 grep 日志：

```bash
# 日志里应有 [Audit] 开头的 JSON，含 requestId、steps[].tool、params
grep '\[Audit\]' 
```

### 7. 集成 E2E（需 Gateway + MCP）

```bash
GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn test -Dtest=AgentE2ETest
```

多轮 Memory 场景：

```bash
GATEWAY_INTEGRATION=true MCP_INTEGRATION=true mvn test -Dtest=MultiTurnScenarioTest
```

### 8. 技术备忘录

```bash
cat docs/tech-choice-java-agent.md
```

---

测试步骤：
1. 启动： mvn spring-boot:run -Dspring-boot.run.profiles=mcp
2. 启动网页（localhost://8501）：./scripts/run-chat-ui.sh