# Java Research Agent 技术选型备忘录

本文档说明 `java-research-agent`（Spring Boot **3.5.16** + Spring AI **1.1.8** + **JDK 21**，Agent 端口 **8081**）的关键架构选择及反证。

---

## 1. 为什么编排层选 Java（Spring 生态）

ReAct 主循环、Memory、REST 接口、审计日志均落在 Spring Boot 应用中。Java 提供强类型与成熟的依赖注入、配置绑定（`@ConfigurationProperties`）、测试基建（`@WebMvcTest`、`@SpringBootTest`），便于与企业现有 Java 微服务同栈集成（Actuator 健康检查、统一日志、Maven 构建）。Iteration 5 的 `POST /api/agent/chat` 可直接被内部系统以 HTTP 调用。

**反证**：若编排层用 Python（如 LangChain），与已存在的 Java MCP Server（flashcard）、企业 Java 网关团队协作成本更高，类型与契约更难在编译期约束。

---

## 2. 为什么 LLM 不走 Provider 直连而走 Go Gateway

应用通过 Spring AI `OpenAiChatModel` 访问 **OpenAI 兼容** 的 Go LLM Gateway（`spring.ai.openai.base-url`），而非在 Java 内嵌各厂商 SDK。Gateway 统一鉴权、限流、模型路由与可观测；Java Agent 只关心「发 Prompt、收 JSON 决策」，Provider 变更时不必改 Agent 代码。

**反证**：直连 OpenAI/Anthropic 会在每个服务重复 API Key 管理、配额与审计策略，多语言客户端行为不一致。

---

## 3. 为什么 Tool 走 MCP 而不是 Java 内嵌

`search_knowledge`（RAG/Python）、`web_search`（Go）、`createFlashcard`（Java MCP）等能力以 **MCP Server** 独立进程暴露，Java 侧仅维护 `ToolRegistry` 动态列表。语言异构、独立部署、按域扩展 Tool 时不需重启 Agent；RAG 与搜索各取所长。

**反证**：把所有 Tool 写成 Java 库会耦合 Python RAG 栈，Tool 故障或升级会拖垮整个 Agent 进程。

---

## 4. 为什么手写 ReAct 而不用 Spring AI ToolCallingAgent

本项目采用 **JSON 决策**（`call_tool` / `finish`）+ 自研 `ReActLoop`，显式实现 `max-steps`、`step-timeout-ms`、`total-timeout-ms` 与逐步 `ReActStep` 审计链。LLM 每步输出可日志追踪、可单测 Parser；停止条件与重复 Tool 检测（`DuplicateToolCallException`）行为确定。

**反证**：框架内置 Tool Calling 往往把工具调用藏在抽象后，难以保证「先 search_knowledge 再 web_search」等业务规则，审计字段也不易与 REST `steps` 对齐。

---

## 5. 为什么 Memory 在应用层注入而不是框架黑盒

多轮对话使用 `SessionChatMemory` + `ReActLoop.run(sessionId, message)`：循环前 `getRecent`，循环后只持久化 **最终 QA 对**（不存每步 observation），滑动窗口由 `agent.memory.max-messages` 配置。Memory 与 Prompt 组装、Gateway `history` 参数同源，Session 隔离可测（`MultiTurnScenarioTest`）。

**反证**：若完全依赖框架隐式 Memory，难以控制窗口内容与 session 边界，也不利于 Iteration 5 REST 层显式传递 `sessionId` 给调用方。

---

## 版本与端口一致性

| 项 | 值 |
|---|---|
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.8 |
| JDK | 21 |
| Agent HTTP | `8081` |
| 健康检查 | `GET /actuator/health` |
| Chat API | `POST /api/agent/chat` |

以上能力与 Iteration 0–5 代码实现一致；鉴权、长期记忆持久化、生产 MCP smoke 关闭等待后续迭代。
