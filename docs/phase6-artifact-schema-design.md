# Phase 6 Artifact Schema Design

P6-01 的目标是先把 artifact 扩展边界定清楚，再进入 P6-02 的持久化实现。当前系统已经能在分析响应里返回 `analysisReport`、evidence、execution logs、validation report 和 risk notices，但 `analysis_artifacts` 表只保存摘要、代码、图表类型和结果预览。

## 当前状态

现有实体：

- [AnalysisArtifact.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\model\entity\AnalysisArtifact.java)
- [ArtifactService.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\service\ArtifactService.java)
- [ArtifactController.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\controller\ArtifactController.java)
- [DuckDBRepository.java](F:\data-analysis-platform\backend\src\main\java\com\analysis\repository\DuckDBRepository.java)

当前 `analysis_artifacts` 字段：

| Field | Purpose |
| --- | --- |
| `id` | artifact id |
| `mode` | artifact mode, current save path uses `workplace` |
| `session_id` | chat/session scope, currently nullable |
| `group_id` | workspace scope |
| `dataset_id` | anchor dataset |
| `user_query` | original user query |
| `generated_code_or_sql` | generated SQL or Python code |
| `summary` | final summary text |
| `chart_type` | recommended chart |
| `result_preview_json` | first 20 display rows |
| `created_at` | created timestamp |

Current gap:

- `AnalysisResponse.analysisReport` is returned only for the live request.
- `AnalysisResponse.validationReport`, `riskNotices`, and `executionLogs` are not persisted.
- Artifact list can show old summary/preview, but cannot replay evidence, validation, risks, or execution process.

## Design Goals

1. Preserve existing artifact reads and existing rows.
2. Persist enough Phase 4 output to support a future artifact detail view.
3. Keep the schema simple while the project still uses DuckDB direct SQL instead of a migration framework.
4. Avoid duplicating large result data: `result_preview_json` remains the preview source; persisted report JSON should not store the full `data` array.
5. Make Phase 5 harness usable as the regression safety net for P6-02 through P6-07.

## Proposed Schema

Extend `analysis_artifacts` with nullable JSON string columns and one schema version marker:

| New Field | Type | Source | Notes |
| --- | --- | --- | --- |
| `artifact_schema_version` | `INTEGER` | save path | `1` for legacy-compatible rows, `2` for Phase 6 rows |
| `analysis_report_json` | `VARCHAR` | `AnalysisReport` | canonical trimmed report payload |
| `evidence_summary_json` | `VARCHAR` | `AnalysisReport.evidence` | read-optimized evidence panel source |
| `execution_logs_json` | `VARCHAR` | `List<ToolExecutionLog>` | process replay source |
| `validation_report_json` | `VARCHAR` | `AnalysisValidationReport` | quality findings |
| `risk_notices_json` | `VARCHAR` | `List<RiskNotice>` | final risk notices |
| `context_summary_json` | `VARCHAR` | `UnifiedContextSummary` | compact structured/document context summary |
| `route_decision_json` | `VARCHAR` | `AnalysisRouteDecision` | route replay and debugging |
| `analysis_plan_json` | `VARCHAR` | `AnalysisPlan` | planner replay and debugging |

Why both `analysis_report_json` and focused JSON columns:

- `analysis_report_json` is the canonical persisted report snapshot.
- Focused columns make artifact detail rendering simpler and let future list/detail APIs expose panels without repeatedly extracting nested JSON.
- The duplicated focused payload is small compared with raw data rows and document chunks.

### Trimmed `analysis_report_json`

Persist a trimmed version of `AnalysisReport`:

```json
{
  "summary": "...",
  "rowCount": 4,
  "recommendedChart": "BAR",
  "generatedCodeOrSqlPresent": true,
  "generatedCodeOrSql": "SELECT ...",
  "evidence": {},
  "validationReport": {},
  "riskNotices": [],
  "executionLogs": []
}
```

Do not persist the full `data` array inside `analysis_report_json`; keep row preview in `result_preview_json`. This avoids storing the same rows in two places and keeps artifact detail bounded.

## Migration Strategy

There is no Flyway/Liquibase directory yet. `DuckDBRepository` currently performs table creation and compatible column additions with `information_schema.columns` checks plus `ALTER TABLE`.

P6-02 should follow that existing pattern:

```sql
ALTER TABLE analysis_artifacts ADD COLUMN artifact_schema_version INTEGER;
ALTER TABLE analysis_artifacts ADD COLUMN analysis_report_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN evidence_summary_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN execution_logs_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN validation_report_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN risk_notices_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN context_summary_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN route_decision_json VARCHAR;
ALTER TABLE analysis_artifacts ADD COLUMN analysis_plan_json VARCHAR;
```

Implementation notes:

- Add these columns to the `CREATE TABLE IF NOT EXISTS analysis_artifacts` statement for fresh databases.
- Add an `ensureAnalysisArtifactPhase6Columns(...)` helper for existing local databases.
- Keep every new field nullable.
- Do not backfill historical rows during P6-02.
- New Phase 6 rows should set `artifact_schema_version = 2`.
- Old rows with `NULL` report fields must still map and return successfully.

## Backend DTO Plan

### Entity

Extend `AnalysisArtifact` with string fields matching the new JSON columns:

- `analysisReportJson`
- `evidenceSummaryJson`
- `executionLogsJson`
- `validationReportJson`
- `riskNoticesJson`
- `contextSummaryJson`
- `routeDecisionJson`
- `analysisPlanJson`
- `artifactSchemaVersion`

### API

Keep the current endpoint compatible:

```text
GET /api/artifacts/recent?sessionId=...&groupId=...&limit=...
```

It can continue returning recent artifact objects. New fields should be nullable for legacy artifacts.

Add a detail endpoint in P6-02 or P6-05:

```text
GET /api/artifacts/{id}
```

Recommended response shape:

```json
{
  "success": true,
  "data": {
    "id": 123,
    "mode": "workplace",
    "groupId": 1,
    "datasetId": 8,
    "userQuery": "...",
    "summary": "...",
    "generatedCodeOrSql": "...",
    "chartType": "BAR",
    "resultPreview": [],
    "artifactSchemaVersion": 2,
    "analysisReport": {},
    "evidence": {},
    "executionLogs": [],
    "validationReport": {},
    "riskNotices": [],
    "contextSummary": {},
    "routeDecision": {},
    "analysisPlan": {},
    "reportAvailable": true,
    "createdAt": "..."
  }
}
```

`reportAvailable` should be `false` for legacy artifacts where `analysis_report_json` is null.

## Write Path Plan

P6-02 should update `AnalysisService.saveAnalysisArtifactSafely(...)` so it receives the Phase 4 output objects already built during analysis:

- `AnalysisReport analysisReport`
- `UnifiedContextSummary contextSummary`
- `AnalysisRouteDecision routeDecision`
- `AnalysisPlan analysisPlan`
- `List<ToolExecutionLog> executionLogs`
- `AnalysisValidationReport validationReport`
- `List<RiskNotice> riskNotices`

Then serialize with the existing `ObjectMapper`:

- `analysis_report_json`: trimmed report object
- `evidence_summary_json`: `analysisReport.evidence()`
- `execution_logs_json`: `executionLogs`
- `validation_report_json`: `validationReport`
- `risk_notices_json`: `riskNotices`
- `context_summary_json`: `contextSummary`
- `route_decision_json`: `routeDecision`
- `analysis_plan_json`: `analysisPlan`

Failure to serialize or save these fields should not fail the analysis request. Keep the current safe-save behavior: log a warning and return a successful analysis response with a null or legacy artifact id if persistence fails.

## Frontend Type Plan

Update [frontend-next/src/lib/types.ts](F:\data-analysis-platform\frontend-next\src\lib\types.ts) in P6-02/P6-05:

- Extend `Artifact` with nullable JSON string fields if `/recent` keeps returning raw entity fields.
- Add a parsed `ArtifactDetail` type for `/api/artifacts/{id}`.
- Reuse existing `AnalysisReport`, `AnalysisEvidenceSummary`, `AnalysisValidationReport`, `RiskNotice`, and `ToolExecutionLog` types.
- Treat `reportAvailable=false` as a legacy artifact state and show only summary, preview, chart type, and generated code.

## Compatibility Rules

Legacy artifact rows:

- May have `artifact_schema_version = NULL` or `1`.
- Have all Phase 6 JSON fields as `NULL`.
- Must still appear in `/api/artifacts/recent`.
- Must not break frontend rendering.

New artifact rows:

- Set `artifact_schema_version = 2`.
- Persist all available Phase 4 outputs.
- May still have null optional fields if an execution path did not produce that field.

API compatibility:

- Do not rename existing JSON properties.
- Keep `resultPreviewJson` until the frontend detail view has a parsed preview contract.
- New parsed detail fields should be additive.

## Phase 6 Task Mapping

- P6-02: add entity fields, DuckDB columns, save path, and detail read path for `analysis_report_json`.
- P6-03: render/persist evidence summary with document/table source coverage.
- P6-04: persist and expose execution logs, validation report, and risk notices.
- P6-05: add artifact detail UI using parsed DTO fields.
- P6-06: add workspace artifact management operations and list ergonomics.
- P6-07: run Phase 5 harness, add Phase 6 regression notes, and write closeout.

## Verification Plan

After implementation tasks begin:

```powershell
mvn -f backend\pom.xml -DskipTests compile
python harness\run_benchmarks.py --suite phase4-regression --list-cases
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
python harness\compare_reports.py <baseline-report> <current-report>
git diff --check
```

P6-01 itself is design-only. Its validation is:

- confirm current artifact schema and save/read path from code
- confirm Phase 4 output model fields
- confirm no migration framework exists yet
- record schema, migration, DTO, API, compatibility, and Phase 6 task mapping

## P6-02 Implementation Checkpoint

P6-02 implemented the first slice of this design:

- added `artifact_schema_version`
- added `analysis_report_json`
- updated fresh DuckDB table creation
- added compatible old-database column migration
- saved trimmed `AnalysisReport` with `data=[]`
- kept `result_preview_json` as the row preview source
- added `GET /api/artifacts/{id}` detail readback with parsed `analysisReport`
- kept `/api/artifacts/recent` backward compatible
- added repository/service tests for save/readback and legacy compatibility

The remaining focused JSON fields stay in the plan for P6-03/P6-04:

- `context_summary_json`
- `route_decision_json`
- `analysis_plan_json`

## P6-03 Implementation Checkpoint

P6-03 implemented the evidence slice:

- added `evidence_summary_json`
- updated fresh DuckDB table creation
- added compatible old-database column migration
- saved `AnalysisReport.evidence()` separately from the full trimmed report
- added parsed `evidence` to artifact detail response
- kept fallback behavior: if `evidence_summary_json` is absent, detail can still use `analysisReport.evidence`
- added repository/service test coverage for evidence save/readback and parsed detail output

This gives the Phase 6 artifact detail UI a stable source for the evidence/source panel without requiring the frontend to inspect the full `analysisReport` payload.

## P6-04 Implementation Checkpoint

P6-04 implemented the execution and quality slice:

- added `execution_logs_json`
- added `validation_report_json`
- added `risk_notices_json`
- updated fresh DuckDB table creation
- added compatible old-database column migration
- saved `AnalysisReport.executionLogs()`, `validationReport()`, and `riskNotices()` separately from the full trimmed report
- added parsed `executionLogs`, `validationReport`, and `riskNotices` to artifact detail response
- kept fallback behavior: if focused JSON fields are absent, detail can still use fields embedded in `analysisReport`
- added repository/service test coverage for save/readback, parsed detail output, legacy compatibility, and old-table migration

This gives the Phase 6 artifact detail UI stable sources for process replay, validation findings, and risk notice panels.

## P6-06 Management Checkpoint

P6-06 did not add new persistence columns yet. It implemented the first workspace artifact management layer on top of the existing list/detail API:

- workspace history list supports refresh, search, schema filtering, and explicit detail entry.
- workspace asset dialog reuses the same artifact management controls.
- recent artifact fetch size increased from 8 to 12.
- delete/archive are intentionally held behind a documented API design until status columns and soft-delete semantics are implemented.

The follow-up management design is tracked in [phase6-artifact-management-design.md](F:\data-analysis-platform\docs\phase6-artifact-management-design.md).
