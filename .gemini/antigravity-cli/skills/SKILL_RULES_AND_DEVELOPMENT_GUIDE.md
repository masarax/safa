# Skill Rules & Development Guide

## 1. Core Development Principles
- **Kotlin First**: Use Kotlin idioms, Coroutines, and Flows extensively.
- **Compose Only**: No XML layouts. All UI must be built with Jetpack Compose.
- **Offline First**: All reads/writes must go to the Room Database first. Network operations are strictly for synchronization.
- **Immutability**: UI State must be immutable data classes.

## 2. Architecture Rules (MVI/MVVM)
- **ViewModels**: Must not contain Android context. Expose state via `StateFlow`. Expose events via `SharedFlow` or Channels.
- **Repositories**: Must abstract the data source (Room vs API). Should return `Flow<T>` for continuous updates or `suspend` functions for one-off operations.
- **DAOs**: Use Coroutines for all queries. Return `Flow` for lists that the UI observes.
- **Dependency Injection**: Use Dagger Hilt. Inject interfaces, not concrete implementations.

## 3. Jetpack Compose Best Practices
- **State Hoisting**: Keep composables stateless where possible. Pass state down, hoist events up.
- **Preview Parameter Providers**: Create robust previews for all UI components using `PreviewParameterProvider` to visualize different states (loading, error, success, empty).
- **Material 3**: Use `androidx.compose.material3.*` imports exclusively. Avoid mixing M2 and M3.
- **Performance**: Use `remember` and `derivedStateOf` to prevent unnecessary recompositions. Use `key` inside `LazyColumn`.

## 4. Database (Room) Guidelines
- Use `@Entity(tableName = "table_name")`.
- Always include an `account_id` foreign key for multi-tenant isolation.
- Use `UUID` as the primary key (`@PrimaryKey val id: String = UUID.randomUUID().toString()`) to facilitate offline generation and syncing.
- Write Room Migrations for any schema changes.

## 5. Networking (Retrofit) Guidelines
- Use Moshi for JSON serialization/deserialization.
- Implement an Interceptor to automatically attach the `Authorization: Bearer` and `X-Account-ID` headers to all requests.
- Handle network errors gracefully; do not crash. Return a sealed `Result` class (Success/Error/Loading) from network calls.

## 6. Testing Requirements
- **Unit Tests**: Minimum 80% coverage on ViewModels and Repositories. Use `Turbine` for testing Flows.
- **UI Tests**: Write UI tests for critical paths (Login, Add Transaction) using Compose Test Rule and Espresso.

## 7. Git & Commit Strategy
- Use semantic commit messages (e.g., `feat: add biometric prompt`, `fix: correct transaction calculation`).
- Develop features in isolated branches.
