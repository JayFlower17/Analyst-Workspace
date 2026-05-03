# Harness

This directory contains the minimum evaluation harness for Phase 1 and the first hybrid analysis checks for Phase 2.

Current goals:

1. Keep benchmark cases in one stable location.
2. Run batches of structured, workspace, and hybrid analysis cases against the local backend.
3. Output a small summary with pass/fail counts and failure reasons.

## Structure

```text
harness/
  benchmarks/
    structured/
    workspace/
    hybrid/
    case-registry.json
    README.md
  suites/
  runs/
  run_benchmarks.py
  compare_reports.py
```

## Case Format

Each benchmark case is a JSON file with:

- `id`: stable case id
- `category`: `structured`, `workspace`, or `hybrid`
- `description`: short human-readable intent
- `tags`: stable tags for suites, routes, assets, and regression buckets
- `setup` (optional): resources to create before the request, such as workspace documents
- `request`: payload sent to `/api/analysis/query`
- `expectations`: lightweight checks for the response

Example:

```json
{
  "id": "structured_orders_top_regions",
  "category": "structured",
  "description": "Top regions by total sales",
  "tags": ["structured", "single-table", "ranking", "smoke"],
  "request": {
    "datasetId": 1,
    "query": "Show the top 5 regions by total sales."
  },
  "expectations": {
    "success": true,
    "min_rows": 1,
    "required_columns_any": ["region", "sales", "total_sales"]
  }
}
```

Hybrid cases can include setup documents:

```json
{
  "setup": {
    "documents": [
      {
        "groupId": 1,
        "name": "Workspace Analysis Note",
        "path": "harness/resources/workspace-analysis-note.md"
      }
    ]
  }
}
```

Put benchmark-owned files under `harness/resources/` so cases remain portable across checkouts.

See [benchmarks/README.md](F:\data-analysis-platform\harness\benchmarks\README.md) and
[benchmarks/case-registry.json](F:\data-analysis-platform\harness\benchmarks\case-registry.json)
for the current coverage matrix, tag taxonomy, route coverage, executor coverage, and source coverage.

## Run

Start the backend first, then run:

```bash
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category structured
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category workspace
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category hybrid
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api
```

Run one specific case when debugging a slow or flaky benchmark:

```bash
python harness/run_benchmarks.py --category workspace --case-id workspace_traffic_orders_join_signal
```

Run a tagged subset. Repeating `--tag` requires every selected case to include all requested tags:

```bash
python harness/run_benchmarks.py --category all --tag phase4-regression --list-cases
python harness/run_benchmarks.py --category hybrid --tag document-retrieval --tag policy
```

Run a named suite. Suite definitions live under `harness/suites/`:

```bash
python harness/run_benchmarks.py --suite phase4-regression --list-cases
python harness/run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
```

Control per-request timeout when the backend or model provider is slow:

```bash
python harness/run_benchmarks.py --category hybrid --request-timeout 120
```

List cases without contacting the backend:

```bash
python harness/run_benchmarks.py --category all --list-cases
```

Outputs are written to `harness/runs/`.

Compare two saved reports to inspect pass/fail regressions, aggregate bucket changes, case-level status changes, duration deltas, and key response excerpt differences:

```bash
python harness/compare_reports.py harness/runs/phase4-regression-run-20260503-193032.json harness/runs/phase4-regression-run-20260503-194831.json
python harness/compare_reports.py --json harness/runs/phase4-regression-run-20260503-193032.json harness/runs/phase4-regression-run-20260503-194831.json
```

The compare command exits with code `2` when it detects a pass/fail regression; otherwise it exits with code `0`.

## Phase 5 Regression Workflow

Use this flow before and after Phase 6 artifact or output-layer changes:

1. Keep a baseline report from `harness/runs/`.
2. Run `python harness/run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression`.
3. Compare baseline/current with `python harness/compare_reports.py <baseline-report> <current-report>`.
4. If the run fails, start with top-level `summary.byFailureStage`, then inspect the failed result's `failure_stage` and `reason`.

See [docs/phase5-closeout.md](F:\data-analysis-platform\docs\phase5-closeout.md) for the full Phase 5 closeout, validation record, known limits, and Phase 6 handoff notes.

The current hybrid set includes:

- `hybrid_workspace_orders_users_note_priority`
- `hybrid_workspace_orders_products_merchandising_priority`
- `hybrid_workspace_orders_products_revenue_policy`
- `hybrid_workspace_revenue_policy_document_only`

## Notes

- Default credentials are `phase1user / phase1pass`. Override with `HARNESS_USERNAME`, `HARNESS_PASSWORD`, or CLI flags when needed.
- This is still a minimum harness, not the final evaluation system.
- Current checks focus on API success, result rows, and column hints.
- Workspace cases require valid local `groupId` and dataset setup in your environment.
- Hybrid cases create and clean up temporary workspace documents around each run.
- Case definitions are validated before authentication, including setup document paths.
- Case definitions must include non-empty `tags`.
- Reports include `request_timeout_seconds` plus per-case `duration_seconds`, `case_path`, `request_excerpt`, setup document counts, created setup resources, and cleanup status.
- Reports include `suite`, `suite_description`, `selected_tags`, and each result includes case `tags` and `description`.
- Reports include a top-level `summary` grouped by `category`, `tag`, and `failure_stage`.
- Failed results include `failure_stage` so regressions can be grouped by stage.
- Workspace and hybrid response excerpts include `contextSummary` when the backend returns a unified analysis context summary.
- Cases can assert `contextSummary` with `context_summary_exact` for exact marker/strategy checks and `context_summary_min` for numeric lower bounds.
- Cases can set `generated_sql_absent` to verify document-only flows do not generate SQL.
- Cases can assert `routeDecision` with `route_decision_exact` to verify router output.
- Cases can assert structured planner output with `analysis_plan_step_types_exact`.
- Cases can assert `analysisReport.evidence` with `analysis_report_evidence_exact` and `analysis_report_evidence_min`.
- Cases can assert validation output with `validation_report_exact`, including `passed`, `findingCount`, and `findingCodes`.
- Cases can assert final risk output with `risk_notices_exact`, including `count` and `codes`.

Failure stages currently used by the runner:

- `api`: HTTP errors, request failures, or API `success=false`
- `result`: row count or required column checks
- `summary`: summary phrase checks
- `context`: non-document context summary checks
- `retrieval`: document context / retrieval summary checks
- `route`: route decision checks
- `plan`: analysis plan checks
- `execution`: generated SQL absence or execution log checks
- `report`: analysis report / evidence checks
- `validation`: validation report checks
- `risk`: risk notice checks
- `cleanup`: benchmark cleanup failures after the main request
