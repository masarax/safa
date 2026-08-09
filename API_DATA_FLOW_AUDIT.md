# SAFA — API DATA FLOW AUDIT REPORT

## 1. Complete API Route Matrix

| Entity | Route | HTTP Method | Controller Method | Scoping |
|---|---|---|---|---|
| Auth | `/api/auth/login` | POST | `AuthJWTController@login` | Mobile + PIN Verification |
| Auth | `/api/auth/shared-accounts` | GET | `AuthJWTController@getSharedAccounts` | `shared_with_user_id` |
| Auth | `/api/auth/switch-account` | POST | `AuthJWTController@switchAccount` | Owner / Share Permission |
| Customer | `/api/customers` | GET | `CustomerController@index` | `account_id` Scoped |
| Customer | `/api/customers` | POST | `CustomerController@store` | `account_id` Scoped |
| Customer | `/api/customers/{id}` | PUT | `CustomerController@update` | `account_id` Scoped |
| Customer | `/api/customers/{id}` | DELETE | `CustomerController@destroy` | `account_id` Scoped |
| Supplier | `/api/suppliers` | GET | `SupplierController@index` | `account_id` Scoped |
| Supplier | `/api/suppliers` | POST | `SupplierController@store` | `account_id` Scoped |
| Supplier | `/api/suppliers/{id}` | PUT | `SupplierController@update` | `account_id` Scoped |
| Supplier | `/api/suppliers/{id}` | DELETE | `SupplierController@destroy` | `account_id` Scoped |
| Transaction | `/api/transactions` | GET | `TransactionController@index` | `account_id` Scoped |
| Transaction | `/api/transactions` | POST | `TransactionController@store` | `account_id` Scoped |
| Transaction | `/api/transactions/{id}` | PUT | `TransactionController@update` | `account_id` Scoped |
| Transaction | `/api/transactions/{id}` | DELETE | `TransactionController@destroy` | `account_id` Scoped |
| Sync | `/api/sync/up` | POST | `SyncController@syncUp` | `account_id` Scoped |
| Sync | `/api/sync/down` | GET | `SyncController@syncDown` | `account_id` Scoped |

---

## 2. Real-Time Bidirectional Data Flow Diagram
```text
[ Android Compose UI ]
        │
        ▼ (User Action)
[ SafaViewModel ]
        │
        ▼ (Network Call)
[ Retrofit / ApiService ]
        │
        ▼ (HTTP + HMAC / JWT)
[ Laravel Backend Routes ]
        │
        ▼ (Middleware Verification)
[ CustomerController / SyncController ]
        │
        ▼ (SQL Transaction)
[ cPanel MySQL DB (Single Source of Truth) ]
        │
        ▼ (JSON Response)
[ SyncManager / Room Cache Update & Local Purge ]
        │
        ▼ (StateFlow Collect)
[ Android Compose UI ]
```
