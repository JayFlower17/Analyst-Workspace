# Phase 4 Closeout

## 1. 阶段结论

Phase 4 已完成核心目标：

**分析过程已经从“单轮大 prompt + 工具调用”升级为有路由、有规划、有执行边界、有质量校验、有统一输出模型的可追踪分析工作流。**

当前 Phase 4 覆盖：

- 问题路由层：structured、document-only、hybrid、legacy dataset
- 分析规划层：按 route 输出结构化 plan 和 step types
- 执行层解耦：SQL、Python、retrieval executor 独立返回模型
- 工具执行日志：统一记录工具类型、步骤、成功状态和耗时
- 结果校验层：空结果、摘要矛盾、文档证据引用 warning
- 风险提示层：将 validation findings 汇总为 `riskNotices`
- 输出层：统一 `analysisReport`
- 前端展示：证据来源区和分析过程摘要区
- benchmark 输出：route、plan、execution logs、validation、risk、analysis report 摘要

---

## 2. 已完成任务

### 2.1 问题路由层

已新增：

- [AnalysisRouteType.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\route\AnalysisRouteType.java)
- [AnalysisRouteDecision.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\route\AnalysisRouteDecision.java)
- [AnalysisRouter.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisRouter.java)
- [AnalysisRouterTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\service\AnalysisRouterTest.java)

当前 route 类型：

- `STRUCTURED_QUERY`
- `DOCUMENT_EXPLANATION`
- `HYBRID_ANALYSIS`
- `LEGACY_DATASET`

`AnalysisResponse.routeDecision` 和 benchmark `response_excerpt.routeDecision` 已可追踪本次分析为何进入某个 route。

对应 roadmap：

- `4.1.1` 定义问题类型分类
- `4.1.2` 实现最小 intent router
- `4.1.3` 为 router 增加日志记录

### 2.2 分析规划层

已新增：

- [AnalysisPlan.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\plan\AnalysisPlan.java)
- [AnalysisPlanStep.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\plan\AnalysisPlanStep.java)
- [AnalysisPlanStepType.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\plan\AnalysisPlanStepType.java)
- [AnalysisPlanner.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisPlanner.java)
- [RuleBasedAnalysisPlanner.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\RuleBasedAnalysisPlanner.java)
- [RuleBasedAnalysisPlannerTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\service\RuleBasedAnalysisPlannerTest.java)

planner 会基于 route 输出结构化步骤：

- structured：route / build context / generate SQL / execute SQL / summarize
- hybrid：route / build context / retrieve documents / generate SQL / execute SQL / summarize
- document-only：route / build context / retrieve documents / answer from documents / summarize
- legacy：兼容旧 dataset 分析入口

benchmark 支持 `analysis_plan_step_types_exact` 断言。

对应 roadmap：

- `4.2.1` 抽离 analysis planner 接口
- `4.2.2` 实现结构化分析 planner
- `4.2.3` 实现联合分析 planner
- `4.2.4` 限制 planner 输出结构

### 2.3 执行层解耦

已新增：

- [SqlExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\SqlExecutor.java)
- [DuckDbSqlExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\DuckDbSqlExecutor.java)
- [SqlExecutionResult.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\SqlExecutionResult.java)
- [PythonExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\PythonExecutor.java)
- [HttpPythonExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\HttpPythonExecutor.java)
- [PythonExecutionResult.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\PythonExecutionResult.java)
- [RetrievalExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\RetrievalExecutor.java)
- [DocumentRetrievalExecutor.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\DocumentRetrievalExecutor.java)
- [RetrievalExecutionResult.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\RetrievalExecutionResult.java)
- [ToolExecutionLog.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\ToolExecutionLog.java)
- [ToolExecutionType.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\execution\ToolExecutionType.java)

`AnalysisService` 不再直接承担 SQL / Python / retrieval 执行细节，而是通过 executor 获取统一结果和 execution log。`WorkspaceContextAssembler` 也改为通过 retrieval executor 获取文档检索结果。

对应 roadmap：

- `4.3.1` 抽离 SQL executor 接口
- `4.3.2` 抽离 Python executor 接口
- `4.3.3` 抽离 retrieval executor 接口
- `4.3.4` 统一工具执行日志

### 2.4 校验层和风险提示

已新增：

- [AnalysisResultValidator.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisResultValidator.java)
- [AnalysisValidationReport.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\AnalysisValidationReport.java)
- [AnalysisValidationFinding.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\AnalysisValidationFinding.java)
- [ValidationSeverity.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\ValidationSeverity.java)
- [RiskNotice.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\validation\RiskNotice.java)
- [RiskNoticeBuilder.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\RiskNoticeBuilder.java)

当前质量防线：

- SQL 执行成功但返回 0 行时生成 `SQL_EMPTY_RESULT`
- summary 明显声称无数据但结果集有行时生成 `SUMMARY_RESULT_CONTRADICTION`
- hybrid / document-only 有文档 chunks 但 summary 未引用文档证据时生成 `DOCUMENT_EVIDENCE_NOT_REFERENCED`
- validation findings 会汇总为 `riskNotices`

对应 roadmap：

- `4.4.1` 增加 SQL 结果空集检查
- `4.4.2` 增加 summary 与结果一致性检查
- `4.4.3` 文档引用存在性检查
- `4.4.4` 最终风险提示字段

### 2.5 输出层和前端展示

已新增：

- [AnalysisReport.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\report\AnalysisReport.java)
- [AnalysisEvidenceSummary.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\report\AnalysisEvidenceSummary.java)
- [AnalysisReportBuilder.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisReportBuilder.java)
- [types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts)
- [workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx)

`AnalysisResponse.analysisReport` 现在可包含：

- summary
- data / rowCount
- recommendedChart
- generatedCodeOrSql
- evidence summary
- validationReport
- riskNotices
- executionLogs

前端工作区结果页现在展示：

- 证据来源区：区分数据表、关系、文档片段和文档名
- 分析过程区：展示工具步骤、工具类型、成功状态、耗时、validation 和 risk 提示

对应 roadmap：

- `4.5.1` 设计统一 analysis report 模型
- `4.5.2` 前端展示证据来源区
- `4.5.3` 前端展示推理过程摘要

---

## 3. 验证记录

Phase 4 closeout 执行：

```powershell
mvn -DskipTests compile
mvn '-Dtest=AnalysisReportBuilderTest,RiskNoticeBuilderTest,AnalysisResultValidatorTest,ToolExecutionLogTest,SqlExecutionResultTest,PythonExecutionResultTest,RetrievalExecutionResultTest,UnifiedAnalysisContextTest,AnalysisRouterTest,RuleBasedAnalysisPlannerTest' test
npm run lint
npm run build
python -m py_compile harness\run_benchmarks.py
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category workspace --case workspace_traffic_orders_join_signal
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category hybrid --case hybrid_workspace_orders_products_revenue_policy
git diff --check
```

结果：

- backend compile：通过
- Phase 4 关键单测：31 个测试通过
- frontend lint：通过
- frontend build：通过
- harness py_compile：通过
- workspace benchmark smoke：1 / 1 通过
- hybrid benchmark smoke：1 / 1 通过
- diff whitespace check：通过

真实报告：

- [workspace-run-20260503-110803.json](F:\data-analysis-platform\harness\runs\workspace-run-20260503-110803.json)
- [hybrid-run-20260503-110802.json](F:\data-analysis-platform\harness\runs\hybrid-run-20260503-110802.json)

报告字段确认：

- workspace smoke 包含 `executionLogs`、`validationReport`、`riskNotices`、`analysisReport.evidence`
- workspace smoke 中 `analysisReport.evidence.datasetCount=2`、`documentChunkCount=0`、`documentStrategy=SKIP`
- hybrid smoke 包含 `DOCUMENT_RETRIEVAL` 和 `SQL_EXECUTION` 两类 execution log
- hybrid smoke 中 `analysisReport.evidence.datasetCount=2`、`documentChunkCount=2`
- hybrid smoke 文档来源包含 `Revenue Recognition Policy` 和 `Workspace Analysis Note`
- 两条 smoke 的 `validationReport.passed=true`，`riskNotices=[]`

---

## 4. 运行状态

closeout 时本地服务状态：

- frontend：`http://localhost:3000/workspace`
- backend API：`http://127.0.0.1:18080/api`

注意：`harness/run_benchmarks.py` 默认 `--base-url` 仍是 `http://127.0.0.1:8080/api`。当前本地后端运行在 `18080` 时，需要显式传入：

```powershell
--base-url http://127.0.0.1:18080/api
```

---

## 5. 已知边界

- router / planner 仍是规则实现，不是 LangGraph 状态机，也不是 LLM-driven planner。
- document-only 和 hybrid route 的识别依赖关键词信号，复杂意图仍可能需要更强分类器。
- summary consistency guard 只覆盖明显矛盾，不做完整数值一致性、单位一致性或逐项事实校验。
- 文档引用检查目前只做存在性检查，不做 citation coverage、片段定位或证据强度评分。
- `analysisReport` 已作为统一输出模型返回，但旧字段仍并行存在，前端也保留 fallback；后续可逐步收敛。
- artifact 持久化还没有完整保存 evidence source、execution logs、risk notices；这属于 Phase 6 的持久化能力。
- benchmark 仍依赖本地测试用户、`groupId=1` 和 stage2 数据集 / 文档资源。
- 前端展示是过程摘要，不是完整 debug trace；更详细的 trace 可在 Phase 5/6 通过 harness report 和 artifact history 承载。

---

## 6. Phase 5 入口建议

建议进入 Phase 5：建设 Harness 工程。

优先任务：

1. 整理 benchmark case registry，让 structured / workspace / hybrid case 可按标签、route、executor 类型筛选。
2. 固化 Phase 4 的断言能力：route decision、plan steps、execution logs、validation report、risk notices、analysis report。
3. 增加 benchmark report 对比能力，方便观察 prompt / planner / executor 调整前后的行为差异。
4. 将当前手工 smoke 命令收敛为一条 Phase 4 regression suite。
5. 处理 `--base-url` 默认端口和本地 dev 端口不一致的问题，减少误跑失败。

---

## 7. 参考记录

- [Phase 4 Kickoff](F:\data-analysis-platform\docs\phase4-kickoff.md)
- [Task Roadmap](F:\data-analysis-platform\docs\task-roadmap.md)
- [Phase 3 Closeout](F:\data-analysis-platform\docs\phase3-closeout.md)
