import argparse
import json
import os
import sys
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


@dataclass
class CaseResult:
    case_id: str
    category: str
    passed: bool
    reason: str
    status_code: int | None
    response_excerpt: dict[str, Any] | None


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


def build_opener() -> request.OpenerDirector:
    # Local benchmark traffic should never be sent through a system proxy.
    return request.build_opener(request.ProxyHandler({}))


def post_json(
    opener: request.OpenerDirector,
    url: str,
    payload: dict[str, Any],
    token: str | None = None,
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
    with opener.open(req, timeout=60) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def get_json(
    opener: request.OpenerDirector,
    url: str,
    token: str | None = None,
) -> tuple[int, dict[str, Any]]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, headers=headers, method="GET")
    with opener.open(req, timeout=60) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def post_multipart(
    opener: request.OpenerDirector,
    url: str,
    fields: dict[str, str],
    files: dict[str, Path],
    token: str | None = None,
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
    with opener.open(req, timeout=60) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def delete_resource(
    opener: request.OpenerDirector,
    url: str,
    token: str | None = None,
) -> tuple[int, dict[str, Any]]:
    headers: dict[str, str] = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, headers=headers, method="DELETE")
    with opener.open(req, timeout=60) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, json.loads(raw)


def authenticate(opener: request.OpenerDirector, base_url: str, username: str, password: str) -> str:
    auth_url = f"{base_url.rstrip('/')}/auth/signin"
    _, response = post_json(
        opener,
        auth_url,
        {"username": username, "password": password},
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
) -> dict[str, Any]:
    focus_names = request_payload.get("focusDatasetNames")
    group_id = request_payload.get("groupId")
    if not focus_names or group_id is None:
        return request_payload

    _, response = get_json(opener, f"{base_url.rstrip('/')}/groups/{group_id}/datasets", token=token)
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
) -> None:
    for resource in reversed(created_resources):
        if resource.get("type") == "document" and resource.get("id") is not None:
            try:
                delete_resource(
                    opener,
                    f"{base_url.rstrip('/')}/documents/{resource['id']}",
                    token=token,
                )
            except Exception:
                pass


def evaluate_case(
    opener: request.OpenerDirector,
    case: dict[str, Any],
    base_url: str,
    token: str,
) -> CaseResult:
    endpoint = f"{base_url.rstrip('/')}/analysis/query"
    expectations = case.get("expectations", {})
    created_resources: list[dict[str, Any]] = []
    try:
        created_resources = setup_case_resources(opener, case, base_url, token)
        payload = resolve_focus_dataset_ids(opener, base_url, token, case["request"])
        status_code, response = post_json(opener, endpoint, payload, token=token)
    except error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=f"HTTP {exc.code}: {detail[:300]}",
            status_code=exc.code,
            response_excerpt=None,
        )
    except Exception as exc:
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=f"Request failed: {exc}",
            status_code=None,
            response_excerpt=None,
        )

    if expectations.get("success", True) and not response.get("success", False):
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=response.get("message", "API returned success=false"),
            status_code=status_code,
            response_excerpt=_excerpt(response),
        )

    rows = response.get("data") or []
    min_rows = expectations.get("min_rows")
    if min_rows is not None and len(rows) < min_rows:
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=f"Expected at least {min_rows} rows, got {len(rows)}",
            status_code=status_code,
            response_excerpt=_excerpt(response),
        )

    required_columns_any = expectations.get("required_columns_any") or []
    if required_columns_any and not has_any_required_column(rows, required_columns_any):
        cleanup_case_resources(opener, base_url, token, created_resources)
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=f"None of the expected columns appeared: {required_columns_any}",
            status_code=status_code,
            response_excerpt=_excerpt(response),
        )

    summary = response.get("summary") or ""
    summary_contains_all = expectations.get("summary_contains_all") or []
    if summary_contains_all and not contains_all(summary, summary_contains_all):
        cleanup_case_resources(opener, base_url, token, created_resources)
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=f"Summary did not contain all required phrases: {summary_contains_all}",
            status_code=status_code,
            response_excerpt=_excerpt(response),
        )

    summary_contains_any = expectations.get("summary_contains_any") or []
    if summary_contains_any and not contains_any(summary, summary_contains_any):
        cleanup_case_resources(opener, base_url, token, created_resources)
        return CaseResult(
            case_id=case["id"],
            category=case["category"],
            passed=False,
            reason=f"Summary did not contain any expected phrases: {summary_contains_any}",
            status_code=status_code,
            response_excerpt=_excerpt(response),
        )

    cleanup_case_resources(opener, base_url, token, created_resources)
    return CaseResult(
        case_id=case["id"],
        category=case["category"],
        passed=True,
        reason="ok",
        status_code=status_code,
        response_excerpt=_excerpt(response),
    )


def _excerpt(response: dict[str, Any]) -> dict[str, Any]:
    return {
        "success": response.get("success"),
        "message": response.get("message"),
        "summary": response.get("summary"),
        "rows": len(response.get("data") or []),
        "artifactId": response.get("artifactId"),
    }


def write_report(results: list[CaseResult], category: str) -> Path:
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
                print(f"- {item.case_id}: {item.reason}")
    print(f"\nReport written to: {report_path}")


def main() -> int:
    args = parse_args()
    cases = load_cases(args.category)
    if args.limit is not None:
        cases = cases[: args.limit]

    if not cases:
        print("No benchmark cases found.", file=sys.stderr)
        return 1

    opener = build_opener()
    try:
        token = authenticate(opener, args.base_url, args.username, args.password)
    except Exception as exc:
        print(f"Failed to authenticate benchmark runner: {exc}", file=sys.stderr)
        return 1

    results = [evaluate_case(opener, case, args.base_url, token) for case in cases]
    report_path = write_report(results, args.category)
    print_summary(results, report_path)
    return 0 if all(item.passed for item in results) else 2


if __name__ == "__main__":
    raise SystemExit(main())
