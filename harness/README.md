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
  runs/
  run_benchmarks.py
```

## Case Format

Each benchmark case is a JSON file with:

- `id`: stable case id
- `category`: `structured` or `workspace`
- `description`: short human-readable intent
- `setup` (optional): resources to create before the request, such as workspace documents
- `request`: payload sent to `/api/analysis/query`
- `expectations`: lightweight checks for the response

Example:

```json
{
  "id": "structured_orders_top_regions",
  "category": "structured",
  "description": "Top regions by total sales",
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
        "path": "temp-samples/workspace-analysis-note.md"
      }
    ]
  }
}
```

## Run

Start the backend first, then run:

```bash
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category structured
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category workspace
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api --category hybrid
python harness/run_benchmarks.py --base-url http://127.0.0.1:8080/api
```

Outputs are written to `harness/runs/`.

## Notes

- Default credentials are `phase1user / phase1pass`. Override with `HARNESS_USERNAME`, `HARNESS_PASSWORD`, or CLI flags when needed.
- This is still a minimum harness, not the final evaluation system.
- Current checks focus on API success, result rows, and column hints.
- Workspace cases require valid local `groupId` and dataset setup in your environment.
- Hybrid cases create and clean up temporary workspace documents around each run.
