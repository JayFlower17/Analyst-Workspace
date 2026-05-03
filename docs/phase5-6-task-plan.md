# Phase 5 + Phase 6 Task Plan

## 1. 总体拆分

Phase 5 和 Phase 6 合并拆成 15 个小任务推进：

- Phase 5：8 个任务，目标是把 harness 从“可运行脚本”升级为可持续回归评测系统
- Phase 6：7 个任务，目标是把分析结果、证据和过程沉淀为可回看的 workspace 分析资产

执行原则：

- 先 Phase 5，后 Phase 6
- 每个任务都要有可验证命令
- 每个任务完成后更新项目构建文档
- 优先不破坏当前 `analysisReport` / artifact 兼容性

---

## 2. Phase 5：Harness 工程

### P5-01 Case metadata 与标签筛选

目标：

- benchmark case 增加稳定 `tags`
- runner 支持按 tag 选择 case
- list 和 report 输出 case metadata

验收：

- `--list-cases` 可展示 tags
- `--tag phase4-regression` 可筛选已有 Phase 4 回归 case
- report 中包含 `selected_tags`、case `tags` 和 description

### P5-02 Case registry 与格式说明

目标：

- 建立 case registry 文档或 JSON
- 统一说明 category、tags、setup、request、expectations 字段
- 标注每个 case 覆盖的 route / executor / source 类型

验收：

- 新成员可以通过 registry 快速知道每个 case 覆盖什么

### P5-03 Phase 4 regression suite

目标：

- 固化一组最小 Phase 4 回归 case
- 覆盖 structured、workspace、hybrid、document-only

验收：

- 一条命令能跑 Phase 4 smoke/regression subset

### P5-04 断言能力补齐

目标：

- 将 Phase 4 输出字段变成稳定断言能力
- 覆盖 route、plan、execution logs、validation、risk、analysis report

验收：

- case 可以显式断言 `analysisReport.evidence`、`riskNotices`、`validationReport`

### P5-05 失败归因字段

目标：

- 将失败原因分类为 retrieval / route / plan / execution / validation / summary / report / api

验收：

- report 中失败 case 有 `failure_stage`

### P5-06 报告摘要增强

目标：

- report 顶层输出按 category、tag、failure stage 聚合的统计

验收：

- 不打开每个 case 也能看到哪里退化

### P5-07 Report diff 对比

目标：

- 增加两个 run report 的对比脚本或命令
- 对比 pass/fail、耗时、route、plan、risk、evidence 摘要

验收：

- 改 prompt / planner 后可快速看前后差异

### P5-08 Harness 使用文档与 closeout

目标：

- 整理 Phase 5 的使用手册和 closeout
- 记录常用命令、本地服务依赖、已知边界

验收：

- Phase 5 可以作为后续 Phase 6 的回归安全网

---

## 3. Phase 6：结果沉淀与平台化

### P6-01 Artifact schema 扩展设计

目标：

- 设计 artifact 对 `analysisReport`、evidence、execution logs、risk notices 的持久化字段
- 明确旧 artifact 兼容策略

验收：

- 有 schema / migration / DTO 设计记录

### P6-02 持久化 analysis report

目标：

- 后端保存完整或裁剪后的 `analysisReport`
- artifact API 返回该 report

验收：

- 新分析生成的 artifact 可回读 report

### P6-03 持久化证据来源

目标：

- 可回看本次用了哪些表、关系、文档和文档片段

验收：

- artifact detail 能展示 evidence summary

### P6-04 持久化执行过程与风险

目标：

- 保存 execution logs、validation report、risk notices

验收：

- artifact detail 能展示本次执行步骤和风险提示

### P6-05 Artifact 详情视图

目标：

- 前端增加 artifact detail modal 或详情页
- 展示摘要、结果预览、代码、证据、过程、风险

验收：

- 用户可以从历史记录打开完整分析回放

### P6-06 Workspace artifact 管理

目标：

- 增强 workspace 内 artifact 列表
- 支持按 workspace 筛选、查看、删除或归档

验收：

- 历史分析记录可管理

### P6-07 Phase 6 回归与 closeout

目标：

- 用 Phase 5 harness 回归 Phase 6 改动
- 整理 Phase 6 closeout 文档

验收：

- artifact 持久化与前端回看能力有稳定验证记录

---

## 4. 当前进度

- `P5-01`：已完成

完成内容：

- 所有现有 benchmark case 增加非空 `tags`
- `harness/run_benchmarks.py` 增加 `--tag` 筛选，重复传入时要求 case 同时包含所有 tag
- `--list-cases` 输出 tags
- report 顶层输出 `selected_tags`
- report 中每个 result 输出 case `tags` 和 `description`
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加 tags 字段和 `--tag` 用法说明

验证记录：

- `python -m py_compile harness\run_benchmarks.py`
- `python harness\run_benchmarks.py --category all --tag phase4-regression --list-cases`
- `python harness\run_benchmarks.py --category hybrid --tag policy --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category all --tag phase4-regression --limit 1`
- `git diff --check`

验证结果：

- `phase4-regression` 筛出 5 个回归 case
- `policy` 筛出 2 个 hybrid policy case
- 最小 tagged benchmark 1 / 1 通过，报告：[all-run-20260503-165146.json](F:\data-analysis-platform\harness\runs\all-run-20260503-165146.json)
- 报告确认包含 `selected_tags`、case `tags` 和 `description`

### P5-02 完成记录

- `P5-02`：已完成

完成内容：

- 新增 [case-registry.json](F:\data-analysis-platform\harness\benchmarks\case-registry.json)，登记 9 个现有 benchmark case
- 新增 [benchmarks/README.md](F:\data-analysis-platform\harness\benchmarks\README.md)，说明 case 格式、tag taxonomy、覆盖矩阵和常用命令
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 链接 registry 和覆盖矩阵
- registry 标注每个 case 覆盖的 route、executors、sources、assertions 和 tags

验证记录：

- `Get-Content harness\benchmarks\case-registry.json | ConvertFrom-Json`
- registry 中 9 个 case path 全部存在
- `python -m py_compile harness\run_benchmarks.py`
- `python harness\run_benchmarks.py --category all --tag phase4-regression --list-cases`

验证结果：

- registry JSON 可解析
- `phase4-regression` 仍可筛出 5 个回归 case

### P5-03 完成记录

- `P5-03`：已完成

完成内容：

- 新增 [phase4-regression.json](F:\data-analysis-platform\harness\suites\phase4-regression.json)，固化 Phase 4 最小回归套件
- 新增 [suites/README.md](F:\data-analysis-platform\harness\suites\README.md)，说明 suite 格式、当前 suite 和运行命令
- `harness/run_benchmarks.py` 增加 `--suite` 参数，可通过 `harness/suites/<name>.json` 加载固定 case 集合
- suite report 文件名使用 suite id，例如 `phase4-regression-run-*.json`
- report 顶层增加 `suite` 和 `suite_description`
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加 suite 目录和 `--suite phase4-regression` 用法

覆盖范围：

- route：`STRUCTURED_QUERY`、`HYBRID_ANALYSIS`、`DOCUMENT_EXPLANATION`
- executor：`SQL_EXECUTION`、`DOCUMENT_RETRIEVAL`
- source：workspace schema、workspace metadata、document chunks
- assertions：context summary、route decision、plan steps、execution logs、document-only SQL absence

验证记录：

- `python -m py_compile harness\run_benchmarks.py`
- `Get-Content harness\suites\phase4-regression.json | ConvertFrom-Json`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression`
- `git diff --check`

验证结果：

- `--suite phase4-regression --list-cases` 可列出 5 个固定回归 case
- Phase 4 regression suite 5 / 5 通过，报告：[phase4-regression-run-20260503-191840.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-191840.json)
- 报告确认包含 `suite=phase4-regression`、`suite_description`、`selected_tags=["phase4-regression"]`

### P5-04 完成记录

- `P5-04`：已完成

完成内容：

- `harness/run_benchmarks.py` 增加 `analysis_report_evidence_exact`
- `harness/run_benchmarks.py` 增加 `analysis_report_evidence_min`
- `harness/run_benchmarks.py` 增加 `validation_report_exact`
- `harness/run_benchmarks.py` 增加 `risk_notices_exact`
- 5 个 Phase 4 regression case 补充 `analysisReport.evidence`、`validationReport`、`riskNotices` 显式断言
- [case-registry.json](F:\data-analysis-platform\harness\benchmarks\case-registry.json) 更新断言覆盖字段
- [benchmarks/README.md](F:\data-analysis-platform\harness\benchmarks\README.md) 增加 Phase 4 输出断言说明
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加新增断言字段说明
- [phase4-regression.json](F:\data-analysis-platform\harness\suites\phase4-regression.json) 更新 suite assertion coverage

新增断言能力：

- `analysis_report_evidence_exact`：精确匹配 `analysisReport.evidence` 字段
- `analysis_report_evidence_min`：对 `analysisReport.evidence` 数值字段做下限检查
- `validation_report_exact`：检查 `validationReport.passed`、`findingCount`、`findingCodes`
- `risk_notices_exact`：检查 `riskNotices.count`、`codes`

验证记录：

- `python -m py_compile harness\run_benchmarks.py`
- `Get-Content harness\benchmarks\case-registry.json | ConvertFrom-Json`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression`
- `git diff --check`

验证结果：

- Phase 4 regression suite 在新增断言下 5 / 5 通过，报告：[phase4-regression-run-20260503-193032.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-193032.json)
- 报告确认包含 `analysisReport.evidence`、`validationReport`、`riskNotices`

### P5-05 完成记录

- `P5-05`：已完成

完成内容：

- `CaseResult` 增加 `failure_stage`
- `build_case_result(...)` 支持写入失败阶段
- `print_summary(...)` 在失败摘要里显示 `[failure_stage]`
- report 中每个 result 都包含 `failure_stage`，成功 case 为 `null`
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加 failure stage 字段和阶段枚举说明

当前阶段枚举：

- `api`
- `result`
- `summary`
- `context`
- `retrieval`
- `route`
- `plan`
- `execution`
- `report`
- `validation`
- `risk`
- `cleanup`

阶段映射：

- API 失败、请求异常、`success=false`：`api`
- 行数 / 列名断言失败：`result`
- summary 文案断言失败：`summary`
- context summary 非文档字段失败：`context`
- document context / retrieval 相关 summary 字段失败：`retrieval`
- route decision 断言失败：`route`
- analysis plan 断言失败：`plan`
- generated SQL absence / execution logs 断言失败：`execution`
- analysis report evidence 断言失败：`report`
- validation report 断言失败：`validation`
- risk notices 断言失败：`risk`
- 主流程成功但清理临时资源失败：`cleanup`

验证记录：

- `python -m py_compile harness\run_benchmarks.py`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression`
- synthetic `build_case_result(...)` 验证失败 case 可写入 `failure_stage=route`
- `git diff --check`

验证结果：

- Phase 4 regression suite 5 / 5 通过，报告：[phase4-regression-run-20260503-194022.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-194022.json)
- 报告确认成功 case 包含 `failure_stage: null`
- synthetic 失败结果确认 `failure_stage=route`

### P5-06 完成记录

- `P5-06`：已完成

完成内容：

- `harness/run_benchmarks.py` 增加 `build_report_summary(...)`
- report 顶层新增 `summary.byCategory`
- report 顶层新增 `summary.byTag`
- report 顶层新增 `summary.byFailureStage`
- 每个聚合 bucket 都包含 `total`、`passed`、`failed`
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加顶层 summary 说明

聚合规则：

- `byCategory`：按 case category 聚合
- `byTag`：按 case tags 聚合，一个 case 可贡献到多个 tag bucket
- `byFailureStage`：只统计带 `failure_stage` 的结果，成功且无清理问题的 case 不进入该 bucket

验证记录：

- `python -m py_compile harness\run_benchmarks.py`
- synthetic `build_report_summary(...)` 验证 category / tag / failure_stage 聚合
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression`
- `git diff --check`

验证结果：

- synthetic summary 正确输出 `byCategory`、`byTag`、`byFailureStage`
- Phase 4 regression suite 5 / 5 通过，报告：[phase4-regression-run-20260503-194831.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-194831.json)
- 报告头部确认包含 `summary.byCategory`、`summary.byTag`、`summary.byFailureStage`

### P5-07 完成记录

- `P5-07`：已完成

完成内容：

- 新增 [harness/compare_reports.py](F:\data-analysis-platform\harness\compare_reports.py)
- 支持输入 baseline / current 两个 benchmark report JSON
- 支持文本输出和 `--json` 机器可读输出
- 对比 report 总体通过 / 失败数量
- 对比 `summary.byCategory`、`summary.byTag`、`summary.byFailureStage`
- 对比 case 增删、pass/fail 状态、失败原因、失败阶段、HTTP 状态和耗时变化
- 对比关键 response excerpt：`routeDecision.route`、`analysisPlan.stepTypes`、`executionLogs.toolTypes`、`validationReport.passed`、`analysisReport.evidence`、risk notice 数量
- 老报告没有顶层 `summary` 时，会从 `results` 回算聚合摘要
- 出现 pass/fail 回归时退出码为 `2`，无回归时退出码为 `0`
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加 report diff 使用说明

验证记录：

- `python -m py_compile harness\compare_reports.py`
- `python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json`
- `python harness\compare_reports.py --json harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json`
- `git diff --check`

验证结果：

- compare 文本输出确认两个 Phase 4 regression 报告均为 5 / 5 通过
- 未检测到 pass/fail regression
- case 级别仅报告耗时变化，关键 response excerpt 字段未变化

### P5-08 完成记录

- `P5-08`：已完成
- Phase 5：已收尾，可作为 Phase 6 的默认回归安全网

完成内容：

- 新增 [phase5-closeout.md](F:\data-analysis-platform\docs\phase5-closeout.md)
- [phase5-closeout.md](F:\data-analysis-platform\docs\phase5-closeout.md) 收束 P5-01 到 P5-08 完成范围
- 记录 Phase 5 常用工作流：启动后端、查看 suite、运行 regression、对比 report、定位失败
- 记录最近验证报告：[phase4-regression-run-20260503-194831.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-194831.json)
- 记录已知限制：本地 workspace 依赖、LLM 文案和耗时波动、尚未接入正式 CI、报告目录需要挑选基线
- 记录 Phase 6 交接建议：每次 artifact/output-layer 改动前后使用 Phase 5 harness 做 baseline/current 对比
- [harness/README.md](F:\data-analysis-platform\harness\README.md) 增加 Phase 5 regression workflow
- [docs/README.md](F:\data-analysis-platform\docs\README.md) 增加 Phase 5 closeout 文档入口

验证记录：

- `python -m py_compile harness\run_benchmarks.py`
- `python -m py_compile harness\compare_reports.py`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-193032.json harness\runs\phase4-regression-run-20260503-194831.json`
- `git diff --check`

验证结果：

- `run_benchmarks.py` 和 `compare_reports.py` 均可编译
- `phase4-regression` suite 可列出 5 个固定 case
- report diff 未检测到 pass/fail regression
- 文档补丁无空白错误

### P6-01 完成记录

- `P6-01`：已完成

完成内容：

- 新增 [phase6-artifact-schema-design.md](F:\data-analysis-platform\docs\phase6-artifact-schema-design.md)
- 梳理当前 `analysis_artifacts` 现有字段和保存/回读路径
- 确认当前 Phase 4 输出只存在于即时 `AnalysisResponse`，还没有持久化到 artifact
- 设计 Phase 6 artifact 扩展字段：
  - `artifact_schema_version`
  - `analysis_report_json`
  - `evidence_summary_json`
  - `execution_logs_json`
  - `validation_report_json`
  - `risk_notices_json`
  - `context_summary_json`
  - `route_decision_json`
  - `analysis_plan_json`
- 明确 `analysis_report_json` 保存裁剪后的报告，不重复保存完整 `data`，结果预览继续使用 `result_preview_json`
- 明确 DuckDB 迁移策略：新库更新 `CREATE TABLE`，旧库通过 `information_schema.columns` + `ALTER TABLE` 兼容补列
- 明确旧 artifact 兼容策略：新增字段 nullable，旧记录仍可列表/回读，`reportAvailable=false`
- 明确后端实体、API detail DTO、前端类型和 Phase 6 后续任务映射
- [docs/README.md](F:\data-analysis-platform\docs\README.md) 增加 Phase 6 schema 设计文档入口

验证记录：

- 读取 [AnalysisArtifact.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\AnalysisArtifact.java)
- 读取 [DuckDBRepository.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\repository\DuckDBRepository.java) 中 `analysis_artifacts` 建表、保存和映射逻辑
- 读取 [AnalysisResponse.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\dto\AnalysisResponse.java)
- 读取 `AnalysisReport`、`AnalysisEvidenceSummary`、`ToolExecutionLog`、`AnalysisValidationReport`、`RiskNotice`
- 读取 [frontend-next/src/lib/types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts)
- `mvn -f backend\pom.xml -DskipTests compile`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `git diff --check`

验证结果：

- 后端编译通过；根目录没有 `pom.xml`，后续文档统一使用 `backend\pom.xml`
- Phase 4 regression suite 仍可列出 5 个固定 case
- P6-01 是设计文档任务，未改动运行时代码
- 文档补丁无空白错误

### P6-02 完成记录

- `P6-02`：已完成

完成内容：

- [AnalysisArtifact.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\AnalysisArtifact.java) 增加：
  - `artifactSchemaVersion`
  - `analysisReportJson`
- [DuckDBRepository.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\repository\DuckDBRepository.java) 更新：
  - 新库 `analysis_artifacts` 建表包含 `artifact_schema_version` 和 `analysis_report_json`
  - 旧库通过 `ensureAnalysisArtifactPhase6Columns(...)` 兼容补列
  - `saveAnalysisArtifact(...)` 写入 schema version 和 trimmed report JSON
  - 新增 `findAnalysisArtifactById(...)` 支持 detail 回读
  - `mapAnalysisArtifact(...)` 回填新增字段
- [AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java) 更新：
  - artifact 保存路径接收 `AnalysisReport`
  - 保存 `artifact_schema_version=2`
  - 保存 trimmed `analysisReport`，其中 `data=[]`，不重复持久化完整结果行
  - 结果预览继续使用 `result_preview_json`
- 新增 [ArtifactDetailResponse.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\dto\ArtifactDetailResponse.java)
- [ArtifactService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\ArtifactService.java) 新增 detail 回读组装逻辑：
  - 解析 `resultPreviewJson`
  - 解析 `analysisReportJson`
  - 旧 artifact 返回 `reportAvailable=false`
- [ArtifactController.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\controller\ArtifactController.java) 新增：
  - `GET /api/artifacts/{id}`
- [frontend-next/src/lib/types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts) 增加 artifact schema/version/report 类型字段和 `ArtifactDetail`
- 新增 [ArtifactServiceTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\service\ArtifactServiceTest.java)
- 新增 [DuckDBRepositoryArtifactTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\repository\DuckDBRepositoryArtifactTest.java)
- [phase6-artifact-schema-design.md](F:\data-analysis-platform\docs\phase6-artifact-schema-design.md) 增加 P6-02 implementation checkpoint

验证记录：

- `mvn -f backend\pom.xml -DskipTests compile`
- `mvn -f backend\pom.xml -Dtest=ArtifactServiceTest test`
- `mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test`
- `npm run lint`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression --limit 1`
- `git diff --check`

验证结果：

- 后端编译通过
- `ArtifactServiceTest` + `DuckDBRepositoryArtifactTest` 3 / 3 通过，覆盖新 report 回读、legacy artifact 兼容、DuckDB 新列保存/按 id 回读
- 前端 lint 通过
- Phase 4 regression suite 仍可列出 5 个固定 case
- 最小 Phase 4 regression benchmark 1 / 1 通过，报告：[phase4-regression-run-20260503-201411.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-201411.json)
- 18080 上的运行中服务可跑 benchmark，但新增 detail API 需要重启后端进程后才能做完整 API 手工验证；本轮通过 repository/service 测试覆盖新代码链路

### P6-03 完成记录

- `P6-03`：已完成

完成内容：

- [AnalysisArtifact.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\AnalysisArtifact.java) 增加 `evidenceSummaryJson`
- [DuckDBRepository.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\repository\DuckDBRepository.java) 更新：
  - 新库 `analysis_artifacts` 建表包含 `evidence_summary_json`
  - 旧库通过 `ensureAnalysisArtifactPhase6Columns(...)` 兼容补列
  - `saveAnalysisArtifact(...)` 写入 evidence summary JSON
  - `mapAnalysisArtifact(...)` 回填 evidence summary JSON
- [AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java) 保存 `analysisReport.evidence()` 到独立 `evidence_summary_json`
- [ArtifactDetailResponse.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\dto\ArtifactDetailResponse.java) 增加 parsed `evidence`
- [ArtifactService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\ArtifactService.java) detail 回读时解析 `evidenceSummaryJson`
- evidence detail 有兼容 fallback：旧行缺少 `evidence_summary_json` 时，仍可从 `analysisReport.evidence` 回退
- [frontend-next/src/lib/types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts) 增加 `evidenceSummaryJson` 和 `ArtifactDetail.evidence`
- [ArtifactServiceTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\service\ArtifactServiceTest.java) 增加 parsed evidence 断言
- [DuckDBRepositoryArtifactTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\repository\DuckDBRepositoryArtifactTest.java) 增加 evidence summary 保存/回读断言
- [DuckDBRepositoryArtifactTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\repository\DuckDBRepositoryArtifactTest.java) 增加 legacy `analysis_artifacts` 表补列迁移断言
- [phase6-artifact-schema-design.md](F:\data-analysis-platform\docs\phase6-artifact-schema-design.md) 增加 P6-03 implementation checkpoint

验证记录：

- `mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test`
- `mvn -f backend\pom.xml -DskipTests compile`
- `npm run lint`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `git diff --check`

验证结果：

- `ArtifactServiceTest` + `DuckDBRepositoryArtifactTest` 4 / 4 通过，覆盖 evidence 独立保存/回读、parsed detail evidence、legacy artifact 兼容、旧表补列迁移
- 后端编译通过
- 前端 lint 通过
- Phase 4 regression suite 仍可列出 5 个固定 case
- 本轮没有重启 18080 后端进程做新 detail API 手工验证；P6-03 新代码链路由 repository/service 测试覆盖

### P6-04 完成记录

- `P6-04`：已完成

完成内容：

- [AnalysisArtifact.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\AnalysisArtifact.java) 增加：
  - `executionLogsJson`
  - `validationReportJson`
  - `riskNoticesJson`
- [DuckDBRepository.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\repository\DuckDBRepository.java) 更新：
  - 新库 `analysis_artifacts` 建表包含 `execution_logs_json`
  - 新库 `analysis_artifacts` 建表包含 `validation_report_json`
  - 新库 `analysis_artifacts` 建表包含 `risk_notices_json`
  - 旧库通过 `ensureAnalysisArtifactPhase6Columns(...)` 兼容补列
  - `saveAnalysisArtifact(...)` 写入 execution / validation / risk 三组 JSON
  - `mapAnalysisArtifact(...)` 回填 execution / validation / risk 三组 JSON
- [AnalysisService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\AnalysisService.java) 保存：
  - `analysisReport.executionLogs()`
  - `analysisReport.validationReport()`
  - `analysisReport.riskNotices()`
- [ArtifactDetailResponse.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\dto\ArtifactDetailResponse.java) 增加 parsed fields：
  - `executionLogs`
  - `validationReport`
  - `riskNotices`
- [ArtifactService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\ArtifactService.java) detail 回读时解析三组独立 JSON
- detail 兼容 fallback：旧行缺少独立字段时，可从 `analysisReport.executionLogs`、`analysisReport.validationReport`、`analysisReport.riskNotices` 回退
- [frontend-next/src/lib/types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts) 增加 raw JSON 字段和 `ArtifactDetail` parsed fields
- [ArtifactServiceTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\service\ArtifactServiceTest.java) 增加 execution / validation / risk parsed detail 断言
- [DuckDBRepositoryArtifactTest.java](F:\data-analysis-platform\backend\src\test\java\com\analysis\repository\DuckDBRepositoryArtifactTest.java) 增加 execution / validation / risk 保存/回读和旧表补列迁移断言
- [phase6-artifact-schema-design.md](F:\data-analysis-platform\docs\phase6-artifact-schema-design.md) 增加 P6-04 implementation checkpoint

验证记录：

- `mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test`
- `mvn -f backend\pom.xml -DskipTests compile`
- `npm run lint`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `git diff --check`

验证结果：

- `ArtifactServiceTest` + `DuckDBRepositoryArtifactTest` 4 / 4 通过，覆盖 execution / validation / risk 独立保存/回读、parsed detail、legacy artifact 兼容、旧表补列迁移
- 后端编译通过
- 前端 lint 通过
- Phase 4 regression suite 仍可列出 5 个固定 case
- 本轮没有重启 18080 后端进程做新 detail API 手工验证；P6-04 新代码链路由 repository/service 测试覆盖

### P6-05 完成记录

- `P6-05`：已完成

完成内容：

- [frontend-next/src/lib/api/client.ts](F:\data-analysis-platform\frontend-next\src\lib\api\client.ts) 增加 `artifactApi.detail(id)`
- [frontend-next/src/app/(dashboard)/workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx) 增加 artifact detail modal
- 历史结果卡片点击后打开详情，不再只把摘要加载到当前结果区
- 详情弹窗展示：
  - 原始查询
  - 摘要
  - result preview 表格
  - parsed `evidence`
  - parsed `executionLogs`
  - parsed `validationReport`
  - parsed `riskNotices`
  - generated SQL / Python
- 详情弹窗支持 legacy artifact：
  - 列表中的旧记录可先用 `resultPreviewJson` 和摘要渲染
  - detail API 加载失败时保留列表态 fallback，并弹出 toast
- 详情弹窗提供“加载到本次结果”操作，可把历史 artifact detail 回填到当前结果区
- 历史结果卡片显示 schema version / legacy 状态

验证记录：

- `npm run lint`
- `npm run build`
- `mvn -f backend\pom.xml -DskipTests compile`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `git diff --check`

验证结果：

- 前端 lint 通过
- 前端 production build 通过
- 后端编译通过
- Phase 4 regression suite 仍可列出 5 个固定 case
- 本轮没有重启前后端服务做浏览器手工验证；下一轮如继续 P6-06 前，建议重启 backend + frontend-next 后打开 `/workspace` 做一次端到端点击检查

### P6-06 完成记录

- `P6-06`：已完成

完成内容：

- [frontend-next/src/app/(dashboard)/workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx) 增强 workspace artifact 管理入口：
  - 历史结果列表增加手动刷新
  - 最近 artifact 拉取数量从 8 提升到 12
  - 支持按 query、summary、generated code、chart type、created time 和 schema label 搜索
  - 支持 `全部` / `Phase 6` / `Legacy` 筛选
  - 历史结果卡片增加明确的“查看详情”入口
  - “工作区资产 > 分析结果”复用同一套搜索、筛选、刷新和详情入口
- 新增 [phase6-artifact-management-design.md](F:\data-analysis-platform\docs\phase6-artifact-management-design.md)：
  - 记录当前 P6-06 已实现的列表管理能力
  - 设计后续 `artifact_status`、`archived_at`、`deleted_at`、`updated_at` 字段
  - 设计后续 archive / restore / soft delete API
  - 记录后续接口实现后的验证点
- [docs/README.md](F:\data-analysis-platform\docs\README.md) 增加 artifact 管理设计文档入口

验证记录：

- `npm run lint`
- `npm run build`
- `mvn -f backend\pom.xml -DskipTests compile`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `git diff --check`

验证结果：

- 前端 lint 通过
- 前端 production build 通过
- 后端编译通过
- Phase 4 regression suite 仍可列出 5 个固定 case
- `git diff --check` 通过
- 本轮没有实现归档/删除接口；接口字段、状态语义和验证点已在 P6-06 管理设计文档中收束，适合作为 P6-07 后或下一阶段的小任务继续实现

### P6-07 完成记录

- `P6-07`：已完成

完成内容：

- 运行 Phase 6 artifact 持久化与回读相关测试
- 运行 Phase 5 harness 的 `phase4-regression` suite
- 对比 baseline/current benchmark report，确认没有 pass/fail regression
- 新增 [phase6-closeout.md](F:\data-analysis-platform\docs\phase6-closeout.md)，收束：
  - P6-01 到 P6-06 完成范围
  - 当前用户可用能力
  - 工程侧新增能力
  - P6-07 验证命令和 benchmark 报告
  - 当前已知限制
  - 后续建议
- [docs/README.md](F:\data-analysis-platform\docs\README.md) 增加 Phase 6 closeout 文档入口

验证记录：

- `mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test`
- `npm run lint`
- `npm run build`
- `mvn -f backend\pom.xml -DskipTests compile`
- `python harness\run_benchmarks.py --suite phase4-regression --list-cases`
- `python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression`
- `python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-201411.json harness\runs\phase4-regression-run-20260503-211312.json`
- `git diff --check`

验证结果：

- `ArtifactServiceTest` + `DuckDBRepositoryArtifactTest` 4 / 4 通过
- 前端 lint 通过
- 前端 production build 通过
- 后端编译通过
- Phase 4 regression suite 可列出 5 个固定 case
- 完整 `phase4-regression` benchmark 5 / 5 通过，报告：[phase4-regression-run-20260503-211312.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-211312.json)
- report compare 未发现 pass/fail regression；当前完整 suite 比 baseline 单 case 覆盖更多 case，因此 aggregate delta 表现为覆盖增加

### 最终收尾补充

完成内容：

- 后端补齐 artifact 状态管理：
  - `artifact_status`
  - `archived_at`
  - `deleted_at`
  - `updated_at`
  - `PATCH /api/artifacts/{id}/archive`
  - `PATCH /api/artifacts/{id}/restore`
  - `DELETE /api/artifacts/{id}` 软删除
- `/api/artifacts/recent` 增加 `status` 参数，默认只返回 `ACTIVE`
- 前端历史分析管理入口增加 `活跃` / `归档` / `删除` 状态筛选
- artifact detail modal 增加归档、恢复、软删除操作
- [phase6-artifact-management-design.md](F:\data-analysis-platform\docs\phase6-artifact-management-design.md) 从设计态更新为已实现状态流说明
- [phase6-closeout.md](F:\data-analysis-platform\docs\phase6-closeout.md) 更新最终已知限制和后续建议

新增验证：

- `mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test`
- `npm run lint`
- `npm run build`

新增验证结果：

- artifact repository/service 测试 6 / 6 通过，覆盖默认 active、按 status 查询、archive、restore、soft delete 和 detail 回读
- 前端 lint 通过
- 前端 production build 通过
- 后端 compile 通过
- 最终完整 `phase4-regression` benchmark 5 / 5 通过，报告：[phase4-regression-run-20260503-215029.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-215029.json)
- 对比 [phase4-regression-run-20260503-211312.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-211312.json) 未发现 pass/fail regression
