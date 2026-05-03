# Phase 6 Closeout

Phase 6 将分析结果从一次性响应推进为 workspace 内可回看的分析资产。当前系统已经可以把 Phase 4 统一输出中的 report、证据、执行过程、校验结果和风险提示持久化到 artifact，并在前端历史结果里打开详情回放和做基础管理。

## 完成范围

### P6-01 Artifact schema 扩展设计

- 新增 [phase6-artifact-schema-design.md](F:\data-analysis-platform\docs\phase6-artifact-schema-design.md)
- 定义 `analysis_artifacts` Phase 6 扩展字段
- 明确 legacy artifact 兼容策略
- 明确 trimmed `analysisReport` 不重复保存完整结果行，继续由 `result_preview_json` 承担结果预览

### P6-02 持久化 analysis report

- `analysis_artifacts` 增加 `artifact_schema_version` 和 `analysis_report_json`
- 新分析保存 trimmed `AnalysisReport`
- 新增 `GET /api/artifacts/{id}` detail 回读
- detail 响应解析 `analysisReport`
- legacy artifact 返回 `reportAvailable=false`，不破坏旧记录

### P6-03 持久化 evidence summary

- `analysis_artifacts` 增加 `evidence_summary_json`
- 保存 `analysisReport.evidence`
- detail 响应返回 parsed `evidence`
- 缺少独立 evidence 字段时，可从 `analysisReport.evidence` 回退

### P6-04 持久化 execution / validation / risk

- `analysis_artifacts` 增加：
  - `execution_logs_json`
  - `validation_report_json`
  - `risk_notices_json`
- 保存并回读执行日志、校验报告和风险提示
- detail 响应返回 parsed `executionLogs`、`validationReport`、`riskNotices`
- 缺少独立字段时，可从 `analysisReport` 内嵌字段回退

### P6-05 Artifact 详情视图

- [workspace/page.tsx](F:\data-analysis-platform\frontend-next\src\app\(dashboard)\workspace\page.tsx) 增加 artifact detail modal
- 历史结果点击进入详情，而不是只回填摘要
- 详情展示：
  - 原始查询
  - 摘要
  - 结果预览
  - 证据来源
  - 执行过程
  - 校验结果
  - 风险提示
  - SQL / Python 代码
- 支持将历史 artifact 加载回当前分析结果区

### P6-06 Workspace artifact 管理

- 历史结果列表增加手动刷新
- 最近 artifact 拉取数量从 8 提升到 12
- 支持搜索 query、summary、generated code、chart type、created time 和 schema label
- 支持 `全部` / `Phase 6` / `Legacy` 筛选
- “工作区资产 > 分析结果”复用同一套管理入口
- 新增 [phase6-artifact-management-design.md](F:\data-analysis-platform\docs\phase6-artifact-management-design.md)，记录归档、恢复和软删除接口设计
- 收尾阶段补齐：
  - `artifact_status`
  - `archived_at`
  - `deleted_at`
  - `updated_at`
  - `PATCH /api/artifacts/{id}/archive`
  - `PATCH /api/artifacts/{id}/restore`
  - `DELETE /api/artifacts/{id}` 软删除

### P6-07 回归与收尾

- 运行 artifact 持久化与回读测试
- 运行前端 lint 和 production build
- 运行后端 compile
- 运行 Phase 5 harness 的 `phase4-regression` suite
- 对比 baseline/current benchmark report，确认没有 pass/fail regression
- 本文档收束 Phase 6 能力、验证报告和已知限制
- 最终收尾阶段补齐 artifact 状态管理，并重新运行相关测试

## 当前能力

用户现在可以：

- 在 workspace 发起分析并生成 artifact。
- 在历史分析列表里看到最近的分析记录。
- 打开历史 artifact detail，回看该次分析的摘要、预览数据、代码、证据、执行过程、校验结果和风险提示。
- 区分新 Phase 6 artifact 和 legacy artifact。
- 搜索、筛选、刷新历史分析列表。
- 从 artifact detail 将历史结果加载回当前结果区。
- 归档、恢复和软删除历史 artifact。

工程侧现在具备：

- DuckDB 兼容补列迁移。
- Phase 6 artifact raw JSON 持久化字段。
- `ArtifactDetailResponse` parsed DTO。
- Artifact 状态管理 API。
- Artifact service/repository 单元测试覆盖。
- Phase 5 harness 回归安全网。

## 验证记录

P6-07 已运行：

```powershell
mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test
npm run lint
npm run build
mvn -f backend\pom.xml -DskipTests compile
python harness\run_benchmarks.py --suite phase4-regression --list-cases
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-201411.json harness\runs\phase4-regression-run-20260503-211312.json
python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-211312.json harness\runs\phase4-regression-run-20260503-215029.json
```

结果：

- `ArtifactServiceTest` + `DuckDBRepositoryArtifactTest`：4 / 4 通过
- 最终收尾后 `ArtifactServiceTest` + `DuckDBRepositoryArtifactTest`：6 / 6 通过
- 前端 lint 通过
- 前端 production build 通过
- 后端 compile 通过
- `phase4-regression` suite 可列出 5 个固定 case
- 完整 `phase4-regression` benchmark：5 / 5 通过
- 最近完整报告：[phase4-regression-run-20260503-211312.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-211312.json)
- 对比 [phase4-regression-run-20260503-201411.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-201411.json) 未发现 pass/fail regression
- 最终整体验收报告：[phase4-regression-run-20260503-215029.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-215029.json)，5 / 5 通过
- 对比 [phase4-regression-run-20260503-211312.json](F:\data-analysis-platform\harness\runs\phase4-regression-run-20260503-211312.json) 未发现 pass/fail regression，aggregate deltas 为 none

Benchmark summary：

- by category：`workspace` 1 / 1，通过；`hybrid` 4 / 4，通过
- by tag：`phase4-regression` 5 / 5，通过；`document-retrieval` 4 / 4，通过；`policy` 2 / 2，通过
- by failure stage：无失败项

## 整体运行验收

最终收尾阶段已完成：

```powershell
mvn -f backend\pom.xml '-Dtest=ArtifactServiceTest,DuckDBRepositoryArtifactTest' test
mvn -f backend\pom.xml -DskipTests compile
npm run lint
npm run build
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
python harness\compare_reports.py harness\runs\phase4-regression-run-20260503-211312.json harness\runs\phase4-regression-run-20260503-215029.json
git diff --check
```

结果：

- artifact 状态管理测试 6 / 6 通过。
- backend compile 通过。
- frontend lint / production build 通过。
- 运行中的本地 API `http://127.0.0.1:18080/api` 完整 benchmark 5 / 5 通过。
- 最终 benchmark 与上一份 closeout 报告相比没有 pass/fail regression。

补充说明：

- 曾尝试用 `18081` 和独立临时数据库启动一套新后端做 live API 验收；第一次被已有 H2 本地库锁住，后续 Windows/PowerShell 启动参数注入不稳定。为避免干扰当前运行服务，最终采用单元测试覆盖新 artifact 状态流，采用现有 `18080` 服务完成整体 benchmark 回归。

## 已知限制

- Artifact 归档、恢复、软删除已实现；但尚未补 workspace membership / ownership 级别的权限说明和测试。
- `context_summary_json`、`route_decision_json`、`analysis_plan_json` 仍停留在 schema 设计阶段，当前未持久化。
- 当前 `/api/artifacts/recent` 支持 status 筛选，但仍不支持服务端 query 搜索、排序和分页；P6-06 的文本搜索和 schema 筛选仍是前端列表内过滤。
- 前端 artifact detail 已通过 build/lint 验证，但本轮没有使用浏览器自动点击 `/workspace` 做视觉和交互核验。
- Benchmark 依赖本地 `http://127.0.0.1:18080/api` 服务和固定样例数据；报告可证明当前本地环境通过，但还不是 CI 级别保证。
- `analysis_report_json` 是 trimmed report，完整结果行不重复存储；历史详情只能展示 `result_preview_json` 保存的预览行。
- 旧 artifact 可以兼容展示，但没有 Phase 6 parsed evidence/execution/validation/risk 字段。
- 当前权限边界仍沿用已有 artifact API，尚未单独补 artifact ownership / workspace membership 校验说明。

## 后续建议

1. 增加 Phase 6 专属 benchmark suite，覆盖 artifact detail API、legacy artifact 兼容和管理状态流。
2. 把 `context_summary_json`、`route_decision_json`、`analysis_plan_json` 从设计推进到持久化和 detail 回放。
3. 用浏览器自动化打开 `/workspace` 做一次 artifact list/detail 手工流验证。
4. 将 Phase 5 harness 接入 CI 或至少形成固定 release checklist。
