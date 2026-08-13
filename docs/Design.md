Agent 是“能力编排者”，它不实现任何 AI 逻辑，只做决策和调度。
定需求设计就是定 Agent 的调度边界和决策规则。

Agent 开发步骤：
1. 先了解 Spring AI 的 Tool Calling 和 Agent 机制（它如何发现工具、如何让 LLM 决策、如何执行调用）
2. 把之前定义的三个 MCP Server 工具列表（`search_knowledge`、`generate_answer`、`web_search`、`createFlashcard`、`listCards`）作为 Agent 的可用工具集
3. 设计 Agent 的系统提示词和决策规则——什么场景该调用哪个工具、什么时候停止调用并给出最终答案
4. 集成 Go Gateway 作为 LLM 调用入口
5. 实现 Memory 机制（短期记忆滑动窗口 + 长期记忆向量检索）
6. 最后暴露 REST 接口给外部系统调用

回答：
1. LLM 收到用户问题，根据 @Tool 注解的描述自主决定调哪个工具、传什么参数，Spring AI 执行对应方法后将结果拼回上下文，LLM 继续推理直到任务完成。
2. 系统提示词和决策规则是 Agent 的"大脑"，直接决定 LLM 什么时候调工具、调哪个、什么时候停。
这个不能随意发挥，必须预先设计好。提示词可随时调整，但停止条件必须在代码里写死。

---

1. 系统提示词：

```text
你是一个 AI 研究助手，负责帮助用户检索知识、搜索信息、生成内容。

你可以使用以下工具：

| 工具名称 | 用途 | 调用时机 |
|---------|------|---------|
| search_knowledge | 在用户的知识库中检索相关文档片段 | 用户的问题需要查资料、查笔记、查文档时 |
| generate_answer | 基于知识库检索结果生成带引用标注的完整回答 | 用户需要详细、有来源的答案时 |
| web_search | 在互联网上搜索最新信息 | 知识库内容不够、过时、或完全不包含用户要的信息时 |
| createFlashcard | 创建一张复习卡片 | 用户明确要求"创建卡片"、"做成卡片"、"帮我记住"时 |
| listCards | 列出所有已有卡片 | 用户想查看已有卡片时 |

决策规则：

1. 先检索，再判断。优先使用 search_knowledge，如果检索结果足够回答，直接生成答案；如果不够或没有结果，再考虑 web_search。
2. 不要重复调用同一个工具。如果 search_knowledge 已经返回空结果，不要再调一次同样的查询。
3. 不要臆测结果。必须基于工具实际返回的内容来回答，不能自己编造。
4. 一次只调一个工具。不要同时调 search_knowledge 和 web_search，等第一个工具返回结果后，再决定是否需要第二个。
5. 如果用户只是闲聊、打招呼、问无关问题，直接回答，不要调任何工具。
```

---

2. 停止条件（代码里硬约束）
   停止条件不能只靠 LLM 自己判断，必须由 ReAct 循环代码强制执行。
   停止条件有两个：
LLM 主动停止：返回 finish 并给出最终答案。
强制停止：循环次数达到上限（如 10 次），防止死循环或 LLM 反复调同一个工具。

---

3. 工具集
   三个 MCP Server 暴露的 Tool 如下：
1. python-rag-mcp：
    - search_knowledge(query: string, top_k: int = 10)：纯检索知识库，返回文档片段+来源。当用户问题需要查资料、查笔记时调用。
    - generate_answer(query: string, top_k: int = 5)：检索+生成+校验，返回带引用标注的完整答案。当用户需要详细、有来源的回答时调用。

2. go-search-mcp：
    - web_search(query: string, max_results: int)：上网搜索补充信息。query 为搜索关键词，max_results 控制返回结果数量。当知识库内容不够或用户问最新信息时调用。

3. java-flashcard-mcp：
    - createFlashcard(title: string, content: string)：创建一张复习卡片。用户明确要求"创建卡片"、"做成卡片"时调用。
    - listCards()：列出已有卡片。用户想看现有卡片时调用。

---

# java-research-agent

**Autonomous research agent orchestrating RAG, web search, and flashcard creation.**

接收自然语言指令，自动拆解为多步任务，按需调用 RAG 检索、在线搜索、知识卡片生成等 MCP 外部工具，通过 Spring AI 编排工具调用并通过 Go Gateway 完成 LLM 推理，最终生成结构化回答。

---

## 整体设计思路

这个项目是整个 AI 工程四层架构的**编排层**，不实现任何 AI 能力。它只做三件事：通过 MCP Client 调三个 MCP Server 的 Tool、通过 Go Gateway 调 LLM 推理、管理 Memory 记住上下文。迭代顺序遵循"先跑通一条完整链路 → 再加智能决策 → 再加记忆 → 最后暴露接口"。

每一次迭代都遵循同一个模板：

1. **这次迭代解决的是哪个具体问题**
2. **技术选型 + 为什么选这个，不选别的**
3. **怎么验收**——用什么指标/行为证明这次迭代确实生效了
4. **产出物**

---

## Iteration 0：项目骨架 + 依赖验证

**目的**

创建 Spring Boot 3.x 项目，引入 Spring AI + MCP Client + Gateway 客户端依赖，确保能编译、能启动、能连上三个 MCP Server 和 Gateway。

**技术选型**

| 组件 | 选择 | 为什么 |
|------|------|--------|
| **框架** | Spring Boot 3.x + Spring AI | 和 java-flashcard-mcp 统一生态 |
| **MCP Client** | Spring AI MCP Client（如不稳定则手写轻量 JSON-RPC Client） | 调三个 MCP Server |
| **LLM 调用** | `OpenAiChatModel` 指向 Go Gateway URL | 统一入口，复用 Gateway 可靠性机制 |
| **Java 版本** | 21（编译目标，JDK 26 运行） | 和你本地环境一致 |

**具体做什么**

- 创建 Spring Boot 项目骨架
- 引入依赖：Spring AI、MCP Client、Spring Web（仅用于后续 REST 接口）
- 配置三个 MCP Server 的连接信息（`application.yml`）
- 配置 Gateway 的 URL 作为 LLM 调用端点
- 写一个最简单的启动类，验证 Spring 上下文能正常加载

**验收标准**

- `mvn compile` 通过
- Spring Boot 启动不报错
- 日志中能看到 MCP Client 初始化信息和 Gateway 连接配置

**产出物**：项目骨架 + `pom.xml` + `application.yml`

---

## Iteration 1：MCP Client 连通性验证

**目的**

验证 Agent 能成功连上三个 MCP Server，获取 Tool 列表，并手动触发一次 Tool 调用。这一步只验证 MCP 协议链路，不涉及 LLM 决策。

**具体做什么**

- 写一个 `McpClientConfig`，配置三个 MCP Server 的连接
- 写一个 `ToolRegistry`，聚合三个 MCP Server 的所有 Tool
- 写一个简单的测试类或 CommandLineRunner，启动后列出所有可用 Tool，并手动调用一次 `search_knowledge("test")` 验证返回结果

**验收标准**

- 启动后日志打印出所有可用 Tool 列表（包含 `search_knowledge`、`generate_answer`、`web_search`、`createFlashcard`、`listCards`）
- 手动触发一次 `search_knowledge("测试查询")`，能拿到 Python RAG MCP 的返回结果
- 手动触发一次 `web_search("测试搜索")`，能拿到 Go Search MCP 的返回结果
- 手动触发一次 `createFlashcard("title", "content")`，能拿到 Java Flashcard MCP 的返回结果

**产出物**：`McpClientConfig.java` + `ToolRegistry.java` + 连通性验证通过日志

---

## Iteration 2：Gateway 连通性 + 单轮对话

**目的**

验证 Agent 能通过 Go Gateway 调 LLM，完成一次简单的单轮对话（不涉及 Tool Calling）。这一步只验证 LLM 调用链路，不涉及 Agent 决策。

**具体做什么**

- 写一个 `GatewayConfig`，配置 `OpenAiChatModel` 指向 Gateway 的 `/v1/chat/completions` 端点
- 写一个简单的对话测试：发送"你好，请介绍一下你自己"，验证能拿到 LLM 回复
- 验证流式和非流式两种模式都能正常工作

**验收标准**

- 非流式调用：发送消息 → 收到完整回复
- 流式调用：发送消息 → 逐 chunk 收到回复
- 日志中能看到请求经过 Gateway 路由到正确的 Provider

**产出物**：`GatewayConfig.java` + 单轮对话验证通过日志

---

## Iteration 3：ReAct 循环实现

**目的**

实现 Agent 的核心决策循环——LLM 根据用户问题自主决定调用哪个 Tool、传什么参数、拿到结果后继续推理还是结束。这是整个项目最关键的一步。

**具体做什么**

- 手写一个简单的 ReAct 循环，不依赖 Spring AI 的 `ToolCallingAgent`
- 把 Tool 列表和 Tool description 作为系统提示词的一部分发给 LLM
- LLM 返回 JSON 格式的决策：`{"action": "call_tool", "tool": "search_knowledge", "params": {"query": "xxx"}}` 或 `{"action": "finish", "answer": "xxx"}`
- Agent 解析 LLM 返回的 JSON，如果是 `call_tool` 就调对应的 MCP Tool，把结果拼回对话上下文，继续下一轮循环
- 设置最大循环次数（如 10 次），防止死循环

**验收标准**

- Agent 收到"帮我查一下知识库里有没有 Transformer 的内容" → 自动调 `search_knowledge("Transformer")` → 返回检索结果
- Agent 收到"知识库里没找到，帮我上网搜" → 自动调 `web_search("Transformer")` → 返回搜索结果
- Agent 收到"把这段内容做成复习卡片" → 自动调 `createFlashcard(...)` → 返回创建成功
- Agent 收到"你好"（不需要调任何工具） → 直接通过 Gateway 生成回复，不调工具
- Agent 连续调 10 次工具仍未完成任务 → 返回"任务未完成"并终止

**产出物**：`ReActLoop.java` + 多场景验证通过日志

---

## Iteration 4：Memory 机制

**目的**

支持多轮对话，让 Agent 记住上下文。验证"Memory 本质是应用层重新注入上下文"这个核心理论。

**具体做什么**

- 用 `InMemoryChatMemory` 或手动维护对话历史列表
- 每次 LLM 调用时，把最近的对话历史拼到系统提示词之前
- 支持简单的滑动窗口（保留最近 10 轮对话），超出部分自动截断
- 预留长期记忆接口（后续可接入向量库，和 RAG 用同一套检索原语）

**验收标准**

- 用户："帮我查一下 Transformer。"
- Agent 调 `search_knowledge("Transformer")` → 返回结果。
- 用户继续："刚才提到的注意力机制再详细讲一下。"
- Agent 能理解"刚才提到的"指的是上一轮检索结果里的"注意力机制"，而不是重新检索所有内容。

**产出物**：`ChatMemoryService.java` + 多轮对话验证通过日志

---

## Iteration 5：REST 接口暴露 + 企业集成场景

**目的**

给 Agent 提供一个简单的 REST 接口，模拟"现有 Java 系统调用 Agent 能力"的企业集成场景。这是 Java 选型的价值最终落地的地方。

**具体做什么**

- 写一个 `AgentController`，暴露 `POST /api/agent/chat` 接口，接收用户问题，返回 Agent 的完整回答
- 支持流式响应（SSE），和 Go Gateway 的流式模式对齐
- 加一个简单的审计日志：记录每次 Agent 调用了哪些工具、做了什么决策
- 写一份技术选型备忘录：为什么这一层用 Java 而不是 Python——面试时可作为技术判断力的书面证据

**验收标准**

- `curl -X POST http://localhost:8080/api/agent/chat -d '{"message":"帮我查一下 Transformer"}'` 返回 Agent 的完整回答
- 流式模式下，回答逐 chunk 返回
- 审计日志能追溯"某次请求调用了哪些工具、每个工具的输入输出是什么"
- 技术选型备忘录完整

**产出物**：`AgentController.java` + 审计日志 + 技术选型备忘录

---

## 迭代顺序背后的逻辑

- **Iteration 0（项目骨架）** → 先让项目能编译、能启动。
- **Iteration 1（MCP 连通性）** → 验证 Agent 能调 MCP Tool，这是后续 ReAct 循环的基础。如果 MCP 链路不通，ReAct 循环无法工作。
- **Iteration 2（Gateway 连通性）** → 验证 Agent 能调 LLM，和 Iteration 1 并行验证，两者都通了才能进入 Iteration 3。
- **Iteration 3（ReAct 循环）** → Agent 的核心，把 Iteration 1 的 Tool 调用能力和 Iteration 2 的 LLM 推理能力串起来，形成自主决策循环。
- **Iteration 4（Memory）** → 在单轮调用基础上加多轮对话能力，让 Agent 记住上下文。
- **Iteration 5（REST 接口 + 企业集成）** → 把 Agent 从"能跑的代码"变成"可被企业系统调用的能力"，给整个项目画上句号。

每一步都在给下一步铺路：先验证独立链路，再串联成完整闭环，再加持久化记忆，最后暴露为服务。