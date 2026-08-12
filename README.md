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
