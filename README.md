# java-research-agent

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.8-6DB33F?logo=spring&logoColor=white)

Autonomous research agent orchestrating RAG, web search, and flashcard creation.

接收自然语言指令，按需调用 RAG 检索、在线搜索、知识卡片等 MCP 工具，经 Go Gateway 完成 LLM 推理，输出结构化回答。本仓库是 AI 工程的**编排层**，只做三件事：调 MCP 工具、调 LLM、管理记忆。

## 目录

- [特性](#特性)
- [架构](#架构)
- [快速开始](#快速开始)
- [REST API](#rest-api)
- [配置](#配置)
- [Chat UI](#chat-uistreamlit)
- [测试](#测试)
- [项目结构](#项目结构)
- [文档](#文档)

## 特性

- **手写 ReAct 循环** — LLM 每步以 JSON 输出 `call_tool` / `finish` 决策；`max-steps`、单步/总超时、重复工具检测由代码强制，不依赖 Spring AI 的隐式 Tool Calling
- **MCP 工具编排** — 经 MCP Client 聚合 3 个 Server 的 5 个工具：`search_knowledge` / `generate_answer` / `web_search` / `createFlashcard` / `listCards`
- **LLM 统一走 Go Gateway** — OpenAI 兼容协议，Agent 不持有 Provider API Key
- **会话记忆** — 按 `sessionId` 隔离的滑动窗口，默认保留最近 20 条消息
- **REST + SSE 双模式** — `POST /api/agent/chat` 同时支持非流式 JSON 与 SSE 流式
- **审计日志** — 每次请求以单行 JSON 写入 INFO 日志（`[Audit]` 前缀），含逐步决策、工具参数与终止原因
- **Streamlit Chat UI** — 独立 Python 前端，对接 REST/SSE API

## 架构

```
user → Streamlit UI (:8501) → java-research-agent (:8081) → go-llm-gateway (:8080) → LLM Provider
                                            │
                                            └─ MCP Client (stdio) → python-rag-mcp / go-search-mcp / java-flashcard-mcp
```

| 组件 | 版本 | 端口 |
|---|---|---|
| Java | 21 | — |
| Spring Boot | 3.5.16 | — |
| Spring AI | 1.1.8 | — |
| java-research-agent | — | 8081 |
| Chat UI（Streamlit） | — | 8501 |
| go-llm-gateway | — | 8080 |

## 快速开始

| 依赖 | 说明 | 必需 |
|---|---|---|
| go-llm-gateway | LLM 推理入口（:8080） | 是 |
| python-rag-mcp / go-search-mcp / java-flashcard-mcp | 3 个 MCP Server（stdio） | 否，不启动则只有纯对话能力 |

JDK 21 必需（本机默认 JDK 26 不受 Spring Boot 3.5 支持）：

```bash
source scripts/use-java21.sh                                        # 钉住 JAVA_HOME
mvn spring-boot:run                                                 # 纯对话模式
mvn spring-boot:run -Dspring-boot.run.profiles=mcp                  # 加载 MCP 工具
```

未执行 use-java21.sh 时，`pom.xml` 的 maven-enforcer-plugin 会在构建开始时报错拦截。MCP 连接配置见 `application-mcp.yml`。

验证：`curl http://localhost:8081/actuator/health`

## REST API

### POST /api/agent/chat

非流式 JSON 与 SSE 流式共用同一端点，由请求体 `stream` 字段区分。

| 字段 | 类型 | 说明 |
|---|---|---|
| `message` | string | 用户消息（必填） |
| `sessionId` | string | 会话 ID，空则服务端生成 UUID |
| `stream` | boolean | SSE 流式，默认 `false` |

```bash
curl -X POST http://localhost:8081/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"帮我查一下知识库里的 Transformer 内容"}'
```

非流式响应：

```json
{
  "sessionId": "550e8400-…",
  "answer": "……",
  "terminatedReason": "LLM_FINISH",
  "steps": [
    { "stepIndex": 1, "action": "call_tool", "tool": "search_knowledge", "params": { "query": "Transformer" } }
  ]
}
```

`terminatedReason` 取值：`LLM_FINISH` / `MAX_STEPS` / `TOTAL_TIMEOUT` / `ERROR`。

流式（`"stream": true`，curl 加 `-N`）SSE 事件：

| 事件 | 数据 | 说明 |
|---|---|---|
| `step` | `StepAuditDto` JSON | 每次工具调用 |
| `token` | 文本 chunk | 最终答案增量 |
| `done` | `{ "sessionId", "terminatedReason" }` | 流结束 |

## 配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `GATEWAY_URL` | `http://localhost:8080` | Go Gateway 地址 |
| `GATEWAY_API_KEY` | `not-needed` | 真实 key 由 Gateway 注入，Agent 不持有 |
| `AGENT_MODEL` | `deepseek-chat` | LLM 模型名 |
| `RAG_MCP_*` / `SEARCH_MCP_*` / `FLASHCARD_MCP_*` | 本机对应仓库路径 | 三个 MCP Server 的 command/args/env，默认值见 `application-mcp.yml` |
| `DEEPSEEK_API_KEY` / `TAVILY_API_KEY` | — | 透传给 rag-mcp / search-mcp |

Agent 行为参数见 `application.yml` 的 `agent.*`（`max-steps=10`、`step-timeout-ms=30000`、`total-timeout-ms=90000`、`memory.max-messages=20`）。

## Chat UI（Streamlit）

Agent 后端仍是 Java（8081）；聊天页用 Streamlit 单独跑，对接 REST/SSE API。

```bash
# 终端 1：Agent
source scripts/use-java21.sh
mvn spring-boot:run -Dspring-boot.run.profiles=mcp

# 终端 2：Chat UI，浏览器打开 http://localhost:8501
./scripts/run-chat-ui.sh
```

环境变量：`AGENT_API_URL`（默认 `http://127.0.0.1:8081`）、`CHAT_UI_PORT`（默认 `8501`）、`PYPI_INDEX`（pip 镜像，默认失败时回退清华源）。Python 3.10–3.12 最稳，3.13 需较新的 streamlit。

**常见问题**：pip 报 `No matching distribution found for protobuf`，通常是 macOS 官方 Python 未装 SSL 根证书。任选其一：

```bash
/Applications/Python\ 3.13/Install\ Certificates.command   # 运行一次（路径随版本略有不同）
./scripts/run-chat-ui.sh                                   # 或直接重跑：脚本内置 venv + certifi + 镜像回退
```

## 测试

```bash
mvn -q test                      # 默认：无外部依赖，CI 用
MCP_INTEGRATION=true mvn -q test # 集成测试：需 3 个 MCP Server + MySQL 就绪
```

## 项目结构

```
├── src/main/java/com/chilly/researchagent/
│   ├── react/     # ReAct 循环、决策解析、Gateway 对话、Prompt 模板
│   ├── tool/      # 工具注册表、启动探针、连通性验证
│   ├── memory/    # 会话记忆（滑动窗口）与长期记忆接口
│   ├── web/       # REST/SSE 入口与 DTO
│   ├── audit/     # 审计日志
│   └── config/    # 配置绑定与启动检查
├── src/main/resources/   # application*.yml、prompts/、static/
├── ui/                   # Streamlit Chat UI
├── scripts/              # use-java21.sh、run-chat-ui.sh
└── docs/                 # 设计与迭代文档
```

## 文档

| 文档 | 内容 |
|---|---|
| [docs/Design.md](docs/Design.md) | 需求设计与 Iteration 0–5 规划 |
| [docs/Iteration0.md](docs/Iteration0.md) ~ [docs/Iteration5.md](docs/Iteration5.md) | 各迭代实现记录 |
| [docs/tech-choice-java-agent.md](docs/tech-choice-java-agent.md) | 技术选型备忘录 |
