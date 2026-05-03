#!/usr/bin/env python3
"""Compare two benchmark run reports."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


COUNT_KEYS = ("total", "passed", "failed")


def load_report(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SystemExit(f"Report not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON report: {path}: {exc}") from exc


def add_counts(bucket: dict[str, int], passed: bool) -> None:
    bucket["total"] = bucket.get("total", 0) + 1
    if passed:
        bucket["passed"] = bucket.get("passed", 0) + 1
        bucket.setdefault("failed", 0)
    else:
        bucket["failed"] = bucket.get("failed", 0) + 1
        bucket.setdefault("passed", 0)


def computed_summary(report: dict[str, Any]) -> dict[str, dict[str, dict[str, int]]]:
    by_category: dict[str, dict[str, int]] = {}
    by_tag: dict[str, dict[str, int]] = {}
    by_failure_stage: dict[str, dict[str, int]] = {}

    for result in report.get("results", []):
        passed = bool(result.get("passed"))
        category = result.get("category") or "unknown"
        add_counts(by_category.setdefault(category, {}), passed)

        for tag in result.get("tags") or []:
            add_counts(by_tag.setdefault(tag, {}), passed)

        failure_stage = result.get("failure_stage")
        if failure_stage:
            add_counts(by_failure_stage.setdefault(failure_stage, {}), passed)

    return {
        "byCategory": sorted_dict(by_category),
        "byTag": sorted_dict(by_tag),
        "byFailureStage": sorted_dict(by_failure_stage),
    }


def report_summary(report: dict[str, Any]) -> dict[str, Any]:
    summary = report.get("summary")
    if isinstance(summary, dict):
        computed = computed_summary(report)
        return {
            "byCategory": summary.get("byCategory") or computed["byCategory"],
            "byTag": summary.get("byTag") or computed["byTag"],
            "byFailureStage": summary.get("byFailureStage") or computed["byFailureStage"],
        }
    return computed_summary(report)


def sorted_dict(value: dict[str, Any]) -> dict[str, Any]:
    return {key: value[key] for key in sorted(value)}


def result_map(report: dict[str, Any]) -> dict[str, dict[str, Any]]:
    mapped: dict[str, dict[str, Any]] = {}
    for result in report.get("results", []):
        case_id = result.get("case_id")
        if case_id:
            mapped[str(case_id)] = result
    return mapped


def overview(path: Path, report: dict[str, Any]) -> dict[str, Any]:
    return {
        "path": str(path),
        "generated_at": report.get("generated_at"),
        "suite": report.get("suite"),
        "category": report.get("category"),
        "total": report.get("total"),
        "passed": report.get("passed"),
        "failed": report.get("failed"),
    }


def bucket_deltas(
    baseline: dict[str, dict[str, int]],
    current: dict[str, dict[str, int]],
) -> list[dict[str, Any]]:
    deltas: list[dict[str, Any]] = []
    for name in sorted(set(baseline) | set(current)):
        before = baseline.get(name, {})
        after = current.get(name, {})
        counts = {
            key: int(after.get(key, 0)) - int(before.get(key, 0))
            for key in COUNT_KEYS
        }
        if any(counts.values()):
            deltas.append(
                {
                    "name": name,
                    "baseline": normalized_counts(before),
                    "current": normalized_counts(after),
                    "delta": counts,
                }
            )
    return deltas


def normalized_counts(value: dict[str, Any]) -> dict[str, int]:
    return {key: int(value.get(key, 0)) for key in COUNT_KEYS}


def nested_get(value: dict[str, Any], *path: str) -> Any:
    current: Any = value
    for key in path:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def risk_notice_count(excerpt: dict[str, Any]) -> int:
    report_count = nested_get(excerpt, "analysisReport", "riskNoticeCount")
    if isinstance(report_count, int):
        return report_count
    notices = excerpt.get("riskNotices")
    if isinstance(notices, list):
        return len(notices)
    return 0


def case_signature(result: dict[str, Any]) -> dict[str, Any]:
    excerpt = result.get("response_excerpt") or {}
    evidence = nested_get(excerpt, "analysisReport", "evidence") or {}
    return {
        "passed": result.get("passed"),
        "reason": result.get("reason"),
        "failure_stage": result.get("failure_stage"),
        "status_code": result.get("status_code"),
        "route": nested_get(excerpt, "routeDecision", "route"),
        "plan_step_types": nested_get(excerpt, "analysisPlan", "stepTypes") or [],
        "execution_tool_types": nested_get(excerpt, "executionLogs", "toolTypes") or [],
        "validation_passed": nested_get(excerpt, "validationReport", "passed"),
        "risk_notice_count": risk_notice_count(excerpt),
        "evidence": {
            "datasetCount": evidence.get("datasetCount"),
            "documentChunkCount": evidence.get("documentChunkCount"),
            "documentNames": evidence.get("documentNames") or [],
            "documentStrategy": evidence.get("documentStrategy"),
        },
    }


def changed_fields(before: dict[str, Any], after: dict[str, Any]) -> list[str]:
    return [key for key in sorted(set(before) | set(after)) if before.get(key) != after.get(key)]


def compare_cases(
    baseline: dict[str, dict[str, Any]],
    current: dict[str, dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    changes: list[dict[str, Any]] = []
    regressions: list[dict[str, Any]] = []

    for case_id in sorted(set(baseline) | set(current)):
        before = baseline.get(case_id)
        after = current.get(case_id)

        if before is None and after is not None:
            change = {
                "case_id": case_id,
                "change_type": "added",
                "current": case_signature(after),
                "duration_delta_seconds": after.get("duration_seconds"),
            }
            changes.append(change)
            if after.get("passed") is False:
                regressions.append(
                    {
                        "case_id": case_id,
                        "type": "added_failed_case",
                        "current": case_signature(after),
                    }
                )
            continue

        if before is not None and after is None:
            change = {
                "case_id": case_id,
                "change_type": "removed",
                "baseline": case_signature(before),
                "duration_delta_seconds": None,
            }
            changes.append(change)
            if before.get("passed") is True:
                regressions.append(
                    {
                        "case_id": case_id,
                        "type": "removed_passing_case",
                        "baseline": case_signature(before),
                    }
                )
            continue

        assert before is not None and after is not None
        before_sig = case_signature(before)
        after_sig = case_signature(after)
        fields = changed_fields(before_sig, after_sig)
        duration_delta = duration_delta_seconds(before, after)

        if fields or duration_delta is not None:
            changes.append(
                {
                    "case_id": case_id,
                    "change_type": "changed",
                    "changed_fields": fields,
                    "baseline": {key: before_sig.get(key) for key in fields},
                    "current": {key: after_sig.get(key) for key in fields},
                    "duration_delta_seconds": duration_delta,
                }
            )

        if before.get("passed") is True and after.get("passed") is False:
            regressions.append(
                {
                    "case_id": case_id,
                    "type": "pass_to_fail",
                    "baseline": before_sig,
                    "current": after_sig,
                }
            )

    return changes, regressions


def duration_delta_seconds(before: dict[str, Any], after: dict[str, Any]) -> float | None:
    before_duration = before.get("duration_seconds")
    after_duration = after.get("duration_seconds")
    if not isinstance(before_duration, (int, float)) or not isinstance(after_duration, (int, float)):
        return None
    delta = round(float(after_duration) - float(before_duration), 3)
    if delta == 0:
        return None
    return delta


def build_comparison(
    baseline_path: Path,
    current_path: Path,
    baseline_report: dict[str, Any],
    current_report: dict[str, Any],
) -> dict[str, Any]:
    baseline_summary = report_summary(baseline_report)
    current_summary = report_summary(current_report)
    case_changes, regressions = compare_cases(
        result_map(baseline_report),
        result_map(current_report),
    )

    aggregate_deltas = {
        "byCategory": bucket_deltas(
            baseline_summary.get("byCategory", {}),
            current_summary.get("byCategory", {}),
        ),
        "byTag": bucket_deltas(
            baseline_summary.get("byTag", {}),
            current_summary.get("byTag", {}),
        ),
        "byFailureStage": bucket_deltas(
            baseline_summary.get("byFailureStage", {}),
            current_summary.get("byFailureStage", {}),
        ),
    }

    failed_delta = int(current_report.get("failed") or 0) - int(baseline_report.get("failed") or 0)
    if failed_delta > 0:
        regressions.append(
            {
                "type": "failed_count_increase",
                "baseline_failed": baseline_report.get("failed"),
                "current_failed": current_report.get("failed"),
            }
        )

    return {
        "baseline": overview(baseline_path, baseline_report),
        "current": overview(current_path, current_report),
        "aggregate_deltas": aggregate_deltas,
        "case_changes": case_changes,
        "regressions": regressions,
    }


def print_text(comparison: dict[str, Any]) -> None:
    baseline = comparison["baseline"]
    current = comparison["current"]
    print("Compared reports:")
    print(
        f"- Baseline: {baseline['path']} "
        f"(suite={baseline.get('suite')}, passed={baseline.get('passed')}, failed={baseline.get('failed')})"
    )
    print(
        f"- Current:  {current['path']} "
        f"(suite={current.get('suite')}, passed={current.get('passed')}, failed={current.get('failed')})"
    )

    regressions = comparison["regressions"]
    if regressions:
        print(f"Outcome: {len(regressions)} regression(s) detected")
    else:
        print("Outcome: no pass/fail regressions")

    aggregate_deltas = comparison["aggregate_deltas"]
    if any(aggregate_deltas.values()):
        print("Aggregate deltas:")
        for group_name, deltas in aggregate_deltas.items():
            if not deltas:
                continue
            print(f"- {group_name}:")
            for delta in deltas:
                change = ", ".join(
                    f"{key} {format_delta(delta['delta'][key])}"
                    for key in COUNT_KEYS
                    if delta["delta"][key]
                )
                print(f"  - {delta['name']}: {change}")
    else:
        print("Aggregate deltas: none")

    case_changes = comparison["case_changes"]
    if case_changes:
        print("Case changes:")
        for change in case_changes:
            details = [change["change_type"]]
            if change.get("changed_fields"):
                details.append("fields=" + ",".join(change["changed_fields"]))
            if change.get("duration_delta_seconds") is not None:
                details.append(f"duration {format_delta(change['duration_delta_seconds'])}s")
            print(f"- {change['case_id']}: {'; '.join(details)}")
    else:
        print("Case changes: none")


def format_delta(value: int | float) -> str:
    if value > 0:
        return f"+{value}"
    return str(value)


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare two benchmark run reports.")
    parser.add_argument("baseline", type=Path, help="Baseline report JSON path")
    parser.add_argument("current", type=Path, help="Current report JSON path")
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    baseline_report = load_report(args.baseline)
    current_report = load_report(args.current)
    comparison = build_comparison(args.baseline, args.current, baseline_report, current_report)

    if args.json:
        print(json.dumps(comparison, ensure_ascii=False, indent=2))
    else:
        print_text(comparison)

    return 2 if comparison["regressions"] else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
