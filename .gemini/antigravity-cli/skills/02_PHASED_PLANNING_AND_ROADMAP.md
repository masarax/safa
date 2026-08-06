# SAFA System Skill 02: Phased Planning & Roadmap Execution Guide

## 1. Roadmap Framework
When executing tasks or adding features to SAFA, follow this exact 5-phase roadmap:

```
[Phase 1: Environment & Schema Alignment]
                  │
                  ▼
[Phase 2: UI Component & Theme Rigor]
                  │
                  ▼
[Phase 3: Business Logic & StateFlow Control]
                  │
                  ▼
[Phase 4: Sync & Network Integration]
                  │
                  ▼
[Phase 5: Automated Test Verification]
```

---

## 2. Phase-by-Phase AI Implementation Plan

### Phase 1: Schema & Domain Model Update
- **Goal**: Ensure room entities and backend migrations mirror financial double-entry rules.
- **Steps**:
  1. Inspect `@Entity` definitions in `app/src/main/java/com/safa/account/data/model/`.
  2. Verify foreign key constraints and `account_id` partitioning.
  3. Ensure SQLite / PostgreSQL migrations in `backend/database/migrations/` match Android entities.

### Phase 2: Material 3 UI/UX Standards
- **Goal**: Render responsive, interactive, and modern Jetpack Compose UIs.
- **Steps**:
  1. Use theme tokens from `ui/theme/Theme.kt` and `ui/theme/Color.kt`.
  2. Build stateless composables with event callbacks hoisted to parent state.
  3. Support Bengali and English dual-language formatting and RTL layouts where applicable.

### Phase 3: Business Logic & State Management
- **Goal**: Implement deterministic ViewModels without blocking UI threads.
- **Steps**:
  1. Expose `StateFlow<UiState>` from ViewModels.
  2. Ensure async Room/Network calls run on `Dispatchers.IO`.
  3. Maintain multi-currency rate calculation accuracy with zero rounding drift.

### Phase 4: Bi-Directional Offline Sync Engine
- **Goal**: Orchestrate data synchronization between Room SQLCipher and Laravel REST backend.
- **Steps**:
  1. Queue offline mutations with `sync_status = 0` (PENDING).
  2. Calculate SHA256 payload checksums and sign headers using HMAC keys (`SAFA_API_KEY`, `SAFA_API_SECRET`).
  3. Resolve conflicts using Last-Write-Wins (LWW) timestamp comparisons.

### Phase 5: Build Verification & Testing
- **Goal**: Validate every code change before reporting completion.
- **Steps**:
  1. Run `./gradlew assembleDebug` or `./gradlew test` for Android.
  2. Run `php artisan test` inside `backend/` for API endpoints.
