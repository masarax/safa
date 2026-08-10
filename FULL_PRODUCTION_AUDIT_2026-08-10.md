# SAFA — Full Production Audit

Date: 2026-08-10
Branch: `fix/full-production-audit`

## Scope

This audit covers the Android Kotlin/Jetpack Compose client, Laravel backend, API contracts, account authorization, synchronization, database migration/update UX, functional flows, UI consistency, error handling, and production-readiness risks.

## Critical findings

### 1. Business data authority

The application has a substantial synchronization/reconciliation layer, but many screens still render directly from Room flows. Room is therefore a cache and UI data source, while the server must remain authoritative. The required server-first contract is documented in `COMPLETE SERVER-FIRST_DATA.md`.

Current safe direction:

```text
ONLINE: UI → API → Laravel/MySQL → response → cache → UI
OFFLINE: UI → explicit pending queue → server when online → cache → UI
```

The current code already reconciles server-created/updated/deleted records in `SyncManager`, but not every mutation and every screen is fully server-first yet.

### 2. Account identity vs account context

The original architecture conflated user identity and account identity in several places. `owner_user_id` is now present on `accounts`, and `AuthorizeAccountContext` resolves an owned/shared account instead of blindly using the authenticated user ID.

Android now persists `active_account_id` and the API interceptor sends `X-SAFA-ACCOUNT-ID` on authenticated requests. The backend still authorizes the requested account server-side.

### 3. Migration/update page bug

`InstallerController` referenced `Schema::hasTable()` / `Schema::hasColumn()` without importing/resolving the Schema facade. Because the pending-migration detector catches exceptions, this could silently return an empty pending list. A namespace bridge was added so the existing controller resolves the Laravel Schema facade correctly.

The update page already uses a one-time session update token. After successful migration it redirects to `/`, where the pending migration list is recalculated; therefore the update option disappears automatically when the schema is current.

### 4. REST contract mismatch

The Android API contract previously declared customer/supplier/transaction GET responses as raw lists, while Laravel returns an object containing `status` plus an entity array. The contracts have been changed to `Response<Map<String, Any?>>`.

Transaction PUT/DELETE contracts were also added because the backend exposes them.

### 5. Account-switching UX/architecture risk

The Settings UI and top-level UI contain operator-oriented account switching concepts. An operator/user identity is not the same thing as an account context. A local operator switch must not silently change the authenticated user.

A server-authoritative `AccountContextController` has been added for account listing, switching, and sharing, and Android API contracts were added for these operations. The existing legacy account-switch endpoints remain a compatibility concern and should be removed after the Android UI is migrated to the new account-context flow.

## Functional findings still requiring follow-up

1. Supplier deposits, wallet batches, wallet ledgers, expenses/income, and daily rates need a complete screen-by-screen server-first mutation audit. `SyncManager` supports reconciliation for these entities, but some ViewModel methods still create Room records before server confirmation.
2. Offline queue UX must visibly distinguish `SERVER-SYNCED`, `PENDING`, `OFFLINE`, and `SYNC FAILED`. A local queued mutation must never look identical to a confirmed server mutation.
3. Every create/update/delete callback should be reviewed for fake-success behavior. In particular, callbacks that close a form after local queueing should be changed to show a pending state rather than a success state.
4. Operator management methods still update Room after catching API exceptions. These flows need the same strict server-confirmed mutation rule as business entities.
5. Daily rates currently have a local fallback/default path. A dedicated account-scoped server rates endpoint should become the authoritative source.
6. The Android UI still contains hardcoded visual colors in `MainActivity` and some screens. These bypass the global design tokens.

## UI/UX findings

### Positive

- Material 3 / Jetpack Compose is used.
- Screen transitions already use `AnimatedContent` with forward/backward direction.
- Customer screen has search, sorting, filtering, profile/add flows and contact picker support.
- A shared design component file exists.

### Problems

- Global design tokens and screen-level hardcoded colors are inconsistent.
- The top app bar and bottom navigation still contain legacy crimson/red values while the current SAFA branding is emerald/gold.
- Some screens use highly dense 9–11sp text and compact controls that may reduce accessibility and readability.
- Loading, error, and offline states are not consistently modeled as first-class UI states.
- Several asynchronous settings/network operations catch exceptions and only log them, leaving the user without feedback.
- Account/operator terminology is mixed in the UI.

### Implemented UI foundation change

The global `Color.kt` tokens were unified around SAFA deep emerald and premium gold with consistent financial status colors. Screen-level hardcoded colors remain to be migrated to these tokens.

## Security findings

- Account-scoped business controllers use `AuthorizeAccountContext` and account-scoped queries.
- The active account header is now sent by Android, but the backend must continue to verify it; the header must never be treated as authorization by itself.
- The existing HMAC API secret is still configured through Android build configuration. A secret embedded in an APK is recoverable by an attacker and must not be treated as a true server-only secret.
- The public migration capability uses a one-time session token and CSRF protection, but production environments should additionally restrict migration access at the web/server layer where possible.
- Production verification of token revocation and user deletion still requires a live backend/device test.

## Database/migration UX contract

Desired behavior:

```text
/open index
   ↓
pending migrations detected
   ↓
show migration/update screen
   ↓
click Run Database Migration
   ↓
one-time authorization consumed
   ↓
Artisan migrate --force
   ↓
clear config/cache/view
   ↓
redirect /
   ↓
no pending migrations
   ↓
normal welcome page
```

This is now the intended flow. Existing production data must not be truncated or reset.

## Verification status

### Verified from repository source

- Account ownership model exists.
- Account context authorization exists.
- Sync down reconciliation exists for customers, suppliers, transactions, deposits, expenses/incomes, wallet ledgers and wallet batches.
- Customer/supplier/transaction REST routes exist and are account-scoped.
- Android has durable outbox/retry infrastructure.
- Android API interceptor now propagates active account context.
- Global SAFA color tokens were unified.

### Not yet physically verified from this environment

- cPanel production MySQL migration execution.
- Physical Android APK build/install.
- Real production login with a live user.
- Creating a customer from the physical APK and checking the production MySQL row.
- Editing/deleting the same record from the APK and checking production state.
- Server-side deletion followed by Android refresh.
- Two-user account isolation against the live database.
- Offline/online network transition on a physical device.

These must not be claimed as completed until actually executed.

## Recommended final gate

Before merging to `main`:

1. Run Laravel tests.
2. Run Android unit tests.
3. Build a release/debug APK.
4. Run production account-isolation tests.
5. Run production create/update/delete tests.
6. Run migration/update-page test against a database snapshot.
7. Test server-side deletion and revocation.
8. Test offline queue and recovery.
9. Replace remaining hardcoded UI colors with design tokens.
10. Remove legacy operator-as-account switching behavior.
11. Add true server rates endpoint and migrate daily-rate UI to it.
12. Only then merge the audit branch.
