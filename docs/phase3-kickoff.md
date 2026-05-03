# Phase 3 Kickoff

## 1. 当前阶段

项目已经完成 Phase 1 主线收尾，并在 Phase 2 打通了 workspace 文档资产、解析、chunk、检索、workspace 分析文档上下文注入、混合 rerank 和 benchmark 诊断链路。

当前进入 Phase 3：构建结构化 + 非结构化联合上下文层。

Phase 3 的核心目标是：

**把 schema、关系、业务语义和文档 chunk 从零散 prompt 片段，逐步收敛为可追踪、可扩展、可评估的统一上下文包。**

---

## 2. 本轮已完成内容

### 2.1 新增结构化上下文模型

- [StructuredContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\StructuredContext.java)

当前封装：

- workspace group id
- workspace datasets
- workspace relations
- schema prompt
- relation prompt

对应 roadmap：

- `3.1.1` 定义 `StructuredContext` 数据结构

### 2.2 新增文档上下文模型

- [DocumentContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\DocumentContext.java)

当前封装：

- document need
- document strategy
- topK
- context budget
- included chunks
- rendered prompt

对应 roadmap：

- `3.1.2` 定义 `DocumentContext` 数据结构

### 2.3 新增语义上下文模型

- [SemanticContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\SemanticContext.java)

当前封装：

- workspace / dataset semantic prompt

对应 roadmap：

- `3.1.3` 定义 `SemanticContext` 数据结构

### 2.4 新增统一分析上下文模型

- [UnifiedAnalysisContext.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\UnifiedAnalysisContext.java)

当前封装：

- structured context
- document context
- semantic context

并提供兼容旧 prompt 链路的读取方法：

- `businessContextPrompt()`
- `schemaPrompt()`
- `relationPrompt()`
- `documentContextPrompt()`
- `documentStrategy()`

对应 roadmap：

- `3.1.4` 定义 `UnifiedAnalysisContext` 数据结构

### 2.5 接入现有 workspace context assembler

- [WorkspaceContextAssembler.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\WorkspaceContextAssembler.java)

当前 `WorkspaceContextAssembler` 已经在汇编阶段生成 `UnifiedAnalysisContext`，同时保留既有 prompt 字段，避免影响 `AnalysisService` 当前行为。

这一步也为 Phase 3.2 做了最小入口：

- structured context 由已有 workspace schema context 转换而来
- semantic context 由已有 business context prompt 转换而来
- document context 由已有 document retrieval / budget / render 逻辑转换而来

### 2.6 分析入口开始读取 unified context

- [AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java)

workspace 分析入口现在从 `analysisContext.unifiedContext()` 读取 prompt 兼容访问器，再传给现有 `AnalysisPlanGenerator`。

这一步让调用方开始依赖统一上下文包，同时保留 `WorkspaceAnalysisContext` 的旧字段，便于后续分阶段迁移。

对应 roadmap：

- `3.2.1` 新建上下文装配服务骨架
- `3.2.2` 先接 structured context 组装
- `3.2.3` 接入 semantic context 组装
- `3.2.4` 接入 document context 组装

### 2.7 新增 unified context 摘要

- [UnifiedContextSummary.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\context\UnifiedContextSummary.java)

`UnifiedAnalysisContext` 现在可以输出轻量摘要，用于日志、benchmark 和后续调试：

- workspace group id
- structured / document / semantic source marker
- dataset / relation 数量
- semantic context 是否存在
- document strategy / topK / budget / chunk 数量
- structured / relation / semantic / document prompt 字符数

`WorkspaceContextAssembler` 会在组装完成后记录这份摘要，方便观察一次分析实际带入了哪些上下文。

对应 roadmap：

- `3.3.4` 给每类上下文增加来源标记
- `3.2.5` 组合成统一 prompt 载荷

### 2.8 新增 unified context 模型测试

- [UnifiedAnalysisContextTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\model\context\UnifiedAnalysisContextTest.java)

当前覆盖：

- structured / semantic / document context 都存在时，summary 能正确统计 group、dataset、relation、document chunk、prompt 字符数
- structured / semantic / document context 缺失时，summary 返回稳定默认值

这为后续把 summary 写入 benchmark 报告提供了低成本回归保护。

### 2.9 benchmark 报告接入 context summary

- [AnalysisResponse.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\dto\AnalysisResponse.java)
- [AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java)
- [run_benchmarks.py](F:\data-analysis-platform\harness\run_benchmarks.py)

workspace 分析响应现在会返回 `contextSummary`，benchmark report 的每个 `response_excerpt` 会保留该字段。

这让 Phase 3 核验可以直接从 `harness/runs/*.json` 里确认一次分析实际带入的 structured / document / semantic context 来源、预算和规模。

### 2.10 benchmark 增加 context summary 断言

- [run_benchmarks.py](F:\data-analysis-platform\harness\run_benchmarks.py)
- [traffic_orders_join_signal.json](F:\data-analysis-platform\harness\benchmarks\workspace\traffic_orders_join_signal.json)
- [workspace_orders_users_note_priority.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_users_note_priority.json)
- [workspace_orders_products_merchandising_priority.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_products_merchandising_priority.json)

benchmark 现在支持：

- `context_summary_exact`：断言 source marker、document strategy 等精确值
- `context_summary_min`：断言 dataset count、document chunk count、topK、budget、prompt 字符数等数值下限

workspace SQL-only case 会断言 document strategy 为 `SKIP`，hybrid cases 会断言 document strategy 为 `HEAVY` 且至少带入一个 document chunk。

### 2.11 新增 revenue policy 联合问题

- [revenue-recognition-policy.md](F:\data-analysis-platform\harness\resources\revenue-recognition-policy.md)
- [workspace_orders_products_revenue_policy.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_orders_products_revenue_policy.json)

新增一条明确依赖“表 + 文档”的业务口径问题：

- 表侧：orders + products 聚合 product category revenue
- 文档侧：revenue recognition policy 要求 recognized revenue 只包含 `PAID`，排除 `REFUNDED`
- 验证点：benchmark 同时检查返回字段、summary 是否提及 refund/policy，以及 context summary 是否显示 document strategy 为 `HEAVY`

对应 roadmap：

- `3.4.1` 准备首批“表 + 文档”联合问题集
- `3.4.2` 验证“文档口径影响分析结论”的场景

### 2.12 新增 document-only 分流

- [AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java)
- [workspace_revenue_policy_document_only.json](F:\data-analysis-platform\harness\benchmarks\hybrid\workspace_revenue_policy_document_only.json)

workspace 分析入口现在会识别明确的 document-only query，例如包含 `document only`、`no SQL`、`只需文档`、`不需要 SQL` 的问题。

命中后：

- 仍通过 `WorkspaceContextAssembler` 构建 `UnifiedAnalysisContext`
- 复用 document retrieval 得到的 chunk 生成文档回答摘要
- 不调用 workspace analysis agent
- 不生成 SQL
- 响应仍返回 `contextSummary`

benchmark 新增 `generated_sql_absent` 断言，用来验证 document-only 场景不会强行生成 SQL。

对应 roadmap：

- `3.4.3` 验证“只需文档、不需 SQL”的场景分流

---

## 3. 验证结果

本轮执行：

```powershell
mvn -DskipTests compile
```

结果：backend compile 成功。

---

## 4. 下一步建议

Phase 3 主体任务已经收束，详细收尾见 [phase3-closeout.md](F:\data-analysis-platform\docs\phase3-closeout.md)。

下一步优先进入 Phase 4：

1. 抽出独立 query intent / route decision 服务
2. 将 document-only / structured-only / hybrid analysis 做成显式 route
3. 在响应和 benchmark report 中记录 route decision
4. 开始升级 agent 编排，减少单轮大 prompt 对稳定性的影响
