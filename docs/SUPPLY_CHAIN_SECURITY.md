# SAFA software supply-chain security policy

This document defines the repository-enforced dependency, static-analysis, SBOM and vulnerability-triage contract for the Android and Laravel production surfaces.

## Automated gates

Every relevant pull request and every push to `main` runs `Supply Chain Security CI` in addition to the normal Android/Laravel production suites.

- Laravel production dependencies are checked with Composer's locked advisory database using `composer audit --locked --no-dev`. Abandoned production packages are treated as failures.
- Production PHP source is scanned by `scripts/php-security-scan.php` for direct process execution/evaluation/deserialization primitives, disabled TLS verification, interpolated raw SQL and insecure production environment defaults. This is a deliberately small fail-closed repository policy layered on Laravel tests and PHP syntax checks.
- Android's exact resolved `releaseRuntimeClasspath` is exported by `:app:safaResolvedReleaseDependencies`. `scripts/osv-scan.py` submits those resolved Maven coordinates to OSV and blocks unexcepted HIGH/CRITICAL findings. This includes transitive dependencies rather than scanning only `libs.versions.toml`.
- `lintRelease` is the Android source-security/static-analysis gate and runs against the production build variant.
- `scripts/check-workflow-action-pins.sh` continues to reject GitHub Actions that are not pinned to immutable commit SHAs.
- Production backend and Android CycloneDX 1.5 SBOMs are generated from `composer.lock` and the resolved Android release graph. The Android signed-release workflow retains the SBOM, checksum, mapping and exact validated commit identity together.

The security workflow also runs daily so a newly disclosed advisory can fail without waiting for a source-code change.

## Dependency updates

Dependabot checks Composer, Gradle/Maven and GitHub Actions weekly. Minor and patch updates are grouped per ecosystem to reduce update noise. Dependency PRs are never auto-merged: they must satisfy the same production CI, security gates and release checks as human-authored changes.

Major upgrades are reviewed separately because Android platform/toolchain and Laravel dependency changes can require migrations or compatibility work.

## Severity and response SLA

The owner of the SAFA repository is the default security triage owner unless repository administration explicitly assigns another maintainer.

| Severity | Required response | Production remediation target |
| --- | --- | --- |
| Critical (CVSS >= 9.0 or advisory `CRITICAL`) | Triage immediately; stop affected release/promotion | 24 hours |
| High (CVSS >= 7.0) | Triage same business day | 72 hours |
| Medium | Triage within 5 business days | 30 days or next planned release |
| Low / unknown | Review during normal dependency maintenance | Risk-based |

A vulnerability affecting authentication, cryptographic key material, tenant isolation, transaction integrity or remotely reachable code execution is escalated one operational priority even when the upstream score is lower.

## Controlled vulnerability exceptions

`security/osv-allowlist.json` is the only repository exception mechanism for the Android OSV gate. An exception is permitted only when all of the following are true:

1. the exact OSV/GHSA/CVE identifier is recorded;
2. an expiry date is recorded and is no longer than 30 days from approval for Critical/High findings;
3. a concrete rationale explains why the vulnerable path is not reachable or what compensating control exists;
4. `package` is set when the identifier could apply to multiple packages;
5. a follow-up dependency upgrade/removal is already planned.

Expired exceptions make CI fail. Never add wildcard vulnerability IDs, permanent exceptions or exceptions whose only rationale is that an upgrade is inconvenient. Composer advisory failures have no file-based bypass; a temporary Composer exception requires a reviewed change to the locked dependency graph or a documented upstream advisory exclusion supported by Composer itself.

## SBOM and provenance handling

SBOMs contain package coordinates, versions, license identifiers where Composer supplies them, repository identity and commit SHA. They must not contain credentials, tokens, PINs, environment variables or business data. Security-CI SBOM artifacts are retained for 30 days. Signed Android release artifacts retain the production SBOM beside the signed package, R8 mapping, SHA-256 checksums and the release-validation evidence.

For an incident, identify the deployed/released commit first, retrieve its SBOM, then match affected package coordinates to the advisory. Do not infer exposure from a version catalog alone because transitive resolution can differ from declared versions.

## Scanner availability and failure policy

Advisory/network scanners fail closed. If OSV or Composer advisory infrastructure is unavailable, the security gate remains failed rather than silently approving a dependency change. A maintainer may retry the job after the upstream service recovers; bypassing the gate by deleting or weakening the scan is not an incident workaround.

Security telemetry and scanner logs must never print repository secrets or application credentials. The OSV scanner sends only public Maven package names and versions.
