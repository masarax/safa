# SAFA audit metadata policy

SAFA audit records are operational evidence, not a copy of business requests.

## Data recorded

For mutating requests the audit middleware records the authenticated user/account identity already known to the server, HTTP action and endpoint, masked source network prefix, response status, a generated/request correlation ID, safe numeric resource/local/server identifiers when present, a validated sync mutation identifier/operation, and names of changed top-level fields.

The middleware never stores request values. PINs, passwords, tokens, secrets, fingerprint/API credentials, uploaded files/base64 data, customer/supplier contact values, receiver identity/account values, notes, amounts or other raw business payload values are not copied into `audit_logs`.

The response exposes `X-SAFA-Request-ID`; support can ask for that value to correlate an incident without requesting credentials or financial payloads.

## Access

Audit data is database operational data. It must only be available to authorized production operators/administrators performing security, reliability or support investigations. It must not be exposed through a general tenant API, analytics export, client log, browser diagnostic payload or Android telemetry.

## Retention

`SAFA_AUDIT_RETENTION_DAYS` defines the required retention window (default example: 90 days). `php artisan audit:prune` removes records older than that window and is scheduled daily at 02:30 by Laravel's scheduler. Production must run `php artisan schedule:run`/the platform scheduler continuously enough for daily tasks to execute.

Any organization that requires a different period must set the environment value according to its documented legal/product policy. Longer-term legal archives, if required, must be handled outside the application database under separate encryption/access controls rather than by disabling pruning.

## Incident use

Start with correlation ID, user/account ID, endpoint/action, mutation/resource identifiers and result status. Do not request raw tokens/PINs or copy customer/remittance payloads into tickets. If a defect cannot be diagnosed from minimized metadata, add narrowly scoped privacy-reviewed telemetry rather than reverting to full-request logging.
