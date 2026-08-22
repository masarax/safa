#!/usr/bin/env python3
"""Validate normalized SAFA Macrobenchmark metrics against release budgets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def check_max(name: str, actual: float, maximum: float, failures: list[str]) -> None:
    if actual > maximum:
        failures.append(f"{name}: {actual:.2f} exceeds {maximum:.2f}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("results", type=Path, help="Normalized benchmark result JSON")
    parser.add_argument(
        "--budgets",
        type=Path,
        default=Path("benchmark/performance-budgets.json"),
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=None,
        help="Optional accepted baseline result JSON for regression checks",
    )
    args = parser.parse_args()

    budgets = read_json(args.budgets)
    results = read_json(args.results)
    failures: list[str] = []

    startup = budgets["startup"]
    frames = budgets["frames"]
    check_max("cold_ms_p50", float(results["cold_ms_p50"]), float(startup["cold_ms_p50_max"]), failures)
    check_max("cold_ms_p95", float(results["cold_ms_p95"]), float(startup["cold_ms_p95_max"]), failures)
    check_max("warm_ms_p50", float(results["warm_ms_p50"]), float(startup["warm_ms_p50_max"]), failures)
    check_max("frame_ms_p95", float(results["frame_ms_p95"]), float(frames["frame_ms_p95_max"]), failures)
    check_max("jank_percent", float(results["jank_percent"]), float(frames["jank_percent_max"]), failures)

    if args.baseline:
        baseline = read_json(args.baseline)
        max_regression = float(budgets["regression"]["max_percent_vs_accepted_baseline"])
        for metric in ("cold_ms_p50", "cold_ms_p95", "warm_ms_p50", "frame_ms_p95", "jank_percent"):
            accepted = float(baseline[metric])
            actual = float(results[metric])
            if accepted <= 0:
                failures.append(f"{metric}: accepted baseline must be positive")
                continue
            regression = ((actual - accepted) / accepted) * 100.0
            if regression > max_regression:
                failures.append(
                    f"{metric}: {regression:.2f}% regression exceeds {max_regression:.2f}%"
                )

    if failures:
        print("SAFA performance budget FAILED")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("SAFA performance budget passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
