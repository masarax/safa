# SAFA — MULTI-ACCOUNT AUTHORIZATION AUDIT REPORT

## 1. Multi-Account Security Model Overview
In the SAFA ecosystem, an authenticated user possesses exactly **one primary identity** (`user_id`). The user may be granted access to multiple business accounts (`account_id`) via ownership or explicit account sharing (`UserAccountShare`).

---

## 2. Authorization Enforcers
1. **JWT Active Context**: When switching accounts via `POST /api/auth/switch-account`, the backend verifies whether `currentUser` owns the target `account_id` or possesses an active `UserAccountShare` record. If verified, a fresh JWT access token containing `user_id` and `account_id` is generated.
2. **Backend Query Scoping**: Controllers (`CustomerController`, `SupplierController`, `TransactionController`, `SyncController`) resolve `$accountId` from request headers (`X-SAFA-ACCOUNT-ID` or JWT claims) and restrict SQL queries exclusively to `where('account_id', $accountId)`.
3. **No Unscoped Fallbacks**: Unscoped queries like `Account::all()`, `User::all()`, or `User::first()` have been eliminated from authorization logic.

---

## 3. Account Access Flow
```text
  Authenticated User (User ID: 25)
               │
               ▼
   GET /api/auth/shared-accounts
               │
               ▼
   Select Target Account ID (e.g. Account 7)
               │
               ▼
   POST /api/auth/switch-account
               │
    ┌──────────┴──────────┐
    ▼                     ▼
Is Owner / Shared?     Not Authorized
    │                     │
    ▼                     ▼
200 OK + JWT           403 Forbidden
```

---

## 4. IDOR & Escalation Defense
* Malicious callers attempting to access `GET /api/customers` for an unauthorized `account_id` are rejected by the backend middleware and scoped query constraints.
* Session invalidation: If a user is deactivated or deleted in cPanel MySQL, requests receive `401 Unauthorized` / `403 Forbidden`, causing the Android application to revoke local sessions and prompt login.
