# Android Accessibility Release Checklist

Run this checklist against the signed release candidate after automated Android Production CI is green. Keep screenshots and notes with the release evidence; a failed critical-flow check blocks promotion.

## Assistive technology

- Enable TalkBack and complete sign-in, Dashboard navigation, customer create/edit, supplier create/edit, transaction create/edit, wallet actions, Settings, logout, and first-run setup states.
- Confirm every actionable control announces a meaningful localized name, role, state, and disabled state where applicable.
- Confirm decorative icons and logos are not announced as duplicate controls or duplicate labels.
- Enable Switch Access and verify every critical action can be reached and activated in a deterministic order.
- Verify focus enters dialogs, remains inside while modal, and returns to the invoking control when dismissed.

## Touch targets and text scaling

- Verify custom controls are at least 48 x 48 dp on the release build. Pay special attention to language selection, icon buttons, navigation actions, dialog close actions, and compact list actions.
- Test system font scale at 1.0x, 1.3x, 1.5x, and 2.0x in both English and Bangla.
- At 2.0x, confirm sign-in, primary navigation, create/save/delete confirmations, transaction totals, wallet balances, Settings actions, and first-run setup actions remain visible, readable, and tappable without clipping or overlap.
- Test increased display size together with 1.5x font scale and verify critical actions remain reachable by scrolling where necessary.

## Locale and layout matrix

Capture release-candidate screenshots for the following states in **English and Bangla** at normal font scale and 2.0x font scale:

- Sign in and first-run setup state.
- Dashboard with navigation and summary cards.
- Customer list, details, and create/edit dialog.
- Supplier list, details, and create/edit dialog.
- Transaction list and create/edit flow.
- Wallet list and critical deposit/withdraw/confirmation states.
- Settings and language selector.

For every screenshot pair verify that localized copy comes from the Android resource catalog, monetary/date values remain correct, no critical text is clipped, and no action moves outside the reachable viewport.

## Release evidence

- Record device/API level, screen size, font scale, display size, locale, build SHA, and signed artifact checksum.
- Attach EN/BN screenshot evidence and any TalkBack/Switch Access notes to the release record.
- Do not waive serious accessibility regressions. Fix them on the release branch and rerun Android Production CI plus this matrix before promotion.
