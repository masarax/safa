# UI/UX Design System & Screens Specification

## 1. Material Design 3 (M3) Foundation
The application will strictly adhere to M3 principles to ensure a modern, native Android feel.

### 1.1 Color Palette (Dynamic & Themed)
- **Primary**: Brand color for prominent actions (e.g., FAB for new transaction).
- **Secondary**: For secondary actions and highlights.
- **Tertiary**: For subtle accents (e.g., indicating specific modes).
- **Surface/Background**: Pure white (Light Mode) or dark grey (Dark Mode) to maximize text contrast.
- **Error/Success**: Semantic colors for positive (Income/Credit) and negative (Expense/Debit) balances.
- *Support for Dynamic Color (Monet) on Android 12+.*

### 1.2 Typography
- Use M3 default typography scale (Display, Headline, Title, Body, Label).
- Enforce readable fonts for numeric values (tabular numerals if possible for ledgers).

### 1.3 Shapes
- M3 standard shapes: Medium rounded corners for Cards, full circular for FABs, small rounded for buttons.

## 2. Core Reusable Components
- `M3Scaffold`: Base wrapper handling TopAppBar, BottomNavigation, and Snackbar.
- `NumericAmount`: A composable specifically for rendering currency and numbers with proper coloring (green for +, red for -) and the user's selected currency symbol.
- `TransactionRow`: A dense, highly readable list item showing date, party name, amount, and sync status icon.
- `ModeIndicatorChip`: A small UI element showing the active operational mode (e.g., "Rate-Based Mode").

## 3. Screen Specifications

### 3.1 Login & Security Screen
- **Initial View**: Biometric prompt immediately on launch (if enabled).
- **Fallback View**: PIN pad input.
- **Visuals**: Centered logo, secure lock icon, minimal M3 interface.

### 3.2 Dashboard (Home)
- **Top Bar**: Active Account dropdown selector, current date.
- **Summary Section**: Large `M3Card` showing Total Balance, Total In, Total Out.
- **Quick Actions**: Row of icon buttons (Add Customer, Add Transaction).
- **Recent Activity**: Short list of latest `TransactionRow` items.

### 3.3 Transaction Entry (Dynamic Screen)
This screen adapts based on the active `Operational Mode`:
- **Standard Mode**: Basic Amount, Date, Note, Type (In/Out).
- **Customer Mode**: Adds a searchable Customer Dropdown.
- **Customer & Supplier Mode**: Adds both Customer and Supplier Dropdowns.
- **Rate-Based Mode**: Adds a "Rate" multiplier field, showing calculated total dynamically.

### 3.4 Ledger / Entities Screen
- Tabbed view: `Customers` | `Suppliers`.
- Search bar for filtering.
- List of entities with their current aggregated balance.
- Tapping an entity opens their detailed ledger view (filtered transaction list).

### 3.5 Settings Screen
- **Account Management**: Create, edit, switch accounts.
- **Preferences**:
  - Operational Mode selection (Radio buttons).
  - Currency Symbol input (Text field).
  - Biometric Security toggle (Switch).
- **Sync Status**: Manual sync button and last synced timestamp.
- **Logout**.

## 4. State Management (UI Layer)
- Use `StateFlow` in ViewModels to expose UI state (e.g., `DashboardUiState`).
- Use `collectAsStateWithLifecycle()` in Compose for lifecycle-aware state consumption.
- Handle one-off events (Snackbar messages, navigation) using shared flows or side-effect channels.
