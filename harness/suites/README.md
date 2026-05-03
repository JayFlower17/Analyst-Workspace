# Benchmark Suites

Suites are named collections of benchmark cases for repeatable regression runs.

Each suite file lives at `harness/suites/<suite-id>.json` and includes:

- `id`: stable suite id used by `--suite`
- `description`: human-readable purpose
- `category`: category scope passed to the case loader
- `tags`: tags all selected cases must include
- `caseIds`: ordered list of case ids in the suite
- `coverage`: route, executor, source, and assertion coverage notes

## Current Suites

### `phase4-regression`

Purpose:

- smoke-test Phase 4 routing, planning, execution logs, retrieval, and document-only behavior

Run:

```powershell
python harness\run_benchmarks.py --suite phase4-regression --list-cases
python harness\run_benchmarks.py --base-url http://127.0.0.1:18080/api --suite phase4-regression
```

Coverage:

- routes: `STRUCTURED_QUERY`, `HYBRID_ANALYSIS`, `DOCUMENT_EXPLANATION`
- executors: `SQL_EXECUTION`, `DOCUMENT_RETRIEVAL`
- sources: workspace schema, workspace metadata, document chunks
- assertions: context summary, route decision, plan steps, execution logs, document-only SQL absence, analysis report evidence, validation report, risk notices
