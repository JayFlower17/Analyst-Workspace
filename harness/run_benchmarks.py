import argparse
import json
import os
import sys
import time
import uuid
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib import error, request


ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
BENCHMARKS_DIR = ROOT / "benchmarks"
RUNS_DIR = ROOT / "runs"
SUITES_DIR = ROOT / "suites"
DEFAULT_REQUEST_TIMEOUT_SECONDS = int(os.getenv("HARNESS_REQUEST_TIMEOUT_SECONDS", "90"))


@dataclass
class CaseResult:
    case_id: str
    category: str
    passed: bool
    reason: str
    status_code: int | None
    response_excerpt: dict[str, Any] | None
    duration_seconds: float
    failure_stage: str | None = None
    case_path: str | None = None
    tags: list[str] | None = None
    description: str | None = None
    request_excerpt: dict[str, Any] | None = None
    setup_documents_requested: int = 0
    setup_resources_created: list[dict[str, Any]] | None = None
    cleanup_succeeded: bool | None = None
    cleanup_errors: list[str] | None = None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run minimum Phase 1 benchmark cases.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api", help="Backend API base URL")
    parser.add_argument("--username", default=os.getenv("HARNESS_USERNAME", "phase1user"), help="Login username")
    parser.add_argument("--password", default=os.getenv("HARNESS_PASSWORD", "phase1pass"), help="Login password")
    parser.add_argument(
        "--category",
        choices=["structured", "workspace", "hybrid", "all"],
        default="all",
        help="Benchmark category to run",
    )
    parser.add_argument(
        "--case-id",
        action="append",
        default=None,
        help="Run only matching case id. Repeat this option to run multiple specific cases.",
    )
    parser.add_argument(
        "--tag",
        action="append",
        default=None,
        help="Run only cases that include this tag. Repeat this option to require multiple tags.",
    )
    parser.add_argument(
        "--suite",
        default=None,
        help="Run a named benchmark suite from harness/suites/<name>.json.",
    )
    parser.add_argument(
        "--request-timeout",
        type=int,
        default=DEFAULT_REQUEST_TIMEOUT_SECONDS,
        help="Per-request timeout in seconds. Defaults to HARNESS_REQUEST_TIMEOUT_SECONDS or 90.",
    )
    parser.add_argument(
        "--list-cases",
        action="store_true",
        help="List selected benchmark cases and exit without authenticating or running requests.",
    )
    parser.add_argument("--limit", type=int, default=None, help="Optional max number of cases to run")
    return parser.parse_args()


def load_suite(name: str) -> dict[str, Any]:
    path = SUITES_DIR / f"{name}.json"
    if not path.exists():
        available = ", ".join(sorted(item.stem for item in SUITES_DIR.glob("*.json"))) if SUITES_DIR.exists() else ""
        suffix = f" Available suites: {available}" if available else ""
        raise ValueError(f"Unknown suite {name!r}.{suffix}")
    with path.open("r", encoding="utf-8") as fh:
        payload = json.load(fh)
    payload["_path"] = str(path)
    return payload


def load_cases(category: str) -> list[dict[str, Any]]:
    selected_dirs = ["structured", "workspace", "hybrid"] if category == "all" else [category]
    cases: list[dict[str, Any]] = []
    for selected in selected_dirs:
        for path in sorted((BENCHMARKS_DIR / selected).glob("*.json")):
            with path.open("r", encoding="utf-8") as fh:
                payload = json.load(fh)
                payload["_path"] = str(path)
                cases.append(payload)
    return cases


def filter_cases_by_id(cases: list[dict[str, Any]], case_ids: list[str] | None) -> list[dict[str, Any]]:
    if not case_ids:
        return cases

    requested = set(case_ids)
    selected = [case for case in cases if case.get("id") in requested]
    found = {case.get("id") for case in selected}
    missing = sorted(requested - found)
    if missing:
        available = ", ".join(sorted(str(case.get("id")) for case in cases))
        raise ValueError(f"Unknown case id(s): {missing}. Available in selected category: {available}")
    return selected


def filter_cases_by_suite(cases: list[dict[str, Any]], suite: dict[str, Any] | None) -> list[dict[str, Any]]:
    if not suite:
        return cases

    case_ids = suite.get("caseIds") or []
    if not isinstance(case_ids, list) or not case_ids:
        raise ValueError(f"Suite {suite.get('id') or suite.get('_path')} must define non-empty caseIds")

    requested = [str(case_id) for case_id in case_ids]
    selected = filter_cases_by_id(cases, requested)
    selected_by_id = {case["id"]: case for case in selected}
    return [selected_by_id[case_id] for case_id in requested]


def filter_cases_by_tag(cases: list[dict[str, Any]], tags: list[str] | None) -> list[dict[str, Any]]:
    if not tags:
        return cases

    requested = {tag.strip() for tag in tags if tag and tag.strip()}
    if not requested:
        return cases

    return [
        case
        for case in cases
        if requested.issubset({str(tag) for tag in case.get("tags", [])})
    ]


def validate_case_definitions(cases: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    valid_categories = {"structured", "workspace", "hybrid"}

    for case in cases:
        label = case.get("id") or case.get("_path") or "<unknown case>"
        category = case.get("category")
        if category not in valid_categories:
            errors.append(f"{label}: invalid category {category!r}")
        if not case.get("request"):
            errors.append(f"{label}: missing request payload")
        if "expectations" not in case:
            errors.append(f"{label}: missing expectations")
        tags = case.get("tags")
        if tags is None:
            errors.append(f"{label}: missing tags")
        elif (
            not isinstance(tags, list)
            or not tags
            or not all(isinstance(tag, str) and tag.strip() for tag in tags)
        ):
            errors.append(f"{label}: tags must be a non-empty string list")

        for document in (case.get("setup") or {}).get("documents", []):
            raw_path = document.get("path")
            if not raw_path:
                errors.append(f"{label}: setup document is missing path")
                continue
            path = resolve_repo_path(raw_path)
            if not path.exists():
                errors.append(f"{label}: setup document does not exist: {path}")
            if document.get("groupId") is None:
                errors.append(f"{label}: setup document {raw_path} is missing groupId")

    return errors


def print_case_list(cases: list[dict[str, Any]]) -> None:
    for case in cases:
        setup_documents = len((case.get("setup") or {}).get("documents", []))
        tags = ",".join(case.get("tags", []))
        print(
            f"{case.get('category')}\t{case.get('id')}\t"
            f"tags={tags}\tsetup_documents={setup_documents}\t{case.get('_path')}"
        )


def build_opener() -> request.OpenerDirector:
    # Local benchmark traffic should never be sent through a system proxy.
    return request.build_opener(request.ProxyHandler({}))


def post_json(
    opener: request.OpenerDirector,
    url: str,
    payload: dict[str, Any],
    token: str | None = None,
    timeout_seconds: int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
) -> tuple[int, dict[str, Any]]:
    body = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(
        url,
        data=body,
        headers=headers,
        method="POST",
    )
    with opener.open(req, timeout=timeout_seconds) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def get_json(
    opener: request.OpenerDirector,
    url: str,
    token: str | None = None,
    timeout_seconds: int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
) -> tuple[int, dict[str, Any]]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, headers=headers, method="GET")
    with opener.open(req, timeout=timeout_seconds) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def post_multipart(
    opener: request.OpenerDirector,
    url: str,
    fields: dict[str, str],
    files: dict[str, Path],
    token: str | None = None,
    timeout_seconds: int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
) -> tuple[int, dict[str, Any]]:
    boundary = f"----CodexHarness{uuid.uuid4().hex}"
    body = bytearray()

    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        body.extend(str(value).encode("utf-8"))
        body.extend(b"\r\n")

    for name, path in files.items():
        filename = path.name
        content = path.read_bytes()
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(
            f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode("utf-8")
        )
        body.extend(b"Content-Type: application/octet-stream\r\n\r\n")
        body.extend(content)
        body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = request.Request(url, data=bytes(body), headers=headers, method="POST")
    with opener.open(req, timeout=timeout_seconds) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def delete_resource(
    opener: request.OpenerDirector,
    url: str,
    token: str | None = None,
    timeout_seconds: int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
) -> tuple[int, dict[str, Any]]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, headers=headers, method="DELETE")
    with opener.open(req, timeout=timeout_seconds) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def authenticate(
    opener: request.OpenerDirector,
    base_url: str,
    username: str,
    password: str,
    timeout_seconds: int,
) -> str:
    auth_url = f"{base_url.rstrip('/')}/auth/signin"
    _, response = post_json(
        opener,
        auth_url,
        {"username": username, "password": password},
        timeout_seconds=timeout_seconds,
    )
    token = response.get("token")
    if not token:
        raise RuntimeError("Authentication succeeded without a token in the response.")
    return token


def resolve_focus_dataset_ids(
    opener: request.OpenerDirector,
    base_url: str,
    token: str,
    request_payload: dict[str, Any],
    timeout_seconds: int,
) -> dict[str, Any]:
    focus_names = request_payload.get("focusDatasetNames")
    group_id = request_payload.get("groupId")
    if not focus_names or group_id is None:
        return request_payload

    _, response = get_json(
        opener,
        f"{base_url.rstrip('/')}/groups/{group_id}/datasets",
        token=token,
        timeout_seconds=timeout_seconds,
    )
    datasets = response.get("data") or []
    datasets_by_name = {item.get("name"): item.get("id") for item in datasets}

    missing = [name for name in focus_names if name not in datasets_by_name]
    if missing:
        raise RuntimeError(f"Missing datasets in group {group_id}: {missing}")

    resolved_payload = dict(request_payload)
    resolved_payload["focusDatasetIds"] = [datasets_by_name[name] for name in focus_names]
    return resolved_payload


def has_any_required_column(rows: list[dict[str, Any]], required_columns_any: list[str]) -> bool:
    if not rows:
        return False
    available = {str(key).lower() for key in rows[0].keys()}
    return any(column.lower() in available for column in required_columns_any)


def contains_any(text: str, options: list[str]) -> bool:
    lowered = text.lower()
    return any(option.lower() in lowered for option in options)


def contains_all(text: str, options: list[str]) -> bool:
    lowered = text.lower()
    return all(option.lower() in lowered for option in options)


def evaluate_context_summary(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    exact = expectations.get("context_summary_exact") or {}
    minimums = expectations.get("context_summary_min") or {}
    if not exact and not minimums:
        return None

    context_summary = response.get("contextSummary")
    if not isinstance(context_summary, dict):
        return "Expected contextSummary in response"

    for key, expected in exact.items():
        actual = context_summary.get(key)
        if actual != expected:
            return f"Expected contextSummary.{key}={expected!r}, got {actual!r}"

    for key, minimum in minimums.items():
        actual = context_summary.get(key)
        if not isinstance(actual, (int, float)) or actual < minimum:
            return f"Expected contextSummary.{key}>={minimum!r}, got {actual!r}"

    return None


def evaluate_route_decision(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    exact = expectations.get("route_decision_exact") or {}
    if not exact:
        return None

    route_decision = response.get("routeDecision")
    if not isinstance(route_decision, dict):
        return "Expected routeDecision in response"

    for key, expected in exact.items():
        actual = route_decision.get(key)
        if actual != expected:
            return f"Expected routeDecision.{key}={expected!r}, got {actual!r}"

    return None


def evaluate_analysis_plan(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    expected_step_types = expectations.get("analysis_plan_step_types_exact") or []
    if not expected_step_types:
        return None

    analysis_plan = response.get("analysisPlan")
    if not isinstance(analysis_plan, dict):
        return "Expected analysisPlan in response"

    steps = analysis_plan.get("steps") or []
    actual_step_types = [step.get("type") for step in steps if isinstance(step, dict)]
    if actual_step_types != expected_step_types:
        return f"Expected analysisPlan step types {expected_step_types!r}, got {actual_step_types!r}"

    return None


def evaluate_execution_logs(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    expected_tool_types = expectations.get("execution_log_tool_types_exact") or []
    if not expected_tool_types:
        return None

    execution_logs = response.get("executionLogs")
    if not isinstance(execution_logs, list):
        return "Expected executionLogs in response"

    actual_tool_types = [log.get("toolType") for log in execution_logs if isinstance(log, dict)]
    if actual_tool_types != expected_tool_types:
        return f"Expected execution log tool types {expected_tool_types!r}, got {actual_tool_types!r}"

    failed_logs = [
        log.get("toolType")
        for log in execution_logs
        if isinstance(log, dict) and log.get("success") is False
    ]
    if failed_logs:
        return f"Expected all execution logs to succeed, failed logs: {failed_logs!r}"

    return None


def evaluate_analysis_report_evidence(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    exact = expectations.get("analysis_report_evidence_exact") or {}
    minimums = expectations.get("analysis_report_evidence_min") or {}
    if not exact and not minimums:
        return None

    report = response.get("analysisReport")
    if not isinstance(report, dict):
        return "Expected analysisReport in response"

    evidence = report.get("evidence")
    if not isinstance(evidence, dict):
        return "Expected analysisReport.evidence in response"

    for key, expected in exact.items():
        actual = evidence.get(key)
        if actual != expected:
            return f"Expected analysisReport.evidence.{key}={expected!r}, got {actual!r}"

    for key, minimum in minimums.items():
        actual = evidence.get(key)
        if not isinstance(actual, (int, float)) or actual < minimum:
            return f"Expected analysisReport.evidence.{key}>={minimum!r}, got {actual!r}"

    return None


def evaluate_validation_report(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    exact = expectations.get("validation_report_exact") or {}
    if not exact:
        return None

    report = response.get("validationReport")
    if not isinstance(report, dict):
        analysis_report = response.get("analysisReport")
        report = analysis_report.get("validationReport") if isinstance(analysis_report, dict) else None
    if not isinstance(report, dict):
        return "Expected validationReport in response"

    if "passed" in exact and report.get("passed") != exact["passed"]:
        return f"Expected validationReport.passed={exact['passed']!r}, got {report.get('passed')!r}"

    findings = report.get("findings") or []
    if "findingCount" in exact and len(findings) != exact["findingCount"]:
        return f"Expected validationReport findingCount={exact['findingCount']!r}, got {len(findings)!r}"

    expected_codes = exact.get("findingCodes")
    if expected_codes is not None:
        actual_codes = [finding.get("code") for finding in findings if isinstance(finding, dict)]
        if actual_codes != expected_codes:
            return f"Expected validationReport findingCodes={expected_codes!r}, got {actual_codes!r}"

    return None


def evaluate_risk_notices(response: dict[str, Any], expectations: dict[str, Any]) -> str | None:
    exact = expectations.get("risk_notices_exact") or {}
    if not exact:
        return None

    notices = response.get("riskNotices")
    if not isinstance(notices, list):
        analysis_report = response.get("analysisReport")
        notices = analysis_report.get("riskNotices") if isinstance(analysis_report, dict) else None
    if not isinstance(notices, list):
        return "Expected riskNotices in response"

    if "count" in exact and len(notices) != exact["count"]:
        return f"Expected riskNotices count={exact['count']!r}, got {len(notices)!r}"

    expected_codes = exact.get("codes")
    if expected_codes is not None:
        actual_codes = [notice.get("code") for notice in notices if isinstance(notice, dict)]
        if actual_codes != expected_codes:
            return f"Expected riskNotices codes={expected_codes!r}, got {actual_codes!r}"

    return None


def resolve_repo_path(raw_path: str) -> Path:
    path = Path(raw_path)
    if path.is_absolute():
        return path
    return PROJECT_ROOT / path


def setup_case_resources(
    opener: request.OpenerDirector,
    case: dict[str, Any],
    base_url: str,
    token: str,
    timeout_seconds: int,
) -> list[dict[str, Any]]:
    setup = case.get("setup") or {}
    created_resources: list[dict[str, Any]] = []

    for document in setup.get("documents", []):
        path = resolve_repo_path(document["path"])
        fields = {
            "groupId": str(document["groupId"]),
            "name": document.get("name", path.name),
        }
        _, response = post_multipart(
            opener,
            f"{base_url.rstrip('/')}/documents/upload",
            fields=fields,
            files={"file": path},
            token=token,
            timeout_seconds=timeout_seconds,
        )
        document_data = response.get("data") or {}
        created_resources.append(
            {
                "type": "document",
                "id": document_data.get("id"),
            }
        )

    return created_resources


def cleanup_case_resources(
    opener: request.OpenerDirector,
    base_url: str,
    token: str,
    created_resources: list[dict[str, Any]],
    timeout_seconds: int,
) -> list[str]:
    cleanup_errors: list[str] = []
    for resource in reversed(created_resources):
        if resource.get("type") == "document" and resource.get("id") is not None:
            try:
                delete_resource(
                    opener,
                    f"{base_url.rstrip('/')}/documents/{resource['id']}",
                    token=token,
                    timeout_seconds=timeout_seconds,
                )
            except Exception as exc:
                cleanup_errors.append(f"{resource}: {exc}")
    return cleanup_errors


def build_case_result(
    case: dict[str, Any],
    passed: bool,
    reason: str,
    status_code: int | None,
    response_excerpt: dict[str, Any] | None,
    started_at: float,
    created_resources: list[dict[str, Any]],
    cleanup_errors: list[str],
    failure_stage: str | None = None,
) -> CaseResult:
    effective_failure_stage = failure_stage
    if passed and cleanup_errors:
        effective_failure_stage = "cleanup"
    return CaseResult(
        case_id=case["id"],
        category=case["category"],
        passed=passed,
        reason=reason,
        status_code=status_code,
        response_excerpt=response_excerpt,
        duration_seconds=round(time.monotonic() - started_at, 3),
        failure_stage=effective_failure_stage,
        setup_resources_created=created_resources,
        cleanup_succeeded=not cleanup_errors,
        cleanup_errors=cleanup_errors,
    )


def context_failure_stage(reason: str) -> str:
    lowered = reason.lower()
    if "document" in lowered or "retrieval" in lowered:
        return "retrieval"
    return "context"


def evaluate_case(
    opener: request.OpenerDirector,
    case: dict[str, Any],
    base_url: str,
    token: str,
    timeout_seconds: int,
) -> CaseResult:
    endpoint = f"{base_url.rstrip('/')}/analysis/query"
    expectations = case.get("expectations", {})
    created_resources: list[dict[str, Any]] = []
    started_at = time.monotonic()
    try:
        created_resources = setup_case_resources(opener, case, base_url, token, timeout_seconds)
        payload = resolve_focus_dataset_ids(opener, base_url, token, case["request"], timeout_seconds)
        status_code, response = post_json(opener, endpoint, payload, token=token, timeout_seconds=timeout_seconds)
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            f"HTTP {exc.code}: {detail[:300]}",
            exc.code,
            None,
            started_at,
            created_resources,
            cleanup_errors,
            "api",
        )
    except Exception as exc:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            f"Request failed: {exc}",
            None,
            None,
            started_at,
            created_resources,
            cleanup_errors,
            "api",
        )

    if expectations.get("success", True) and not response.get("success", False):
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            response.get("message", "API returned success=false"),
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "api",
        )

    rows = response.get("data") or []
    min_rows = expectations.get("min_rows")
    if min_rows is not None and len(rows) < min_rows:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            f"Expected at least {min_rows} rows, got {len(rows)}",
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "result",
        )

    required_columns_any = expectations.get("required_columns_any") or []
    if required_columns_any and not has_any_required_column(rows, required_columns_any):
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            f"None of the expected columns appeared: {required_columns_any}",
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "result",
        )

    summary = response.get("summary") or ""
    summary_contains_all = expectations.get("summary_contains_all") or []
    if summary_contains_all and not contains_all(summary, summary_contains_all):
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            f"Summary did not contain all required phrases: {summary_contains_all}",
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "summary",
        )

    summary_contains_any = expectations.get("summary_contains_any") or []
    if summary_contains_any and not contains_any(summary, summary_contains_any):
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            f"Summary did not contain any expected phrases: {summary_contains_any}",
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "summary",
        )

    if expectations.get("generated_sql_absent") and response.get("generatedSql"):
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            "Expected generatedSql to be absent",
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "execution",
        )

    context_summary_error = evaluate_context_summary(response, expectations)
    if context_summary_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            context_summary_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            context_failure_stage(context_summary_error),
        )

    route_decision_error = evaluate_route_decision(response, expectations)
    if route_decision_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            route_decision_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "route",
        )

    analysis_plan_error = evaluate_analysis_plan(response, expectations)
    if analysis_plan_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            analysis_plan_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "plan",
        )

    execution_logs_error = evaluate_execution_logs(response, expectations)
    if execution_logs_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            execution_logs_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "execution",
        )

    analysis_report_evidence_error = evaluate_analysis_report_evidence(response, expectations)
    if analysis_report_evidence_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            analysis_report_evidence_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "report",
        )

    validation_report_error = evaluate_validation_report(response, expectations)
    if validation_report_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            validation_report_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "validation",
        )

    risk_notices_error = evaluate_risk_notices(response, expectations)
    if risk_notices_error:
        cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
        return build_case_result(
            case,
            False,
            risk_notices_error,
            status_code,
            _excerpt(response),
            started_at,
            created_resources,
            cleanup_errors,
            "risk",
        )

    cleanup_errors = cleanup_case_resources(opener, base_url, token, created_resources, timeout_seconds)
    return build_case_result(
        case,
        True,
        "ok",
        status_code,
        _excerpt(response),
        started_at,
        created_resources,
        cleanup_errors,
    )


def _excerpt(response: dict[str, Any]) -> dict[str, Any]:
    return {
        "success": response.get("success"),
        "message": response.get("message"),
        "summary": response.get("summary"),
        "rows": len(response.get("data") or []),
        "artifactId": response.get("artifactId"),
        "generatedSqlPresent": bool(response.get("generatedSql")),
        "contextSummary": response.get("contextSummary"),
        "routeDecision": response.get("routeDecision"),
        "analysisPlan": _plan_excerpt(response.get("analysisPlan")),
        "executionLogs": _execution_logs_excerpt(response.get("executionLogs")),
        "validationReport": response.get("validationReport"),
        "riskNotices": response.get("riskNotices"),
        "analysisReport": _analysis_report_excerpt(response.get("analysisReport")),
    }


def _plan_excerpt(plan: dict[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(plan, dict):
        return None
    steps = plan.get("steps") or []
    return {
        "route": plan.get("route"),
        "objective": plan.get("objective"),
        "stepTypes": [step.get("type") for step in steps if isinstance(step, dict)],
        "stepCount": len(steps),
    }


def _execution_logs_excerpt(logs: list[dict[str, Any]] | None) -> dict[str, Any] | None:
    if not isinstance(logs, list):
        return None
    typed_logs = [log for log in logs if isinstance(log, dict)]
    return {
        "toolTypes": [log.get("toolType") for log in typed_logs],
        "stepTypes": [log.get("stepType") for log in typed_logs],
        "successes": [log.get("success") for log in typed_logs],
        "durationsMs": [log.get("durationMs") for log in typed_logs],
        "count": len(typed_logs),
    }


def _analysis_report_excerpt(report: dict[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(report, dict):
        return None
    evidence = report.get("evidence") if isinstance(report.get("evidence"), dict) else {}
    return {
        "rowCount": report.get("rowCount"),
        "recommendedChart": report.get("recommendedChart"),
        "generatedCodeOrSqlPresent": report.get("generatedCodeOrSqlPresent"),
        "evidence": {
            "datasetCount": evidence.get("datasetCount"),
            "documentChunkCount": evidence.get("documentChunkCount"),
            "documentNames": evidence.get("documentNames"),
            "documentStrategy": evidence.get("documentStrategy"),
        },
        "riskNoticeCount": len(report.get("riskNotices") or []),
        "executionLogCount": len(report.get("executionLogs") or []),
    }


def _request_excerpt(payload: dict[str, Any]) -> dict[str, Any]:
    query = str(payload.get("query") or "")
    excerpt: dict[str, Any] = {
        "groupId": payload.get("groupId"),
        "datasetId": payload.get("datasetId"),
        "focusDatasetNames": payload.get("focusDatasetNames"),
        "focusDatasetIds": payload.get("focusDatasetIds"),
        "query": query[:240],
    }
    return {key: value for key, value in excerpt.items() if value not in (None, "", [])}


def attach_case_metadata(result: CaseResult, case: dict[str, Any]) -> CaseResult:
    result.case_path = case.get("_path")
    result.tags = list(case.get("tags") or [])
    result.description = case.get("description")
    result.request_excerpt = _request_excerpt(case.get("request") or {})
    result.setup_documents_requested = len((case.get("setup") or {}).get("documents", []))
    return result


def _empty_summary_bucket() -> dict[str, int]:
    return {"total": 0, "passed": 0, "failed": 0}


def _add_to_summary_bucket(summary: dict[str, dict[str, int]], key: str, result: CaseResult) -> None:
    bucket = summary.setdefault(key, _empty_summary_bucket())
    bucket["total"] += 1
    if result.passed:
        bucket["passed"] += 1
    else:
        bucket["failed"] += 1


def build_report_summary(results: list[CaseResult]) -> dict[str, Any]:
    by_category: dict[str, dict[str, int]] = {}
    by_tag: dict[str, dict[str, int]] = {}
    by_failure_stage: dict[str, dict[str, int]] = {}

    for result in results:
        _add_to_summary_bucket(by_category, result.category, result)

        tags = result.tags or ["__untagged__"]
        for tag in tags:
            _add_to_summary_bucket(by_tag, tag, result)

        if result.failure_stage:
            _add_to_summary_bucket(by_failure_stage, result.failure_stage, result)

    return {
        "byCategory": dict(sorted(by_category.items())),
        "byTag": dict(sorted(by_tag.items())),
        "byFailureStage": dict(sorted(by_failure_stage.items())),
    }


def write_report(
    results: list[CaseResult],
    report_label: str,
    category: str,
    request_timeout_seconds: int,
    selected_tags: list[str] | None,
    suite: dict[str, Any] | None,
) -> Path:
    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    path = RUNS_DIR / f"{report_label}-run-{stamp}.json"
    success_count = sum(1 for item in results if item.passed)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "category": category,
        "suite": suite.get("id") if suite else None,
        "suite_description": suite.get("description") if suite else None,
        "total": len(results),
        "passed": success_count,
        "failed": len(results) - success_count,
        "request_timeout_seconds": request_timeout_seconds,
        "selected_tags": selected_tags or [],
        "summary": build_report_summary(results),
        "results": [item.__dict__ for item in results],
    }
    with path.open("w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
    return path


def print_summary(results: list[CaseResult], report_path: Path) -> None:
    passed = sum(1 for item in results if item.passed)
    failed = len(results) - passed
    print(f"Ran {len(results)} cases")
    print(f"Passed: {passed}")
    print(f"Failed: {failed}")
    if failed:
        print("\nFailures:")
        for item in results:
            if not item.passed:
                stage = item.failure_stage or "unknown"
                print(f"- {item.case_id} [{stage}]: {item.reason} ({item.duration_seconds}s)")
    print(f"\nReport written to: {report_path}")


def main() -> int:
    args = parse_args()
    try:
        suite = load_suite(args.suite) if args.suite else None
        category = suite.get("category", args.category) if suite else args.category
        tags = list(suite.get("tags") or []) if suite else []
        tags.extend(args.tag or [])
        cases = filter_cases_by_tag(
            filter_cases_by_id(filter_cases_by_suite(load_cases(category), suite), args.case_id),
            tags,
        )
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    if args.limit is not None:
        cases = cases[: args.limit]

    if not cases:
        print("No benchmark cases found.", file=sys.stderr)
        return 1

    if args.list_cases:
        print_case_list(cases)
        return 0

    definition_errors = validate_case_definitions(cases)
    if definition_errors:
        print("Benchmark case validation failed:", file=sys.stderr)
        for item in definition_errors:
            print(f"- {item}", file=sys.stderr)
        return 1

    opener = build_opener()
    try:
        token = authenticate(opener, args.base_url, args.username, args.password, args.request_timeout)
    except Exception as exc:
        print(f"Failed to authenticate benchmark runner: {exc}", file=sys.stderr)
        return 1

    results = [
        attach_case_metadata(evaluate_case(opener, case, args.base_url, token, args.request_timeout), case)
        for case in cases
    ]
    report_label = suite.get("id") if suite else category
    report_path = write_report(results, report_label, category, args.request_timeout, tags, suite)
    print_summary(results, report_path)
    return 0 if all(item.passed for item in results) else 2


if __name__ == "__main__":
    raise SystemExit(main())
