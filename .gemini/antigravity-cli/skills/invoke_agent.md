invoke_agent(agent_name="generalist", prompt="""Objective: Conduct a comprehensive, non-destructive audit of the existing Android project and create a detailed, phased development plan to transform it into a professional, multi-account financial management application using Jetpack Compose with Material Design 3 (M3) and a Laravel-based API backend.

Project Vision:
The final application will be a sophisticated \"Hundi\" style accounting and money management system. It must be highly flexible, secure, and feature a polished, professional user interface.

Key Functional Requirements:
1.  **Multi-Account Architecture:** The core system must support multiple, isolated user accounts or \"books.\"
2.  **Flexible Operational Modes:** Users must be able to configure the app to operate in different modes via Settings:
    *   **Standard Mode:** General income/expense tracking.
    *   **Customer-Centric Mode:** Transactions tracked per customer.
    *   **Customer & Supplier Mode:** Transactions linked to both customers and suppliers.
    *   **Rate-Based Mode:** Incorporates supplier buying rates into transactions.
3.  **Professional UI/UX:** The entire UI must be rebuilt using Jetpack Compose and adhere strictly to Material Design 3 (M3) principles for a modern and intuitive experience.
4.  **Enhanced Security:** Implement biometric (fingerprint) authentication for app access.
5.  **Customization:** Allow users to change the currency symbol/icon through the settings screen.
6.  **Backend Integration:** The app will be powered exclusively by a Laravel API for all data operations (syncing, authentication, etc.). There will be no frontend website.

---

Phase 1: Deep Codebase Audit (Read-Only)

Your primary directive is to analyze and understand the current codebase without making any modifications.

1.  **Full Project Structure Analysis:**
    *   Map out the existing directory and file structure.
    *   Identify the purpose of each key package (e.g., `data`, `ui`, `viewmodel`).
    *   Review all Gradle files (``build.gradle.kts``, ``settings.gradle.kts``, ``libs.versions.toml``) to understand current dependencies, SDK versions, and project configuration. Pay close attention to libraries related to UI, database, and networking.

2.  **UI Layer Audit:**
    *   Examine all files in the ``ui/screens`` and ``ui/components`` packages.
    *   Determine the current UI framework (e.g., legacy Android Views, early-stage Compose).
    *   Assess the current adherence (or lack thereof) to Material Design principles.
    *   Identify all existing UI screens and their functionalities.

3.  **Data Layer & Business Logic Audit:**
    *   **Repository & DAO:** Analyze ``AppRepository.kt`` and ``AppDaos.kt`` to understand the current data flow and database operations (Room).
    *   **Models:** Inspect ``Models.kt`` to understand the current data schema.
    *   **API Client:** Review the ``data/api`` package (``ApiService.kt``, ``RetrofitClient.kt``, ``DtoMappers.kt``). Document the existing API endpoints, DTOs, and the current approach to network requests and data mapping.
    *   **ViewModel:** Analyze ``HundiViewModel.kt`` to grasp the existing business logic and state management.

4.  **Identify Gaps:** Based on the audit and the new requirements, create a list of major gaps. Examples:
    *   \"Current data model in ``Models.kt`` lacks fields for multi-account support.\"
    *   \"UI in ``CustomerScreen.kt`` is built with legacy views and needs a full rewrite in Compose M3.\"
    *   \"No biometric authentication logic exists; ``BiometricHelper.kt`` is a placeholder.\"
    *   \"API service does not have endpoints for supplier management or rate-based transactions.\"

---

Phase 2: Strategic Development Plan Creation

Based on your audit, create a detailed, actionable development plan. This plan must be documented in markdown files.

Instructions for Deliverables:
*   Create a new directory in the project root: `.gemini/`.
*   Inside `.gemini/`, create the main planning document: ``SYSTEM_AUDIT_AND_PLAN.md``.
*   This main document should outline all phases below and can link to more detailed, separate markdown files (e.g., ``API_SPECIFICATION.md``, ``UI_COMPONENT_GUIDE.md``) within the same `.gemini/` directory if needed for clarity.

The plan documented in ``SYSTEM_AUDIT_AND_PLAN.md`` must include the following sections:

1.  Foundation & Architecture Refactoring:
    *   **Database Schema:** Propose a new Room database schema. Detail the necessary changes to ``Models.kt`` and ``AppDatabase.kt`` to support multi-account functionality, suppliers, and transaction rates.
    *   **Repository & Data Layer:** Refactor `AppRepository` to manage new entities.
    *   Implement logic to filter all data operations by the currently active user account.
    *   **Dependency Injection (Hilt):**
        *   Plan the setup of Hilt for providing dependencies like `AppRepository`, `AppDatabase`, and `ApiService`.

2.  UI/UX Overhaul Plan (Compose M3):
    *   **Design System:**
        *   Define a strict M3 color palette, typography scale, and shape system in the ``ui/theme`` package.
        *   Create a library of reusable M3 components (e.g., `M3Scaffold`, `M3Card`, `M3FilledButton`) in ``ui/components``.
    *   **Screen Rewrite Plan:**
        *   **DashboardScreen:** Redesign as a central hub with summary cards for the active account.
        *   **TransactionScreen:** Rebuild with M3 `TextFields`, `DatePicker` dialogs, and `DropdownMenus`.
        *   **SettingsScreen:** Design a new screen for managing app mode, currency symbol, and enabling biometric security.
        *   **LoginScreen:** Create a new authentication screen with support for password and biometric login.

3.  Feature Implementation Roadmap:
    *   **Biometric Security:**
        *   Implement `BiometricPrompt` using ``BiometricHelper.kt``.
        *   Integrate into `LoginScreen` and as an optional app lock.
    *   **Multi-Account Logic:**
        *   Implement UI for creating and switching between accounts.
        *   Ensure all database queries and view models are scoped to the selected account.
    *   **Dynamic Operational Modes:**
        *   Use a `StateFlow` from a settings repository to observe the current app mode.
        *   Conditionally show/hide UI elements and adjust business logic in ViewModels based on the mode (e.g., hide supplier fields if in \"Customer-Centric Mode\").

4.  Laravel API Integration Plan:
    *   **API Specification:**
        *   **Auth:** ``POST /api/login``, ``POST /api/register``, ``POST /api/logout``.
        *   **Accounts:** ``GET /api/accounts``, ``POST /api/accounts``, ``GET /api/accounts`/{id}`.
        *   **Customers:** ``GET /api/customers``, ``POST /api/customers``.
        *   **Suppliers:** ``GET /api/suppliers``, ``POST /api/suppliers``.
        *   **Transactions:** ``GET /api/transactions``, ``POST /api/transactions``.
        *   *(For each, define request/response JSON structures).*
    *   **Client-Side Implementation:**
        *   Update ``ApiService.kt`` with all new Retrofit service methods.
        *   Create corresponding DTOs in ``data/api/dto``.
        *   Implement mappers in ``DtoMappers.kt`` to convert DTOs to local Room entities.
        *   Enhance ``SyncManager.kt`` to orchestrate fetching and posting data for all entities.

5.  Testing Strategy:
    *   **Unit Tests:**
        *   Write JUnit tests for all `ViewModels` to verify state logic.
        *   Test `AppRepository` and data transformations.
    *   **UI Tests:**
        *   Use `createComposeRule()` to write Espresso tests for key user flows on the new Compose screens.
        *   Verify correct display of data and navigation between screens."""
)