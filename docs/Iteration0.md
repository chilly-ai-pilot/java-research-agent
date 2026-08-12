版本按 A 走（Boot 3.5.16 + Spring AI 1.1.8 + JDK 21），坐标沿用你的风格：`groupId=com.chilly`、`artifactId=java-research-agent`、包名 `com.chilly.researchagent`。

Iteration 0 拆 **6 步**，每步都能独立验证、独立提交，出问题能立刻定位到是哪一步引入的。

---

## Step 1 — 固定 JDK 21，建目录

**做什么**
- 项目根加 `.mvn/jvm.config`（或 `.sdkmanrc` / `.java-version`，看你用什么管理），把这个项目的 JDK 钉在本地那个 `ms-21.0.12` 上
- 建标准 Maven 目录：`src/main/java` `src/main/resources` `src/test/java`
- `.gitignore` 补 `target/`、`.idea/`（现在 `.idea/` 还是 untracked 状态）

**为什么单独一步**
你本机默认 JDK 是 26，`mvn -v` 显示的就是 26。Boot 3.5 / Spring Framework 6.2 没覆盖到 JDK 26，Spring 内部用 ASM 读 class 时遇到未知 class file version 会抛一个跟业务毫无关系的异常。**这个坑必须在写第一行代码之前堵掉**，否则后面每个报错你都要先怀疑一遍 JDK。

**验证**：项目目录下 `mvn -v` 的 `Java version` 显示 21。

---

## Step 2 — 写 pom.xml，只验证依赖能解析

**做什么**
- 沿用你 flashcard 的 pom 结构（parent + `spring-ai-bom` dependencyManagement），只换版本：`spring-boot-starter-parent` → **3.5.16**，`spring-ai.version` → **1.1.8**
- 依赖只加 5 个：`spring-boot-starter-web`、`spring-boot-starter-actuator`、`spring-ai-starter-model-openai`、`spring-ai-starter-mcp-client`、`spring-boot-starter-test`
- **不加** `data-jpa` / `mysql-connector`（Agent 层无持久化）
- **不加** Lombok（理由见文末）

**为什么单独一步**
这一步是整个升级方案的**唯一真实风险点**。你之前只用过 `1.0.0-M7`，从 M7 跨到 1.1.8 中间隔了 GA + 一个 minor，Spring AI 在 GA 前后调整过 artifact 命名和包路径。这一步就是要在写任何代码之前，先确认这两个 starter 在 1.1.8 下**存在且能拉下来**。

> 我已经在 Maven Central 上确认 `spring-ai-starter-mcp-client` 和 `spring-ai-starter-model-openai` 两个坐标在 1.1.8 都有发布，且 1.1.8 的 Boot baseline 就是 3.5.15——和我们选的 3.5.16 同线，兼容。

**验证**：`mvn -q dependency:resolve` 无报错；`mvn dependency:tree | grep spring-ai` 能看到两个 starter 都解析到 1.1.8。

**回退预案**：万一 1.1.8 有解析或装配问题，**退到 Spring AI 1.0.9 + Boot 3.5.x**，不要退回 M7（里程碑版没有补丁维护）。

---

## Step 3 — 启动类 + 空包结构

**做什么**
- `ResearchAgentApplication.java`（一个 `@SpringBootApplication` + main）
- 按后续迭代提前建空包：`config/` `tool/` `react/` `memory/` `audit/` `web/`

**为什么**
包结构提前定好，后面每个迭代往里填，避免写到 Iteration 3 时代码全堆在一个包里再返工。

**验证**：`mvn -q clean compile` 通过。

---

## Step 4 — application.yml（关键：MCP client 保持关闭）

**做什么**
- `server.port: 8081`
- `spring.ai.openai.base-url` 指向 Gateway，`api-key` 用占位符，`temperature: 0.1`
- `spring.ai.mcp.client.enabled: false`，且 **connections 一个都不配**（双保险）
- `agent.*` 自定义配置块（max-steps / 各类超时 / observation 截断长度）
- actuator 暴露 health

**为什么这一步最容易出事**
`spring-ai-starter-mcp-client` 一旦配了 stdio connections，会在 **Spring 启动阶段**就去 fork 子进程——Python venv、Go 二进制、Flashcard jar（还连 MySQL）。任何一个环境没准备好，应用直接启动失败。

那样你就分不清是骨架有问题还是子进程环境没就绪。**Iteration 0 引依赖但不建连接**，连通性完整留给 Iteration 1。这是我对原文档"Iteration 0 确保能连上三个 MCP Server"这句的明确偏差——那个目标在这一步做不到，也不该做。

**验证**：`mvn spring-boot:run` 启动成功，日志里**没有任何子进程 fork、没有 DataSource 相关输出**。

---

## Step 5 — AgentProperties + StartupBanner

**做什么**
- `AgentProperties`：`@ConfigurationProperties("agent")`，把 yml 里的 `agent.*` 绑成强类型对象
- `StartupBanner`：一个 `ApplicationRunner`，启动时打印**实际生效**的配置：

```
Agent config → gateway=http://localhost:8080, model=deepseek-chat,
               temp=0.1, maxSteps=10, stepTimeout=30s, mcpClient=DISABLED
```

**为什么值得单独一步**
这条正好满足原文档 Iteration 0 的验收项"日志中能看到 Gateway 连接配置"。但注意区别：它打印的是 **Environment 里解析后的最终值**，不是 yml 文件内容。环境变量覆盖、profile 覆盖导致配置和你以为的不一样——这类问题拖到 Iteration 3 才发现会非常费时间，现在一行日志就永久解决。

**验证**：启动日志里出现上面那行，且值与 yml 一致。

---

## Step 6 — 冒烟测试 + 完整验收

**做什么**
写一个 `@SpringBootTest`，断言两件事：
1. `OpenAiChatModel` bean 存在
2. 上下文里**没有** MCP client bean

然后跑完整验收清单：

| # | 验收项 | 命令 |
|---|---|---|
| 1 | 编译通过 | `mvn -q clean compile` |
| 2 | 测试通过 | `mvn -q test` |
| 3 | 启动成功、无子进程、无 DataSource 报错 | `mvn spring-boot:run` |
| 4 | 健康检查 | `curl -s localhost:8081/actuator/health` → `{"status":"UP"}` |
| 5 | 配置横幅正确 | 看日志 |
| 6 | JDK 是 21 | `mvn -v` |

**为什么第 1 条断言重要**
它证明"OpenAI starter 装配成功，但零外部依赖"。有了这个保证，Iteration 2 一旦调 Gateway 失败，你能 **100% 确定问题在 Gateway 侧或网络**，不在 Spring 装配。这是 Iteration 0 真正的产出价值——不是"跑起来了"，而是"排除了一整类后续故障来源"。

---

## 两个说明

**Lombok 为什么不引**
你 flashcard 里用它是合理的（JPA 实体、DTO 一堆 getter/setter）。但 Agent 层几乎没有贫血数据类，主要是逻辑类，少数几个 DTO 用 `record` 就够了。少一个注解处理器，就少一类"编译过了但 IDE 报红"的干扰。

如果你还是想用，记得**保留你 pom 里那段 `annotationProcessorPaths` 和 `-proc:full`**——JDK 21 开始隐式注解处理被弃用（JDK 23 起默认关闭），你那个 pom 写对了，别删。

**为什么每步都要能编译/启动**
6 步里有 4 步的验证方式是"跑一下"。建议**每步一个 git commit**。Spring AI 从 M7 跨到 1.1.8 属于跨大版本，真出装配问题时，能 `git bisect` 到具体是哪一步引入的，比对着一屏 Spring 启动异常猜要快得多。

---

方案确认没问题的话，我从 Step 1 开始做，每步跑完验证再进下一步，中间有任何一步的验收不过我停下来跟你说。

---

总结报告

## Iteration 0 总结：项目骨架 + 依赖验证

**目标**：搭一个能编译、能启动、零外部依赖的 Spring Boot 骨架，为后续迭代排除掉"是骨架错了还是环境没就绪"这类噪音。

### 六步做了什么

| 步骤 | 产出 | 关键点 |
|---|---|---|
| 1 | `scripts/use-java21.sh` + Maven 目录 | 本机默认 JDK 26，Boot 3.5 不支持；脚本把 `JAVA_HOME` 钉到 21 |
| 2 | `pom.xml` | Spring Boot 3.5.16 + Spring AI 1.1.8，5 个依赖（web/actuator/openai/mcp-client/test）；`maven-enforcer-plugin` 兜底——忘记 source 脚本时构建第一秒报中文错误，而非启动阶段的 ASM 异常 |
| 3 | `ResearchAgentApplication.java` + 六个空包 | `config/tool/react/memory/audit/web`，对应 Iteration 1-5 的落地位置 |
| 4 | `application.yml` | 端口 8081；`spring.ai.mcp.client.enabled: false` 且不配 connections——MCP client 一旦配连接会在启动阶段 fork 三个子进程，Iteration 0 故意不碰 |
| 5 | `AgentProperties` + `StartupBanner` | 启动时打印 Environment 里**实际生效**的配置值，不是 yml 文件内容 |
| 6 | `ResearchAgentApplicationTests.java` | 两条核心断言 + 完整验收 |

### 最终验收（6/6 PASS）

1. `mvn -q clean compile` 通过
2. `mvn -q test`：3 个测试全绿
3. `mvn spring-boot:run` 启动成功，`pgrep` 确认零子进程、无 DataSource/JDBC/MySQL 日志
4. `curl localhost:8081/actuator/health` → `UP`
5. 启动横幅与 yml 逐字段一致
6. `mvn -v` 确认 JDK 21.0.12

### 这次迭代真正验证的两件事（你选中的那行）

```java
mcpClientBeansAreAbsent();
```

对应的两条断言，都做过反证，不是摆设：

- **`OpenAiChatModel` bean 存在** → 证明 Spring AI 装配成功。反证：换成必然不存在的类型查询会抛 `NoSuchBeanDefinitionException`，证明这条断言真的能失败。
- **上下文里没有 MCP client bean**（`mcpSyncClients`/`mcpAsyncClients` 等四个具体 bean 名）→ 证明 `spring.ai.mcp.client.enabled: false` 确实生效。反证：把该配置临时翻成 `true`，断言真的报错（`Failures: 1`）。

这两条叠加起来是这次迭代最大的产出：**排除了一整类后续故障来源**——Iteration 2 调 Gateway 万一失败，能 100% 确定问题不在 Spring 装配，只会在 Gateway 侧或网络。

### 过程中两个偏离原方案的地方

- 原方案想用 `.mvn/jvm.config` 钉 JDK，实际发现它只传 JVM 参数不能选 JDK，改成脚本设 `JAVA_HOME` + enforcer 硬校验兜底。
- 原方案想用 `getBeanNamesForType(McpSyncClient.class)` 判断"无 MCP client"，反查字节码发现这是假阳性（实际 bean 类型是 `List<McpSyncClient>`），改成按具体 bean 名断言，并用 `/actuator/beans` 实测核实过。

### 已提交状态

六步 commit 全部保留（`--no-ff` 合并，未 squash），已合入 `main`，工作区干净。

---
