# Benchmark Case Registry

This directory stores the benchmark cases used by `harness/run_benchmarks.py`.

The registry lives in:

- [case-registry.json](F:\data-analysis-platform\harness\benchmarks\case-registry.json)

Use the registry when deciding which cases belong in a smoke run, Phase 4 regression run, route-specific check, or future comparison report.

## Case Format

Each case JSON must include:

- `id`: stable case id, unique across all benchmark categories
- `category`: one of `structured`, `workspace`, or `hybrid`
- `description`: short human-readable purpose
- `tags`: non-empty list of stable labels
- `request`: payload sent to `/api/analysis/query`
- `expectations`: response checks used by the runner

Optional:

- `setup`: temporary resources to create before the request, such as benchmark-owned documents

## Tag Taxonomy

Current tag groups:

- suite: `smoke`, `phase4-regression`
- input shape: `single-table`, `multi-table`, `document-only`
- operation: `aggregation`, `ranking`, `join`, `sql-only`
- source: `document-retrieval`, `workspace-note`, `policy`, `policy-note`
- observability: `execution-log`

Tags are intentionally simple strings. Prefer adding one clear tag over encoding multiple meanings in one tag.

## Phase 4 Output Assertions

The runner supports explicit assertions for Phase 4 output fields:

- `analysis_report_evidence_exact`: exact checks against `analysisReport.evidence`
- `analysis_report_evidence_min`: minimum numeric checks against `analysisReport.evidence`
- `validation_report_exact`: checks `validationReport.passed`, `findingCount`, and optional `findingCodes`
- `risk_notices_exact`: checks `riskNotices.count` and optional `codes`

Example:

```json
{
  "expectations": {
    "analysis_report_evidence_exact": {
      "datasetCount": 2,
      "documentStrategy": "HEAVY"
    },
    "analysis_report_evidence_min": {
      "documentChunkCount": 1
    },
    "validation_report_exact": {
      "passed": true,
      "findingCount": 0
    },
    "risk_notices_exact": {
      "count": 0
    }
  }
}
```

## Coverage Matrix

| Case | Route | Executors | Sources | Key tags |
| --- | --- | --- | --- | --- |
| `structured_employees_department_summary` | `LEGACY_DATASET` | legacy SQL/Python | dataset | `structured`, `single-table`, `aggregation`, `smoke` |
| `structured_sales_monthly_rollup` | `LEGACY_DATASET` | legacy SQL/Python | dataset | `structured`, `single-table`, `aggregation`, `smoke` |
| `structured_website_traffic_top_source` | `LEGACY_DATASET` | legacy SQL/Python | dataset | `structured`, `single-table`, `ranking`, `smoke` |
| `workspace_orders_users_join_overview` | `STRUCTURED_QUERY` | `SQL_EXECUTION` | workspace schema, metadata | `workspace`, `multi-table`, `join`, `smoke` |
| `workspace_traffic_orders_join_signal` | `STRUCTURED_QUERY` | `SQL_EXECUTION` | workspace schema, metadata | `workspace`, `sql-only`, `phase4-regression` |
| `hybrid_workspace_orders_products_merchandising_priority` | `HYBRID_ANALYSIS` | `DOCUMENT_RETRIEVAL`, `SQL_EXECUTION` | workspace schema, metadata, document chunks | `hybrid`, `policy-note`, `phase4-regression` |
| `hybrid_workspace_orders_products_revenue_policy` | `HYBRID_ANALYSIS` | `DOCUMENT_RETRIEVAL`, `SQL_EXECUTION` | workspace schema, metadata, document chunks | `hybrid`, `policy`, `phase4-regression` |
| `hybrid_workspace_orders_users_note_priority` | `HYBRID_ANALYSIS` | `DOCUMENT_RETRIEVAL`, `SQL_EXECUTION` | workspace schema, metadata, document chunks | `hybrid`, `workspace-note`, `phase4-regression` |
| `hybrid_workspace_revenue_policy_document_only` | `DOCUMENT_EXPLANATION` | `DOCUMENT_RETRIEVAL` | workspace schema, metadata, document chunks | `hybrid`, `document-only`, `policy`, `phase4-regression` |

## Useful Commands

List the Phase 4 regression cases:

```powershell
python harness\run_benchmarks.py --category all --tag phase4-regression --list-cases
```

List policy-related hybrid cases:

```powershell
python harness\run_benchmarks.py --category hybrid --tag policy --list-cases
```

Run a single tagged smoke case against a backend on port 18080:

```powershell
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --category all --tag phase4-regression --limit 1
```
