版本沿用 Iteration 0：`Boot 3.5.16` + `Spring AI 1.1.8` + `JDK 21`。

Iteration 2 拆 **5 步**，目标：**Agent 能通过 Go Gateway 完成单轮对话**（非流式 + 流式），不涉及 Tool Calling、不涉及 MCP。

可与 Iteration 1 **并行**开发——本迭代不需要 MCP profile。

---

## 前置条件

| 依赖 | 地址 | 说明 |
|---|---|---|
| Go LLM Gateway | `http://localhost:8080`（或 `${GATEWAY_URL}`） | 已配置至少一个 Provider（如 DeepSeek） |
| 环境变量 | `GATEWAY_URL`、`AGENT_MODEL`（可选） | 与 `application.yml` 占位符一致 |

Gateway 未启动时，Step 2 及之后的验证**必然失败**——这是预期行为，用来确认失败信息足够清晰。

---

## Step 1 — GatewayChatService 封装

**做什么**
- 新建 `react/GatewayChatService.java`（Iteration 3 的 ReAct 也会复用）：
  - 构造注入 `OpenAiChatModel`
  - `chat(String userMessage)` → `String`：单轮、非流式，system prompt 可选（本步可传 null 或固定 `"You are a helpful assistant."`）
  - `chatStream(String userMessage)` → `Flux<String>`（或 `Stream<String>`，与 Spring AI 1.1.8 API 对齐）：流式逐 token/chunk
- 新建 `GatewayChatServiceTest`（**纯单元测试，Mock ChatModel**）：
  - Mock `OpenAiChatModel.call(...)` 返回固定文本 `"mock-reply"`
  - 断言 `chat("你好")` 返回 `"mock-reply"`
  - 不启动 Spring 上下文、不连 Gateway

**为什么单独一步**
Iteration 0 已证明 `OpenAiChatModel` bean 存在。这一步验证**你的封装逻辑**正确，且测试不依赖外部网络——CI 永远能跑。

**验证**
```bash
mvn -q test -Dtest=GatewayChatServiceTest
```

---

## Step 2 — 非流式：真实 Gateway 单轮对话

**做什么**
- 新建 `config/GatewayConfig.java`（如需要）：确认 `OpenAiChatModel` 的 `base-url`、`model`、`temperature` 与 yml 一致；**一般 Iteration 0 的 auto-config 已足够**，此类可能只有 `@Configuration` + 日志
- 新建 `GatewayIntegrationTest`：
  - `@Tag("integration")` + `@EnabledIfEnvironmentVariable(named = "GATEWAY_INTEGRATION", matches = "true")`
  - `@SpringBootTest`（**不要**激活 `mcp` profile）
  - 调用 `gatewayChatService.chat("你好，请用一句话介绍你自己")`
  - 断言：返回非空、长度 > 10、不含 `"error"` / `"Exception"`

**为什么单独一步**
非流式是最短路径：`call()` → HTTP POST → 等完整 body。流式涉及 SSE/chunk 解析，分开后非流式失败可以直接 tcpdump/curl Gateway，不必同时怀疑 Flux 管道。

**验证**（Gateway 已启动）
```bash
export GATEWAY_INTEGRATION=true
mvn -q test -Dtest=GatewayIntegrationTest#nonStreamingChat
```

手动冒烟：
```bash
source scripts/use-java21.sh
mvn spring-boot:run
# 另开终端，用临时 CommandLineRunner 或 Step 3 的 REST 尚未就绪——此处用测试输出为准
```

Gateway 日志应出现路由到正确 Provider 的记录（取决于 Gateway 实现）。

---

## Step 3 — 流式：chunk 逐段返回

**做什么**
- 在 `GatewayChatService.chatStream` 中确保使用 Spring AI 的 streaming API（`StreamingChatModel` / `stream()`，以 1.1.8 实际 API 为准）
- `GatewayIntegrationTest` 追加测试：
  - 收集 `chatStream("数到 5")` 的全部 chunk
  - 断言：chunk 数量 ≥ 2（证明不是一次性假流式）
  - 断言：拼接后的完整字符串非空

**为什么单独一步**
很多 Gateway 的非流式和流式走不同 code path（`/v1/chat/completions` vs `stream=true`）。Iteration 5 的 SSE 接口会直接复用这条链路——现在不验证，后面 REST 层 bug 会和 Agent 逻辑 bug 纠缠。

**验证**
```bash
export GATEWAY_INTEGRATION=true
mvn -q test -Dtest=GatewayIntegrationTest#streamingChat
```

---

## Step 4 — Gateway 不可用时的错误处理

**做什么**
- 在 `GatewayChatService` 捕获 `RestClientException` / Spring AI 包装的运行时异常
- 抛出项目内受检异常或 `GatewayUnavailableException`（`RuntimeException` 子类），消息含：`gatewayUrl`、HTTP status（如有）、原始 cause 摘要
- 新建 `GatewayChatServiceErrorTest`（`@SpringBootTest` + `@TestPropertySource` 把 `spring.ai.openai.base-url` 指到 `http://localhost:59999`）：
  - 断言 `chat("你好")` 抛出 `GatewayUnavailableException`
  - 断言异常 message 包含 `localhost:59999`

**为什么单独一步**
Iteration 3 的 ReAct 循环遇到 Gateway 挂掉时，需要**确定性失败**而不是 LLM 返回空字符串被当成 `finish`。这一步把「网络/Gateway 问题」和「LLM 决策 JSON 解析问题」在异常类型上分开。

**验证**
```bash
mvn -q test -Dtest=GatewayChatServiceErrorTest
# 不依赖 Gateway 运行
```

---

## Step 5 — 完整验收 + 日志对照

**做什么**
- 更新 `StartupBanner`：Gateway 相关字段已在 Iteration 0 打印，本步确认 `GATEWAY_INTEGRATION` 测试通过后，横幅中的 `gateway` / `model` / `temp` 与 Gateway 实际收到的请求一致（可人工对照 Gateway access log）
- 编写验收清单（下表）并跑一遍

**验证**

| # | 验收项 | 命令 / 期望 |
|---|---|---|
| 1 | 单元测试（无外部依赖） | `mvn -q test -Dtest=GatewayChatServiceTest,GatewayChatServiceErrorTest` 全绿 |
| 2 | 非流式集成 | `GATEWAY_INTEGRATION=true mvn -q test -Dtest=GatewayIntegrationTest#nonStreamingChat` |
| 3 | 流式集成 | `GATEWAY_INTEGRATION=true mvn -q test -Dtest=GatewayIntegrationTest#streamingChat` |
| 4 | MCP 仍默认关闭 | `mvn -q test -Dtest=ResearchAgentApplicationTests#mcpClientBeansAreAbsent` |
| 5 | OpenAiChatModel 仍在 | `mvn -q test -Dtest=ResearchAgentApplicationTests#openAiChatModelBeanExists` |
| 6 | 错误端口语义清晰 | Step 4 测试 PASS |

---

## 产出物

| 类 | 包 | 职责 |
|---|---|---|
| `GatewayChatService` | `react` | 单轮非流式 / 流式 LLM 调用（Iteration 3 复用） |
| `GatewayConfig` | `config` | 可选，Gateway 相关 Bean 声明 / 日志 |
| `GatewayUnavailableException` | `react` | Gateway 不可达时的明确异常 |
| `GatewayChatServiceTest` | `test` | Mock 单元测试 |
| `GatewayIntegrationTest` | `test` | 可选真实 Gateway 集成测试 |
| `GatewayChatServiceErrorTest` | `test` | 错误路径测试 |

---

## 与 Iteration 3 的接口约定

Iteration 2 结束时，`GatewayChatService` 应暴露：

```java
String chat(String systemPrompt, List<Message> history, String userMessage);
Flux<String> chatStream(String systemPrompt, List<Message> history, String userMessage);
```

若 Step 1 只做了单参数版，在 Step 5 前扩展为带 `history` 的三参数版——Iteration 4 Memory 会直接依赖这个签名，Iteration 3 ReAct 每轮追加 observation 也依赖它。

建议**每步一个 git commit**。

## Go Gateway API
```bash
curl -N -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama3.2:latest",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": false
  }'
```