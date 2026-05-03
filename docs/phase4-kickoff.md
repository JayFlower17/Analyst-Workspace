# Phase 4 Kickoff

## 1. 当前阶段

Phase 4 的目标是把分析过程从“单轮大 prompt + 工具调用”升级为更稳定、可追踪、可测试的分析工作流。

当前 Phase 4 从问题路由层开始：

**先让系统明确知道一次请求应该走 structured-only、document-only、hybrid 还是 legacy dataset 路线。**

---

## 2. 本轮已完成内容

### 2.1 新增 route 类型

- [AnalysisRouteType.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\route\AnalysisRouteType.java)

当前类型：

- `STRUCTURED_QUERY`
- `DOCUMENT_EXPLANATION`
- `HYBRID_ANALYSIS`
- `LEGACY_DATASET`

对应 roadmap：

- `4.1.1` 定义问题类型分类

### 2.2 新增 route decision 模型

- [AnalysisRouteDecision.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\route\AnalysisRouteDecision.java)

当前字段：

- route
- reason
- requiresWorkspaceContext
- requiresDocumentContext
- allowsSqlGeneration

### 2.3 新增最小 router

- [AnalysisRouter.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisRouter.java)

当前规则：

- 没有 groupId：`LEGACY_DATASET`
- 明确 no SQL / document-only：`DOCUMENT_EXPLANATION`
- 提到 document / policy / rule / priority / explain 等信号：`HYBRID_ANALYSIS`
- 其它 workspace query：`STRUCTURED_QUERY`

router 会记录 route、是否允许 SQL、是否需要文档上下文和 reason。

对应 roadmap：

- `4.1.2` 实现最小 intent router
- `4.1.3` 为 router 增加日志记录

### 2.4 接入响应和 benchmark

- [AnalysisResponse.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\dto\AnalysisResponse.java)
- [run_benchmarks.py](F:\data-analysis-platform\harness\run_benchmarks.py)

分析响应现在返回 `routeDecision`，benchmark report 的 `response_excerpt` 会保留该字段。

benchmark 新增：

- `route_decision_exact`

用于断言 route、requiresWorkspaceContext、requiresDocumentContext 和 allowsSqlGeneration。

### 2.5 新增 router 测试

- [AnalysisRouterTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\service\AnalysisRouterTest.java)

覆盖：

- workspace structured query
- policy hybrid analysis
- document-only no-SQL question
- legacy dataset request

---

## 3. 下一步建议

优先继续 Phase 4.2：

1. 抽离 analysis planner 接口
2. 定义 structured / hybrid / document-only 的结构化 plan
3. 将 route decision 传入 planner
4. 让 benchmark report 同时展示 route decision、context summary 和 plan summary

---

## 4. Phase 4.2 起步

### 4.1 新增 planner 模型

- [AnalysisPlan.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\plan\AnalysisPlan.java)
- [AnalysisPlanStep.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\plan\AnalysisPlanStep.java)
- [AnalysisPlanStepType.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\plan\AnalysisPlanStepType.java)

planner 输出是结构化对象，而不是自由文本。

### 4.2 新增 planner 接口和规则实现

- [AnalysisPlanner.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisPlanner.java)
- [RuleBasedAnalysisPlanner.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\RuleBasedAnalysisPlanner.java)

当前支持：

- structured plan：route / build context / generate SQL / execute SQL / summarize
- hybrid plan：route / build context / retrieve documents / generate SQL / execute SQL / summarize
- document-only plan：route / build context / retrieve documents / answer from documents / summarize
- legacy dataset plan：保持旧入口兼容

### 4.3 接入响应与 benchmark

`AnalysisService` 现在会基于 `AnalysisRouteDecision` 生成 `AnalysisPlan`，并在 `AnalysisResponse` 返回。

benchmark report 的 `response_excerpt.analysisPlan` 会记录：

- route
- objective
- stepTypes
- stepCount

benchmark 也支持 `analysis_plan_step_types_exact`，用于断言 structured / hybrid / document-only 的计划步骤序列。

对应 roadmap：

- `4.2.1` 抽离 analysis planner 接口
- `4.2.2` 实现结构化分析 planner
- `4.2.3` 实现联合分析 planner
- `4.2.4` 限制 planner 输出结构

---

## 5. Phase 4.3 起步

### 5.1 抽离 SQL executor

- [SqlExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\SqlExecutor.java)
- [DuckDbSqlExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\DuckDbSqlExecutor.java)
- [SqlExecutionResult.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\SqlExecutionResult.java)

SQL 执行现在有独立边界：

- SQL whitelist validation
- DuckDB query execution
- success / failure
- row count
- durationMs
- error message

`AnalysisService` 不再直接执行 SQL 白名单校验和 DuckDB query，而是通过 `SqlExecutor` 获取 `SqlExecutionResult`。

对应 roadmap：

- `4.3.1` 抽离 SQL executor 接口

### 5.2 抽离 Python executor

- [PythonExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\PythonExecutor.java)
- [HttpPythonExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\HttpPythonExecutor.java)
- [PythonExecutionResult.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\PythonExecutionResult.java)

Python 执行现在也有独立边界：

- HTTP executor client 适配
- success / failure
- row count
- durationMs
- error message
- recommended chart
- summary

`AnalysisService` 不再直接调用 `PythonExecutorClient`，而是通过 `PythonExecutor` 获取 `PythonExecutionResult`。

对应 roadmap：

- `4.3.2` 抽离 Python executor 接口

### 5.3 抽离 retrieval executor

- [RetrievalExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\RetrievalExecutor.java)
- [DocumentRetrievalExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\DocumentRetrievalExecutor.java)
- [RetrievalExecutionResult.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\RetrievalExecutionResult.java)

文档检索现在有独立执行边界：

- groupId / query / topK
- success / failure
- chunk count
- durationMs
- retrievalMode
- 统一的 document chunk 列表

`WorkspaceContextAssembler` 不再直接调用 `DocumentService.searchDocumentChunks(...)`，而是通过 `RetrievalExecutor` 获取 `RetrievalExecutionResult` 后再进行 prompt 优先级排序和预算裁剪。

对应 roadmap：

- `4.3.3` 抽离 retrieval executor 接口

### 5.4 统一工具执行日志

- [ToolExecutionLog.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\ToolExecutionLog.java)
- [ToolExecutionType.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\ToolExecutionType.java)

SQL / Python / Retrieval 三类 executor 现在都会生成统一执行日志：

- toolType
- toolName
- stepType
- success
- message
- durationMs
- input summary
- output summary

`AnalysisResponse.executionLogs` 会返回当前分析请求实际执行过的工具步骤。benchmark report 的 `response_excerpt.executionLogs` 会记录 toolTypes、stepTypes、successes、durationsMs 和 count，并支持 `execution_log_tool_types_exact` 断言。

对应 roadmap：

- `4.3.4` 统一工具执行日志

---

## 6. Phase 4.4 起步

### 6.1 SQL 空集检查

- [AnalysisResultValidator.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisResultValidator.java)
- [AnalysisValidationReport.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\AnalysisValidationReport.java)
- [AnalysisValidationFinding.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\AnalysisValidationFinding.java)
- [ValidationSeverity.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\ValidationSeverity.java)

分析响应现在会返回 `validationReport`。当前第一条质量防线覆盖 SQL 空结果：

- SQL 执行成功但返回 0 行时生成 `SQL_EMPTY_RESULT`
- severity 为 `WARNING`
- summary 会追加 validation warning，避免空集被包装成确定业务结论
- benchmark report 的 `response_excerpt.validationReport` 会保留校验结果

对应 roadmap：

- `4.4.1` 增加 SQL 结果空集检查

### 6.2 Summary 与结果一致性检查

`AnalysisResultValidator` 现在增加了 summary consistency guard：

- 当结果集存在行，但 summary 明确声称 no data / no results / 无数据 / 无结果时，生成 `SUMMARY_RESULT_CONTRADICTION`
- severity 为 `WARNING`
- 多条 validation finding 可以合并到同一个 `validationReport`
- summary 会追加 validation warning，让前端和用户都能看到明显矛盾

当前实现只覆盖低误伤的明显矛盾；后续可继续扩展到数值一致性、top category 一致性和单位一致性检查。

对应 roadmap：

- `4.4.2` 增加 summary 与结果一致性检查

### 6.3 文档引用存在性检查

`AnalysisResultValidator` 现在增加了 document evidence reference guard：

- 当 `DocumentContext` 中存在检索到的 chunks，但 summary 没有清晰引用文档证据时，生成 `DOCUMENT_EVIDENCE_NOT_REFERENCED`
- 引用识别优先匹配文档名，也接受明确的 document / policy / note / rule / 根据 / 文档 / 规则 等证据引用信号
- severity 为 `WARNING`
- 适用于 hybrid 分析和 document-only 分流路径

当前实现只做存在性检查，不判断引用是否充分或逐句对应。后续可继续扩展为 citation coverage、引用片段定位和文档证据强度评分。

对应 roadmap：

- `4.4.3` 增加文档引用存在性检查

### 6.4 最终风险提示字段

- [RiskNotice.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\RiskNotice.java)
- [RiskNoticeBuilder.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\RiskNoticeBuilder.java)

分析响应现在会返回 `riskNotices`：

- 每条 notice 来源于 `validationReport.findings`
- 字段包含 code、severity、message、source
- 无风险时返回空列表
- benchmark report 的 `response_excerpt.riskNotices` 会保留该字段，方便对比每次 agent / prompt 改动后的风险变化

当前风险提示先聚合 4.4.1-4.4.3 的 validation findings；后续可加入 planner 风险、执行异常风险、模型置信度风险和数据质量风险。

对应 roadmap：

- `4.4.4` 增加最终风险提示字段

---

## 7. Phase 4.5 起步

### 7.1 统一 analysis report 模型

- [AnalysisReport.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\report\AnalysisReport.java)
- [AnalysisEvidenceSummary.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\report\AnalysisEvidenceSummary.java)
- [AnalysisReportBuilder.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisReportBuilder.java)

分析响应现在会返回 `analysisReport`，用于作为前端输出层的统一展示模型：

- summary
- data / rowCount
- recommendedChart
- generatedCodeOrSql
- evidence summary
- validationReport
- riskNotices
- executionLogs

第一版 report 不替换现有字段，而是与现有 `summary`、`data`、`executionLogs`、`validationReport` 并行返回，方便前端逐步接入证据来源区和推理过程摘要。benchmark report 的 `response_excerpt.analysisReport` 会保留 rowCount、chart、code 是否存在、证据摘要、risk notice 数量和 execution log 数量。

对应 roadmap：

- `4.5.1` 设计统一 analysis report 模型

### 7.2 前端展示证据来源区

- [types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts)
- [workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx)

工作区分析结果现在会读取 `analysisReport.evidence`，并在“本次结果”里展示独立的证据来源区：

- 数据表：展示本次可用的数据表数量、关系数量和表名
- 文档片段：展示检索策略、命中文档片段数量和文档名
- 无 report evidence 时保留空状态提示，兼容历史 artifact 和旧响应

同时前端结果展示会优先读取统一 `analysisReport` 中的 `summary`、`data` 和 `generatedCodeOrSql`，旧字段仍保留为 fallback，方便后端逐步收敛输出模型。

对应 roadmap：

- `4.5.2` 前端展示证据来源区

### 7.3 前端展示推理过程摘要

[workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx) 现在会读取统一 `analysisReport` 中的执行与质量字段，并在“本次结果”里展示“分析过程”区：

- `executionLogs`：按步骤展示本次使用过的 SQL、Python、文档检索等工具、步骤类型、成功状态和耗时
- `validationReport`：展示结果校验 warning / finding
- `riskNotices`：展示最终风险提示，并在无风险时给出明确空状态

该区域与 7.2 的“证据来源”区配合，前端用户可以同时看到“用了哪些资产”和“大致经过了哪些执行步骤”。旧响应或历史 artifact 未返回 report 字段时仍保留兼容空状态。

对应 roadmap：

- `4.5.3` 前端展示推理过程摘要

### 7.4 Phase 4.5 收尾核验

对照 [task-roadmap.md](F:\data-analysis-platform\docs\task-roadmap.md) 的 4.5 输出层验收标准，当前状态：

- `4.5.1` 统一 report：`analysisReport` 已包含数据结果、摘要、推荐图表、代码存在性、证据、校验报告、风险提示和执行日志
- `4.5.2` 证据来源区：前端工作区结果页已区分数据表证据和文档片段证据
- `4.5.3` 推理过程摘要：前端工作区结果页已展示执行步骤、工具类型、成功状态、耗时，以及 validation / risk 提示

本轮验证命令：

- `mvn -DskipTests compile`
- `mvn '-Dtest=AnalysisReportBuilderTest,RiskNoticeBuilderTest,AnalysisResultValidatorTest,ToolExecutionLogTest,SqlExecutionResultTest,PythonExecutionResultTest,RetrievalExecutionResultTest,UnifiedAnalysisContextTest,AnalysisRouterTest,RuleBasedAnalysisPlannerTest' test`
- `npm run lint`
- `npm run build`
- `python -m py_compile harness\run_benchmarks.py`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category workspace --case workspace_traffic_orders_join_signal`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category hybrid --case hybrid_workspace_orders_products_revenue_policy`

验证结果：

- 后端编译通过
- Phase 4 关键单测 31 条通过
- 前端 lint 和生产构建通过
- harness 脚本编译通过
- workspace benchmark 1/1 通过，报告：[workspace-run-20260503-110803.json](F:\data-analysis-platform\harness\runs\workspace-run-20260503-110803.json)
- hybrid benchmark 1/1 通过，报告：[hybrid-run-20260503-110802.json](F:\data-analysis-platform\harness\runs\hybrid-run-20260503-110802.json)

端到端人工核验记录：

- 当前前端服务：`http://localhost:3000/workspace`
- 当前后端服务：`http://127.0.0.1:18080/api`
- workspace benchmark 响应摘录包含 `executionLogs`、`validationReport`、`riskNotices` 和 `analysisReport.evidence`；报告中 `analysisReport.evidence.datasetCount=2`、`documentChunkCount=0`、`documentStrategy=SKIP`
- hybrid benchmark 响应摘录包含 `DOCUMENT_RETRIEVAL` 和 `SQL_EXECUTION` 两类执行日志；报告中 `analysisReport.evidence.datasetCount=2`、`documentChunkCount=2`，文档来源包含 `Revenue Recognition Policy` 和 `Workspace Analysis Note`
- 两条端到端路径的 `validationReport.passed=true`，`riskNotices=[]`

Phase 4.5 输出层可以进入 closeout。Phase 4 closeout 时建议把 4.1-4.5 的核心变更、验证命令、benchmark 报告路径和仍未覆盖的限制集中整理到单独 closeout 文档中。
