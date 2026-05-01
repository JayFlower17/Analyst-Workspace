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
    case_path: str | None = None
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
        print(
            f"{case.get('category')}\t{case.get('id')}\t"
            f"setup_documents={setup_documents}\t{case.get('_path')}"
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
) -> CaseResult:
    return CaseResult(
        case_id=case["id"],
        category=case["category"],
        passed=passed,
        reason=reason,
        status_code=status_code,
        response_excerpt=response_excerpt,
        duration_seconds=round(time.monotonic() - started_at, 3),
        setup_resources_created=created_resources,
        cleanup_succeeded=not cleanup_errors,
        cleanup_errors=cleanup_errors,
    )


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
    result.request_excerpt = _request_excerpt(case.get("request") or {})
    result.setup_documents_requested = len((case.get("setup") or {}).get("documents", []))
    return result


def write_report(results: list[CaseResult], category: str, request_timeout_seconds: int) -> Path:
    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    path = RUNS_DIR / f"{category}-run-{stamp}.json"
    success_count = sum(1 for item in results if item.passed)
    payload = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "category": category,
        "total": len(results),
        "passed": success_count,
        "failed": len(results) - success_count,
        "request_timeout_seconds": request_timeout_seconds,
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
                print(f"- {item.case_id}: {item.reason} ({item.duration_seconds}s)")
    print(f"\nReport written to: {report_path}")


def main() -> int:
    args = parse_args()
    try:
        cases = filter_cases_by_id(load_cases(args.category), args.case_id)
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
    report_path = write_report(results, args.category, args.request_timeout)
    print_summary(results, report_path)
    return 0 if all(item.passed for item in results) else 2


if __name__ == "__main__":
    raise SystemExit(main())
