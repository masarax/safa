#!/usr/bin/env python3
"""Fail CI when resolved dependencies contain unexcepted HIGH/CRITICAL OSV findings."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import pathlib
import sys
import urllib.error
import urllib.request
from typing import Any

OSV_QUERY_BATCH = "https://api.osv.dev/v1/querybatch"
BLOCKING_SCORE = 7.0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ecosystem", required=True)
    parser.add_argument("--dependencies", required=True, type=pathlib.Path)
    parser.add_argument("--allowlist", required=True, type=pathlib.Path)
    return parser.parse_args()


def read_dependencies(path: pathlib.Path) -> list[tuple[str, str]]:
    dependencies: set[tuple[str, str]] = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.rsplit(":", 1)
        if len(parts) != 2 or not all(parts):
            raise SystemExit(f"Invalid dependency coordinate in {path}: {line!r}")
        dependencies.add((parts[0], parts[1]))
    if not dependencies:
        raise SystemExit(f"No dependencies found in {path}")
    return sorted(dependencies)


def read_allowlist(path: pathlib.Path) -> list[dict[str, Any]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema") != 1 or not isinstance(data.get("exceptions"), list):
        raise SystemExit("OSV allowlist must use schema 1 with an exceptions array")

    today = dt.date.today()
    valid: list[dict[str, Any]] = []
    for entry in data["exceptions"]:
        vuln_id = str(entry.get("id", "")).strip()
        rationale = str(entry.get("rationale", "")).strip()
        expires_raw = str(entry.get("expires", "")).strip()
        if not vuln_id or not rationale or not expires_raw:
            raise SystemExit("Every OSV exception requires id, expires and rationale")
        try:
            expires = dt.date.fromisoformat(expires_raw)
        except ValueError as exc:
            raise SystemExit(f"Invalid OSV exception expiry for {vuln_id}: {expires_raw}") from exc
        if expires < today:
            raise SystemExit(f"Expired OSV exception must be removed or re-reviewed: {vuln_id}")
        valid.append(entry)
    return valid


def osv_query(ecosystem: str, dependencies: list[tuple[str, str]]) -> list[dict[str, Any]]:
    queries = [
        {"package": {"ecosystem": ecosystem, "name": name}, "version": version}
        for name, version in dependencies
    ]
    body = json.dumps({"queries": queries}, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(
        OSV_QUERY_BATCH,
        data=body,
        headers={"Content-Type": "application/json", "User-Agent": "safa-ci-osv/1"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise SystemExit(f"OSV query failed closed: {exc}") from exc

    results = payload.get("results")
    if not isinstance(results, list) or len(results) != len(dependencies):
        raise SystemExit("OSV returned an unexpected result shape")
    return results


def round_up_tenth(value: float) -> float:
    return math.ceil((value - 1e-10) * 10.0) / 10.0


def cvss3_score(vector: str) -> float | None:
    if not vector.startswith(("CVSS:3.0/", "CVSS:3.1/")):
        return None
    metrics: dict[str, str] = {}
    for token in vector.split("/")[1:]:
        key, sep, value = token.partition(":")
        if not sep:
            return None
        metrics[key] = value

    try:
        scope = metrics["S"]
        av = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.20}[metrics["AV"]]
        ac = {"L": 0.77, "H": 0.44}[metrics["AC"]]
        pr_table = (
            {"N": 0.85, "L": 0.68, "H": 0.50}
            if scope == "C"
            else {"N": 0.85, "L": 0.62, "H": 0.27}
        )
        pr = pr_table[metrics["PR"]]
        ui = {"N": 0.85, "R": 0.62}[metrics["UI"]]
        cia = {"H": 0.56, "L": 0.22, "N": 0.0}
        confidentiality = cia[metrics["C"]]
        integrity = cia[metrics["I"]]
        availability = cia[metrics["A"]]
    except KeyError:
        return None

    iss = 1.0 - (1.0 - confidentiality) * (1.0 - integrity) * (1.0 - availability)
    if scope == "U":
        impact = 6.42 * iss
    elif scope == "C":
        impact = 7.52 * (iss - 0.029) - 3.25 * ((iss - 0.02) ** 15)
    else:
        return None
    if impact <= 0:
        return 0.0

    exploitability = 8.22 * av * ac * pr * ui
    base = min(impact + exploitability, 10.0) if scope == "U" else min(1.08 * (impact + exploitability), 10.0)
    return round_up_tenth(base)


def severity_score(vulnerability: dict[str, Any]) -> tuple[float | None, str]:
    database_specific = vulnerability.get("database_specific")
    if isinstance(database_specific, dict):
        text = str(database_specific.get("severity", "")).upper()
        if text == "CRITICAL":
            return 9.0, "CRITICAL"
        if text == "HIGH":
            return 7.0, "HIGH"
        if text == "MODERATE" or text == "MEDIUM":
            return 4.0, text
        if text == "LOW":
            return 1.0, "LOW"

    best: float | None = None
    for severity in vulnerability.get("severity") or []:
        raw = str(severity.get("score", "")).strip()
        if not raw:
            continue
        try:
            numeric = float(raw)
        except ValueError:
            numeric = cvss3_score(raw)
        if numeric is not None and (best is None or numeric > best):
            best = numeric
    if best is None:
        return None, "UNKNOWN"
    if best >= 9.0:
        label = "CRITICAL"
    elif best >= 7.0:
        label = "HIGH"
    elif best >= 4.0:
        label = "MEDIUM"
    else:
        label = "LOW"
    return best, label


def is_allowed(vulnerability: dict[str, Any], package: str, allowlist: list[dict[str, Any]]) -> bool:
    identifiers = {str(vulnerability.get("id", "")), *(str(a) for a in vulnerability.get("aliases") or [])}
    for entry in allowlist:
        if str(entry.get("id", "")) not in identifiers:
            continue
        scoped_package = str(entry.get("package", "")).strip()
        if scoped_package and scoped_package != package:
            continue
        return True
    return False


def main() -> int:
    args = parse_args()
    dependencies = read_dependencies(args.dependencies)
    allowlist = read_allowlist(args.allowlist)
    results = osv_query(args.ecosystem, dependencies)

    blocking: list[str] = []
    findings = 0
    for (package, version), result in zip(dependencies, results, strict=True):
        for vulnerability in result.get("vulns") or []:
            findings += 1
            score, label = severity_score(vulnerability)
            vuln_id = str(vulnerability.get("id", "UNKNOWN"))
            allowed = is_allowed(vulnerability, package, allowlist)
            suffix = " [EXCEPTION]" if allowed else ""
            score_text = "unknown" if score is None else f"{score:.1f}"
            print(f"{label:8} score={score_text:>7} {package}:{version} {vuln_id}{suffix}")
            if score is not None and score >= BLOCKING_SCORE and not allowed:
                blocking.append(f"{package}:{version} {vuln_id} {label} {score_text}")

    print(f"Scanned {len(dependencies)} resolved {args.ecosystem} dependencies; OSV findings={findings}; blocking={len(blocking)}")
    if blocking:
        print("Blocking HIGH/CRITICAL vulnerabilities:", file=sys.stderr)
        for item in blocking:
            print(f"- {item}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
