# Laravel API & Ecosystem Specification

## 1. Ecosystem Overview
The system operates as an offline-first Android application powered by a Laravel REST API.
- **Android App (com.safa.account)**: The primary interface. Stores data locally in SQLite (Room) for immediate access and offline capability.
- **Laravel Backend**: The central source of truth. Handles authentication, data persistence, and cross-device synchronization.

## 2. Authentication Flow
- The app uses Laravel Sanctum for API token authentication.
- **Login**: `POST /api/login` -> Returns Bearer Token. Token is stored securely (EncryptedSharedPreferences) on the device.
- All subsequent API requests must include `Authorization: Bearer {token}`.

## 3. Multi-Tenant Architecture
- The API must support multi-tenancy based on the User and their Accounts.
- A single User can have multiple Accounts (Books).
- Every resource (Customer, Supplier, Transaction) belongs to an `account_id`.
- The Android app must send `X-Account-ID: {id}` in the headers for all data operations, or include it in the JSON payload, to ensure data is scoped correctly.

## 4. API Endpoint Contract

### 4.1 Authentication
- `POST /api/auth/login` (Body: email, password, device_name)
- `POST /api/auth/register`
- `POST /api/auth/logout`

### 4.2 Accounts
- `GET /api/accounts` (List user's accounts)
- `POST /api/accounts` (Create account: name, currency)
- `PUT /api/accounts/{id}`

### 4.3 Customers & Suppliers
- `GET /api/customers?account_id={id}`
- `POST /api/customers` (Body: name, phone, account_id, remote_id/uuid)
- `GET /api/suppliers?account_id={id}`
- `POST /api/suppliers`

### 4.4 Transactions
- `GET /api/transactions?account_id={id}&since={timestamp}` (For sync)
- `POST /api/transactions`
  - Body: `uuid`, `account_id`, `amount`, `type`, `customer_id`, `supplier_id`, `rate`, `timestamp`.
- `PUT /api/transactions/{id}`
- `DELETE /api/transactions/{id}`

## 5. Synchronization Strategy (SyncManager)
To handle offline-first requirements without complex conflict resolution:
1. **UUIDs**: The Android app generates a UUID for every entity (Customer, Transaction) upon creation.
2. **Push**: The app posts new/updated records to the Laravel API using these UUIDs. The backend uses `updateOrCreate` based on the UUID.
3. **Pull**: The app requests records modified after the `last_sync_timestamp`. The backend returns records, and the app updates its local Room database.
4. **Soft Deletes**: Deletions should be soft-deletes (flagged `is_deleted = 1`). The sync engine pushes the delete flag, and the backend marks it deleted, preventing it from being synced back as a new record.

## 6. Security Considerations
- **HTTPS**: Enforce HTTPS for all Retrofit communication.
- **Biometrics vs API**: Biometric authentication unlocks the local UI and local data. It does not authenticate with the API directly; it merely gates access to the stored Sanctum token.
