# Architecture & Roadmap for Hundi Ledger

## 1. High-Level Architecture
The application will follow a clean, modern Android architecture:
- **Presentation Layer**: Jetpack Compose (M3) + ViewModels (MVI/MVVM pattern). State flows downwards, events flow upwards.
- **Domain Layer**: Usecases/Interactors for complex business logic (e.g., calculating rate-based transaction totals, syncing logic).
- **Data Layer**:
  - **Local Source**: Room Database (Offline-first truth source).
  - **Remote Source**: Retrofit (Laravel REST API).
  - **Repository**: Single source of truth, mediating between Room and Retrofit, handling synchronization via `SyncManager`.
- **Dependency Injection**: Dagger Hilt will be introduced to wire ViewModels, Repositories, DAOs, and API services.

## 2. Multi-Account Database Schema Strategy
All operational tables must include an `account_id` to ensure tenant isolation locally.
- **Account**: `id`, `name`, `currency_symbol`, `created_at`
- **Customer**: `id`, `account_id`, `name`, `phone`, `balance`, `created_at`
- **Supplier**: `id`, `account_id`, `name`, `balance`
- **Transaction**: `id`, `account_id`, `type` (Credit/Debit), `amount`, `customer_id` (nullable), `supplier_id` (nullable), `rate` (nullable), `timestamp`, `sync_status` (Pending/Synced)

## 3. Offline-First Sync Engine (SyncManager)
The app will prioritize local writes.
1. User creates a transaction -> Saved to Room with `sync_status = PENDING`.
2. UI updates immediately from Room flow.
3. `SyncManager` (Worker or Coroutine) attempts to push `PENDING` records to the Laravel API.
4. On success, updates Room `sync_status = SYNCED`.
5. On fetch, pulls latest from API and reconciles with local Room DB.

## 4. Development Roadmap

### Phase 1: Foundation (Weeks 1-2)
- Reconfigure Gradle, update namespaces (`com.safa.account`).
- Integrate Dagger Hilt.
- Define Room Database schema with `account_id` support.
- Setup DataStore for App Preferences (Operational Mode, Active Account ID, Biometric toggle).

### Phase 2: Design System & UI Core (Weeks 3-4)
- Implement Jetpack Compose M3 Theme (Colors, Typography, Shapes).
- Create core components: `M3TopAppBar`, `M3BottomNav`, `M3TransactionCard`, `M3StatWidget`.
- Build navigation graph.

### Phase 3: Feature Implementation (Weeks 5-7)
- **Authentication**: Biometric login screen + PIN fallback. Laravel API Token logic.
- **Dashboard**: Aggregated stats for the active account.
- **Transactions**: Complex entry forms adapting to the current Operational Mode.
- **Settings**: Currency, Mode toggle, Account switching.

### Phase 4: API Integration & Sync (Weeks 8-9)
- Implement Retrofit services matching Laravel endpoints.
- Build `SyncManager` for background synchronization.
- Implement conflict resolution strategies.

### Phase 5: Testing & Polish (Week 10)
- Unit tests for ViewModels and Repositories.
- UI tests for critical Compose flows.
- Performance profiling and release build optimization.
