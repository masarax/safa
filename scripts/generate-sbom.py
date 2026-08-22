#!/usr/bin/env python3
"""Generate a compact CycloneDX 1.5 SBOM tied to the SAFA commit identity."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import urllib.parse
import uuid
from typing import Any


def args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=["android", "backend"], required=True)
    parser.add_argument("--input", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA", "development"))
    parser.add_argument("--version", default="development")
    return parser.parse_args()


def purl_part(value: str) -> str:
    return urllib.parse.quote(value, safe="._-")


def android_components(path: pathlib.Path) -> list[dict[str, Any]]:
    components: list[dict[str, Any]] = []
    seen: set[tuple[str, str, str]] = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        coordinate = raw.strip()
        if not coordinate or coordinate.startswith("#"):
            continue
        split = coordinate.split(":")
        if len(split) != 3 or not all(split):
            raise SystemExit(f"Invalid Maven coordinate: {coordinate!r}")
        group, name, version = split
        key = (group, name, version)
        if key in seen:
            continue
        seen.add(key)
        purl = f"pkg:maven/{purl_part(group)}/{purl_part(name)}@{purl_part(version)}"
        components.append({
            "type": "library",
            "bom-ref": purl,
            "group": group,
            "name": name,
            "version": version,
            "purl": purl,
        })
    return sorted(components, key=lambda item: item["bom-ref"])


def backend_components(path: pathlib.Path) -> list[dict[str, Any]]:
    lock = json.loads(path.read_text(encoding="utf-8"))
    packages = lock.get("packages")
    if not isinstance(packages, list):
        raise SystemExit("composer.lock does not contain a packages array")
    components: list[dict[str, Any]] = []
    for package in packages:
        name = str(package.get("name", "")).strip()
        version = str(package.get("version", "")).strip()
        if not name or not version or "/" not in name:
            continue
        vendor, package_name = name.split("/", 1)
        purl = f"pkg:composer/{purl_part(vendor)}/{purl_part(package_name)}@{purl_part(version)}"
        component: dict[str, Any] = {
            "type": "library",
            "bom-ref": purl,
            "group": vendor,
            "name": package_name,
            "version": version,
            "purl": purl,
        }
        license_entries = package.get("license")
        if isinstance(license_entries, list) and license_entries:
            component["licenses"] = [{"license": {"id": str(value)}} for value in license_entries if value]
        components.append(component)
    return sorted(components, key=lambda item: item["bom-ref"])


def main() -> int:
    parsed = args()
    commit = parsed.commit.strip() or "development"
    version = parsed.version.strip() or "development"
    components = android_components(parsed.input) if parsed.target == "android" else backend_components(parsed.input)
    if not components:
        raise SystemExit("Refusing to write an empty SBOM")

    serial = uuid.uuid5(uuid.NAMESPACE_URL, f"https://github.com/masarax/safa/{commit}/{parsed.target}/{version}")
    timestamp = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    bom = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            "timestamp": timestamp,
            "component": {
                "type": "application",
                "name": f"safa-{parsed.target}",
                "version": version,
                "properties": [
                    {"name": "safa:repository", "value": "masarax/safa"},
                    {"name": "safa:commit", "value": commit},
                ],
            },
        },
        "components": components,
    }
    parsed.output.parent.mkdir(parents=True, exist_ok=True)
    parsed.output.write_text(json.dumps(bom, ensure_ascii=False, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    print(f"Wrote {parsed.target} CycloneDX SBOM with {len(components)} production components to {parsed.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
