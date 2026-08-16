# SAFA Web UI/UX + Figma Specification

Issue: #122  
Figma target file: `SAFA Unified Android + Web Design System`  
Figma file key: `LF2NVsxMV69EDs1Y0zwZ8U`

## Status

This specification is the implementation contract for the web redesign and the exact source for the Figma canvas. The connected Figma account is currently on the Starter plan and Figma MCP rejects both metadata reads and canvas writes because the plan tool-call quota is exhausted. Therefore the production design contract is versioned here until the Figma quota permits the same foundations/components/screens to be materialized in the target file.

Do not mark the Figma deliverable complete until the target file contains the foundations, components and representative screens defined below.

## Design source hierarchy

1. **Actual rendered Android Compose UI** is the visual/composition source of truth.
2. `AppColors`, `SafaDimensions`, `Typography`, `AppShapes` and reusable Compose components are the primitive/state source of truth.
3. Web-specific adaptation may change arrangement for larger viewports, but it must not create a second brand.
4. Existing Laravel routes, account isolation, permissions, authentication, API contracts and runtime selectors are functional constraints and must not be redesigned away.

## Important conflict resolutions

The Android codebase contains both canonical theme tokens and legacy screen-level visual choices. The previous web pass followed only canonical green/orange tokens and therefore did not reproduce the actual application.

For this redesign:

- Material theme primary remains SAFA orange `#F97316` for primary actions and focused fields.
- Structural green remains `#064E3B` for secondary brand semantics.
- The **actual Android app chrome** is preserved: light top app bar gold `#D7A84B`, dark app bar `#1B1812`, light content ink `#3E2700`, dark content ink `#E5C158`.
- The **actual Android selected navigation treatment** is preserved: light selected red `#A82222` on `#FFEBEE`; dark selected blue `#6EA8FF` on `#1E293B`.
- Dashboard shortcut colors use the actual Compose values, not a single-color web reinterpretation.
- Cards use the actual Android neutral white/gray presentation: light card `#FFFFFF`, light border `#E5E7EB`; dark surfaces use the application dark surface family.

## Foundations

### Core color variables

| Token | Light | Dark | Purpose |
|---|---:|---:|---|
| Background | `#F7FAF8` | `#071A14` | page/workspace |
| Surface | `#FFFFFF` | `#0D241C` | cards/forms |
| Card border | `#E5E7EB` | `#2E3748` | Android-style neutral card outline |
| Text | `#10231C` | `#E8F2EE` | default text |
| Muted | `#5F7069` | `#B8C9C2` | secondary text |
| Brand orange | `#F97316` | `#F97316` | primary action/focus |
| Brand green | `#064E3B` | `#0B6B52` | secondary brand |
| App bar | `#D7A84B` | `#1B1812` | actual Android chrome |
| App bar content | `#3E2700` | `#E5C158` | actual Android chrome content |
| Active navigation | `#A82222` | `#6EA8FF` | actual Android selection |
| Active navigation bg | `#FFEBEE` | `#1E293B` | actual Android indicator |
| Danger | `#B91C1C` | `#F87171` | destructive/error |
| Success | `#15803D` | `#4ADE80` | success/completed |
| Warning | `#D97706` | `#F59E0B` | pending/warning |
| Info | `#0369A1` | `#38BDF8` | informational/advance |

### Dashboard shortcut colors

| Shortcut | Icon | Container |
|---|---:|---:|
| Customers / Transactions family | `#E53935` | `#FFEBEE` |
| Income / Expense | `#FB8C00` | `#FFF3E0` |
| Supplier | `#43A047` | `#E8F5E9` |
| Exchange rate | `#E91E63` | `#FCE4EC` |
| Riyal stock | `#FBC02D` | `#FFFDE7` |
| Overview / Profit-loss family | `#00796B` | `#E0F2F1` |
| Sync / all activity family | `#3F51B5` | `#E8EAF6` |
| Account / customers auxiliary | `#8D6E63` | `#EFEBE9` |

### Spacing

Use the Android spacing scale only: `4, 8, 12, 16, 20, 24, 32`.

### Shapes

- XS: 6
- Small: 10
- Field/compact card: 12
- Standard card: 16 web adaptation of Android 14–16 presentation
- Large: 20
- Extra large: 24
- Avatar/logo/icon treatments: circle

### Sizing

- Standard touch target: 48
- Web form field: 48 minimum; Android canonical field is 52 where layout allows
- Standard primary action: 48
- Mobile app bar: 72
- Mobile bottom navigation: 68
- Desktop sidebar: 248

### Typography

Use system/default fonts, matching Android `FontFamily.Default`. Bengali relies on system Bengali support. Do not introduce a separate marketing font.

Hierarchy:

- Screen title: 18 mobile, 22 desktop, 800–850
- Card/section title: 14–16, 800
- Body: 13–14
- Secondary/body small: 10–12
- Navigation/compact labels: 9–11, 650–800
- Metric: 18–20, 900

## Components to create in Figma

All reusable components must have light/dark examples and the states that apply.

### 1. App Top Bar

Variants:
- Mobile / Light gold
- Mobile / Dark gold
- Desktop context bar

Content:
- circular app logo
- app name + signed-in user on mobile
- account selector
- theme action
- settings action

### 2. Navigation Item

Variants:
- default
- hover
- selected
- disabled if needed

Responsive forms:
- mobile vertical bottom-nav item
- desktop horizontal sidebar item

Use Material-style vector icons, never Unicode glyphs.

### 3. Primary Button

- Orange `#F97316`
- height 48 standard
- radius 10
- icon optional
- states: default, hover, pressed, focus, disabled, loading

### 4. Secondary / Compact / Danger Buttons

- secondary white/outlined
- compact height 34–40 where Android uses compact screen actions
- danger red semantic container

### 5. Text Field / Select

- radius 12
- leading icon optional
- focus orange
- states: default, hover, focus, error, disabled, read-only

### 6. Surface Card

- white/neutral surface
- 1px neutral border
- low/no elevation, consistent with Android cards
- standard radius 16

### 7. Metric Card

- label + strong value
- semantic value variants: default, danger, info, success

### 8. Shortcut Action

- 46 circular icon container
- 20 vector icon
- compact 9–10 label
- 4-column mobile grid and 4-column desktop grouping
- all eight Android color families

### 9. Directory Entity Card

- 40 circular avatar/icon
- name + phone/detail
- balance block aligned right
- 3 compact metrics below divider
- interactive hover/focus on web
- due/advance/settled variants

### 10. Status Chip

- success, pending/warning, error/cancelled, info, primary

### 11. Profile Header + Tabs

- person/avatar block
- balance block
- segmented tabs
- active tab surface treatment

### 12. Ledger Date Group / Row

- date heading
- 32 circular customer/supplier type icon
- primary line name
- rate/time secondary line
- semantic amount aligned right
- expandable details

### 13. Modal / Subpage Toolbar

- close/back icon button
- eyebrow + title
- no Unicode ×/← in final representation

### 14. Transaction Flow

- 2-step progress segments
- amount field
- payout method chips
- wallet selection
- due/advance semantic notice
- summary box
- back + primary submit actions

### 15. Settings Card

- title + explanation
- form group
- save action
- variants for profile, preferences, PIN, business configuration, user management

### 16. Empty / Loading / Error / Toast

- empty state with vector icon + title + guidance
- loading state/skeleton or progress
- error notice
- success/info/warning notices
- toast success/error

## Figma screens

### Screen A — Login / Mobile

Frame: Android phone width.

Composition:
1. top-right 36 language pill
2. vertically centered content
3. 88 circular logo
4. SAFA title, 22 bold, orange primary
5. mobile/email field with phone icon
6. PIN/password field with security icon
7. error message state example
8. full-width 48–50 primary action
9. small session-security note for web adaptation

### Screen B — Login / Desktop

Do not create a marketing split-screen. Preserve the Android centered authentication composition inside a restrained web card, max width about 420.

### Screen C — Dashboard / Mobile

1. gold app bar
2. 8 shortcut actions, 2 rows × 4
3. KPI cards
4. business overview
5. recent transaction history grouped by date
6. ledger reserves summary
7. red-selected bottom navigation

### Screen D — Dashboard / Desktop

1. 248 sidebar
2. gold brand block at sidebar top
3. selected red navigation item
4. neutral desktop context topbar with account selector/actions
5. same shortcut grid, not a separate desktop theme
6. 4 KPI cards
7. overview
8. recent ledger
9. reserves

### Screen E — Customers

1. compact People icon header + title
2. compact Add action
3. search field
4. filter/sort controls
5. responsive entity cards
6. empty state

### Screen F — Customer Profile

1. back toolbar
2. profile hero
3. balance
4. tabs
5. ledger grouped by date
6. action controls according to permissions

### Screen G — Suppliers

Same compact directory family as Customers, with supplier semantic balances and supplier flow entry points.

### Screen H — Wallet

1. wallet header + add-ledger action
2. summary metrics
3. ledger register cards
4. deposit/withdraw/rename modal states

### Screen I — Income / Expense

1. compact payments header
2. summary metrics
3. entry list/cards
4. add/edit entry modal

### Screen J — Settings

Responsive card grid containing:
- My account
- Personal preferences
- Change PIN
- Brand & Business Configuration when permitted
- User Management when permitted
- Sign out danger action

### Screen K — Transaction Flow

Create Step 1 and Step 2 frames, plus due collection and advance return variants.

### Screen L — System Update

Use the same product shell primitives; this must not look like a separate installer brand.

## Responsive rules

### Mobile `< 720`

- gold app bar
- bottom navigation floating/fixed near safe area
- single-column entity lists
- KPI 2 columns
- shortcut grid 4 columns
- settings 1 column
- modal nearly full width

### Tablet `720–1023`

- bottom navigation retained
- entity lists 2 columns
- settings 2 columns
- KPI 4 columns when space allows

### Desktop `>= 1024`

- 248 persistent sidebar
- desktop context topbar
- content max width 1440
- entity cards 2 columns; 3 at wide desktop
- settings 2 columns; 3 at wide desktop
- subpages offset from sidebar/topbar
- transaction flow max width about 900

## Accessibility and interaction contract

- No icon-only action without accessible name.
- Keyboard focus ring uses orange semantic focus and must be visible in light/dark modes.
- Minimum interactive target 40 for compact desktop actions, 48 for primary/touch-critical actions.
- Do not encode due/success/state only with color; text labels remain present.
- Respect `prefers-reduced-motion`.
- Use ellipsis for constrained single-line names/amounts; never allow financial summary layout to overflow.
- Responsive filters/selects must remain keyboard accessible.

## Production mapping

- `backend/public/safa-web-product.css`: foundations + all component/layout styles.
- `backend/public/safa-web.css`: single entry point and narrow runtime compatibility overrides.
- `backend/resources/views/safa/login.blade.php`: Login screens.
- `backend/resources/views/safa/app.blade.php`: app shell, dashboard shortcuts, directories, wallet, expense, settings and modal templates.
- `backend/public/safa-web.js`: authoritative existing business/runtime behavior.
- `backend/public/safa-web-product.js`: presentation-only dashboard recent ledger/reserve rendering using the existing authenticated workspace GET endpoint; no mutations.

## Figma completion gate

When Figma MCP quota is available:

1. Open file `LF2NVsxMV69EDs1Y0zwZ8U`.
2. Create local variables for foundations above, including light/dark modes.
3. Create the 16 component families above with variants/states.
4. Create Screens A–L.
5. Verify desktop and mobile implementations visually against the production web structure and Android source.
6. Only after those steps may issue #122's Figma acceptance criterion be marked complete.
