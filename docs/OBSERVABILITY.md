# Production observability, SLOs and incident response

Issue: #228

## Privacy boundary

SAFA telemetry is operational metadata, never a shadow copy of business data. The backend request observer records only the HTTP method, route template, status class and latency bucket. It never reads request or response bodies. Mobile telemetry accepts a fixed event enum plus release, endpoint/reason enums, bounded counters and a SHA-256 stack-coordinate fingerprint. Do not add PINs, access/refresh/session/device/fingerprint tokens, API secrets, mobile numbers, names, full receiver/account numbers, free-text exception messages or financial payloads.

`/api/ops/metrics` and `/api/ops/synthetic-persistence` require `SAFA_OPS_METRICS_KEY`. Unauthorized callers receive 404. Mobile telemetry is protected by the existing public mobile-client identifier boundary and accepts no arbitrary dimensions.

## Service-level objectives

Measure rolling 30-day objectives and alert on short/long burn rather than one isolated event.

| User-visible flow | SLI | SLO |
| --- | --- | --- |
| Login / token refresh | successful authenticated operations / attempts | 99.9% |
| Customer/supplier/wallet/transaction writes | non-5xx successful writes / attempts | 99.9% |
| Foreground/background sync | successful reconciliations / attempts | 99.5% |
| Core API latency | route-template request latency | p95 <= 750 ms; p99 <= 1500 ms |
| Database | connectivity and connection saturation | healthy; <80% sustained saturation |
| Android stability | crash + ANR events / active telemetry sessions | >=99.5% crash/ANR-free target |

The in-process metrics endpoint reports cumulative bounded histograms. Production dashboards must compute rates/deltas between scrapes; do not interpret cumulative counters as a time-window rate.

## Required dashboards

At minimum expose:

- API traffic, 5xx/error rate and p50/p95/p99 latency by route template and release/build;
- login and auth-refresh failures;
- MySQL probe latency, `Threads_connected`, `Threads_running`, max-used connections and connection saturation;
- sync success/failure/retry counts, duration, downloaded bytes, maximum pending-outbox count and oldest pending age;
- Android crash/ANR/nonfatal counts by release and stack fingerprint;
- synthetic authentication + rollback persistence result.

The backend emits `X-SAFA-REQUEST-ID` on API responses. Support may request this opaque value; it contains no account identity or business data.

## Alerts

Actionable production alerts:

1. Synthetic business-flow failure on two consecutive 15-minute runs: page the primary operator; verify health endpoint, auth/login, DB and last deployment.
2. Database connectivity false: page immediately. Connection saturation >=90% on the scheduled probe is a hard failure; sustained >=80% is a warning requiring capacity/query investigation.
3. API 5xx error-budget burn: alert when 1-hour error rate exceeds 5% or when 6-hour rate exceeds 2x the applicable SLO budget.
4. Core route p95 >750 ms for 15 minutes or p99 >1500 ms for 15 minutes: investigate application/DB saturation and recent releases.
5. Sync success <99.5%, retry/failure spike, pending backlog or oldest-pending age rising across successive scrapes: investigate auth refresh, cursor protocol and outbox processing.
6. Android crash/ANR rate regression by release: stop staged rollout when the new release materially exceeds the previous stable release or violates the 99.5% crash/ANR-free target.

## Synthetic configuration

Create a dedicated, non-financial synthetic user/account. Never point the persistence probe at a customer account. Configure production runtime:

- `SAFA_OPS_METRICS_KEY`
- `SAFA_SYNTHETIC_ACCOUNT_ID`

Configure GitHub Actions secrets:

- `SAFA_SYNTHETIC_MOBILE`
- `SAFA_SYNTHETIC_PIN`
- `SAFA_MOBILE_API_KEY`
- `SAFA_OPS_METRICS_KEY`

Configure repository variable `SAFA_PRODUCTION_BASE_URL` (defaults to `https://safa.masarax.com` in the script).

The synthetic authenticates through the real login endpoint. Its persistence probe inserts and reads a `Customer` only inside one DB transaction and rolls it back before returning, so it leaves no business row, tombstone or sync-journal history. The probe then logs out the authenticated session.

## First response

1. Capture the failing workflow/run time and any safe `X-SAFA-REQUEST-ID`; never paste credentials into issues or chat.
2. Check `/api/auth/health` and `/api/ops/metrics` using the protected ops key.
3. Determine whether impact is API-wide, DB saturation, auth-only, sync-only or Android-release-specific.
4. Compare the affected release/build against the prior stable release.
5. For a release regression, stop rollout and forward-fix with a new version. For DB saturation, inspect slow/blocked queries and connection usage before scaling blindly.
6. After recovery, verify two consecutive synthetic successes and that error/latency/backlog signals return inside SLO.

## Retention and ownership

The application itself stores only bounded aggregates and at most 100 pending sanitized Android events. External production monitoring should retain high-resolution operational metrics for 30 days and aggregate trend data for up to 13 months, subject to organizational privacy policy. Raw business payloads are never eligible for telemetry retention.

Primary ownership: backend/API and database alerts belong to the service operator; Android crash/ANR and sync-release regressions belong to the Android release owner. Any alert without an actionable owner must be removed or redesigned rather than left noisy.
