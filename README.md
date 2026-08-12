# java-research-agent
Autonomous research agent orchestrating RAG, web search, and flashcard creation.

## 开发环境

本项目固定使用 **JDK 21**（本机默认 JDK 是 26，Spring Boot 3.5 不支持）。

每次开新终端，先执行：

```bash
source scripts/use-java21.sh
```

忘了也没关系——`pom.xml` 里的 maven-enforcer-plugin 会在构建一开始就拦下来。

| 组件 | 版本 | 端口 |
|---|---|---|
| Java | 21 | — |
| Spring Boot | 3.5.16 | — |
| Spring AI | 1.1.8 | — |
| java-research-agent | — | 8081 |
| go-llm-gateway（依赖） | — | 8080 |
| rag-mcp HTTP（依赖，可选） | — | 8000 |

## 跑测试

默认（无外部依赖，CI 用）：

```bash
mvn -q test
```

MCP 集成测试（`McpConnectivityIntegrationTest`）默认 skip。本地跑需要
python-rag-mcp / go-search-mcp / java-flashcard-mcp 三个 Server + MySQL
都已就绪（路径见 `application-mcp.yml`，可用环境变量覆盖），然后：

```bash
export MCP_INTEGRATION=true
mvn -q test
```

已知问题：`searchKnowledgeReturnsNonEmptyResult` 和
`listAllToolsReturnsAllFiveTools` 目前会失败——rag-mcp 首次加载 embedding
模型时把日志打到 stdout 而非 stderr，污染 JSON-RPC 流；该连接一旦被污染就
永久失效（含 listTools()），要等 rag-mcp 把这条日志挪到 stderr 才能修，
不在本仓库范围内。详见 `McpConnectivityIntegrationTest` 的类注释和
commit dcb0aba。
