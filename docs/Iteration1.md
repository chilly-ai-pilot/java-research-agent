版本沿用 Iteration 0：`Boot 3.5.16` + `Spring AI 1.1.8` + `JDK 21`，端口 `8081`。

Iteration 1 拆 **6 步**，目标只有一个：**证明 Agent 能连上三个 MCP Server、列出 Tool、手动调通各一条**。不涉及 LLM 决策。

---

## 前置条件（整轮 Iteration 1 共用）

三个 MCP Server 必须在本机可启动，且 stdout **只输出 JSON-RPC**（Java flashcard jar 若打 banner 到 stdout 会污染协议，需 `banner-mode: off` + 日志走 stderr）。

| Server | 典型启动方式 | 依赖 |
|---|---|---|
| python-rag-mcp | `uv run python -m rag_mcp`（或你仓库里的等价命令） | Python venv、向量库/知识库数据 |
| go-search-mcp | 编译好的二进制或 `go run .` | 搜索 API Key（如有） |
| java-flashcard-mcp | `mvn -q -f .../pom.xml spring-boot:run` | MySQL |

建议三个 Server 的路径/命令**不要写死在 yml**，用环境变量或 `application-mcp.yml`（profile）注入，和 Gateway 的 `${GATEWAY_URL:...}` 同一风格。

---

## Step 1 — MCP 连接配置结构（默认仍关闭）

**做什么**
- 新建 `src/main/resources/application-mcp.yml`（**不**在默认 profile 激活），内容包含：
  - `spring.ai.mcp.client.enabled: true`
  - `spring.ai.mcp.client.type: SYNC`
  - `spring.ai.mcp.client.request-timeout: 30s`
  - 三个 `stdio.connections`：`rag-mcp`、`search-mcp`、`flashcard-mcp`（command/args/env 用占位符 `${RAG_MCP_COMMAND:...}` 等）
- 新建 `McpServerProperties`（`@ConfigurationProperties("mcp.servers")`），把三个 Server 的可执行路径、参数、环境变量绑成强类型，供 yml 引用
- **默认** `application.yml` 保持 `spring.ai.mcp.client.enabled: false` 不变

**为什么单独一步**
Iteration 0 已经证明「MCP 关闭时上下文干净」。这一步只验证**配置能绑定、编译通过**，不 fork 子进程。配置写错（缩进、属性名）在这一步就能发现，不会和「子进程起不来」混在一起。

**验证**
```bash
source scripts/use-java21.sh
mvn -q clean compile
mvn -q test   # 现有 3 个 Iteration 0 测试仍全绿，mcpClientBeansAreAbsent 仍成立
```

---

## Step 2 — 打开 MCP profile，验证 Bean 装配

**做什么**
- 新建 `config/McpClientConfig.java`：加 `@Profile("mcp")`，打印一条 DEBUG 日志确认 profile 生效
- 更新 `StartupBanner`：当 `spring.ai.mcp.client.enabled=true` 时，额外打印 `mcpConnections=rag-mcp,search-mcp,flashcard-mcp`
- 新建 `src/test/resources/application-mcp-test.yml`：复制 Step 1 的连接配置，但 command 指向**假路径** `/nonexistent/mcp-server`（仅测装配，不测连通）
- 新建 `McpClientBeanTests`（`@ActiveProfiles("mcp-test")`）：
  - 断言 `mcpSyncClients` bean **存在**
  - 断言 `OpenAiChatModel` bean **仍存在**（MCP 和 LLM 装配互不干扰）

**为什么单独一步**
Iteration 0 的反证是「enabled=false → 四个 MCP bean 不存在」。这一步做对称反证：**enabled=true → bean 必须出现**。若 Spring AI 1.1.8 改了 bean 名或装配条件，在这一步就能定位，不必等三个真实 Server 都就绪。

**验证**
```bash
mvn -q test -Dtest=McpClientBeanTests
# 期望：PASS（允许启动时子进程 fork 失败——若失败说明 test profile 误用了真实路径，改回假路径）
```

> 若 Spring AI 在 enabled=true 但子进程启动失败时导致**整个上下文加载失败**，把 `McpClientBeanTests` 改成 `@SpringBootTest(properties = "spring.ai.mcp.client.enabled=true")` 且**不配 connections**，只验 auto-config 条件——这是允许的降级，但要在 commit message 里说明。

---

## Step 3 — 启动探针：列出全部 Tool

**做什么**
- 新建 `tool/ToolRegistry.java`：
  - 注入 `List<McpSyncClient>`（或 Spring AI 1.1.8 实际提供的 client 类型）
  - `listAllTools()` → 聚合所有 Server 的 Tool 定义，返回 `List<ToolDescriptor>`（`record`: name, description, serverName, inputSchema）
- 新建 `tool/McpStartupProbe.java`（`@Profile("mcp")` + `ApplicationRunner`）：
  - 启动后调用 `toolRegistry.listAllTools()`，逐条 INFO 日志打印
- 激活真实 profile 启动应用

**为什么单独一步**
「能列出 Tool」和「能调用 Tool」是两层问题。列表为空可能是连接没建、可能是 Server 没注册 Tool、可能是聚合逻辑写错——分开后，Step 3 失败就只查连接和 listTools API。

**验证**（三个 MCP Server 均已就绪）
```bash
source scripts/use-java21.sh
mvn spring-boot:run -Dspring-boot.run.profiles=mcp
```

日志中必须出现 **5 个** Tool 名（顺序不限）：
- `search_knowledge`
- `generate_answer`
- `web_search`
- `createFlashcard`
- `listCards`

```bash
# 启动后确认 fork 了 3 个子进程（数量因实现而异，至少 >0）
pgrep -lf 'rag-mcp|search-mcp|flashcard' | wc -l
```

---

## Step 4 — 手动调用：search_knowledge + web_search

**做什么**
- 在 `ToolRegistry` 增加 `callTool(String toolName, Map<String, Object> params)`：
  - 按 name 路由到对应 `McpSyncClient`
  - 返回原始 JSON 字符串（Iteration 3 再做强类型解析）
  - 超时使用 `agent.step-timeout-ms`
- 新建 `tool/McpConnectivityRunner.java`（`@Profile("mcp")` + `ApplicationRunner`，`@Order(2)` 在 Probe 之后）：
  - 调用 `search_knowledge(query="测试查询", top_k=3)`
  - 调用 `web_search(query="Spring AI MCP", max_results=3)`
  - 把返回摘要（前 200 字符）打 INFO 日志

**为什么单独一步**
RAG 和 Search 是两个独立进程、两种协议栈（Python / Go）。先调通这两个，再调 Java flashcard（还带 MySQL），能把「MCP 协议问题」和「数据库问题」分开。

**验证**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mcp
```

| 调用 | 期望 |
|---|---|
| `search_knowledge("测试查询")` | 日志出现非空 JSON，`isError` 不为 true（或 Python Server 定义的等价成功标志） |
| `web_search("Spring AI MCP")` | 日志出现搜索结果数组，长度 ≥ 1 |

---

## Step 5 — 手动调用：createFlashcard + listCards

> java-flashcard-mcp 的 `@Tool` 方法没有显式 `name=` 覆盖，Spring AI 直接用 Java
> 方法名作为 Tool 名，所以真实名字是 camelCase 的 `createFlashcard`/`listCards`，
> 不是本文档最初设想的 snake_case。

**做什么**
- 在 `McpConnectivityRunner` 追加：
  - `createFlashcard(title="iteration1-test", content="验证 MCP 连通")`
  - `listCards()`
- 调用 `createFlashcard` 时使用**固定 title 前缀** `iteration1-test-`，方便测试后清理

**为什么单独一步**
Flashcard MCP 是唯一依赖 MySQL 的 Server。单独一步后，若失败，日志栈里出现 `SQLException` / `Communications link failure` 就能直接断定是 DB 而非 MCP Client 代码。

**验证**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mcp
```

| 调用 | 期望 |
|---|---|
| `createFlashcard(...)` | 返回含 card id 或 success 标志 |
| `listCards()` | 返回列表中包含刚创建的 `iteration1-test` 卡片 |

---

## Step 6 — 集成测试 + 完整验收

**做什么**
- 新建 `McpConnectivityIntegrationTest`：
  - `@Tag("integration")` + `@EnabledIfEnvironmentVariable(named = "MCP_INTEGRATION", matches = "true")`
  - `@ActiveProfiles("mcp")`
  - 测试 1：`listAllTools()` .size() == 5 且包含上述 5 个 name
  - 测试 2：`callTool("search_knowledge", ...)` 返回非空
  - 测试 3：`callTool("web_search", ...)` 返回非空
- 更新 `ResearchAgentApplicationTests`：**不加** MCP 断言（默认 profile 仍关闭）
- 文档化本地跑集成测试的方式

**为什么单独一步**
CI 默认不跑外部依赖测试；本地一条命令可选开启。`@Tag("integration")` 让 `mvn test` 默认全绿，`MCP_INTEGRATION=true mvn test` 做完整验收。

**验证**

默认（无外部依赖）：
```bash
mvn -q test
# 3 个 Iteration 0 测试 + McpClientBeanTests 全绿；integration 测试 skipped
```

完整（三个 Server + MySQL 就绪）：
```bash
export MCP_INTEGRATION=true
mvn -q test
```

| # | 验收项 | 命令 / 期望 |
|---|---|---|
| 1 | 默认 profile 仍零 MCP 子进程 | `mvn spring-boot:run` → 无 fork |
| 2 | mcp profile 列出 5 个 Tool | Step 3 日志 |
| 3 | RAG 手动调用通 | Step 4 日志 |
| 4 | Search 手动调用通 | Step 4 日志 |
| 5 | Flashcard 手动调用通 | Step 5 日志 |
| 6 | 集成测试（可选） | `MCP_INTEGRATION=true mvn test` |

---

## 产出物

| 类 / 文件 | 包 | 职责 |
|---|---|---|
| `McpServerProperties` | `config` | MCP Server 路径/参数绑定 |
| `McpClientConfig` | `config` | `@Profile("mcp")` 装配辅助 |
| `ToolRegistry` | `tool` | 聚合 Tool 列表 + 统一调用入口 |
| `McpStartupProbe` | `tool` | 启动时打印 Tool 列表 |
| `McpConnectivityRunner` | `tool` | 启动时手动 smoke 三个 Server |
| `application-mcp.yml` | `resources` | MCP profile 连接配置 |
| `McpClientBeanTests` | `test` | Bean 装配对称断言 |
| `McpConnectivityIntegrationTest` | `test` | 可选端到端 |

---

## 回退预案

- **某个 Server 的 stdio 始终调不通**：先用 `curl` 确认该 Server 独立运行正常，再查 Agent 侧 command/args；不要在这一轮引入 HTTP/SSE transport 重写法（留给问题定位后的专项修复）
- **Spring AI listTools API 变更**：以 `/actuator/beans` + 实际 client 类型为准，更新 `ToolRegistry` 注入类型，不改 Iteration 3 的接口契约（`callTool(name, params)`）

建议**每步一个 git commit**，和 Iteration 0 相同。
