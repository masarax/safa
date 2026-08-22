# SAFA web localization and accessibility release checklist

Issue: #245

## Automated gates

- English and Bangla `web.php` catalogs must expose identical recursive keysets.
- Core authenticated workspace and login Blade views must not contain inline English/Bangla ternary copy.
- JavaScript-rendered dashboard copy is supplied by the active Laravel locale catalog; account and business data never enters translation resources.
- Laravel feature tests verify locale selection plus dialog/live-region semantics.
- Existing CSP, authorization rendering, backend tests and JavaScript syntax checks remain release gates.

## Manual browser matrix

Run the following against both English and Bangla before a production web release:

1. Keyboard only: sign in, primary navigation, account selector, customer/supplier creation, wallet/expense actions, settings, destructive confirmations and sign out. Focus must remain visible and return to the invoking control after a modal closes.
2. Screen reader: verify page landmarks, navigation labels, form labels/errors, modal names, live status/toast announcements and icon-only control names. Decorative icons remain hidden from the accessibility tree.
3. Zoom/reflow: validate 200% and 400% browser zoom at desktop and 320 CSS-pixel mobile width. No critical action may require two-dimensional scrolling or become clipped.
4. Text expansion: validate long Bangla labels and English strings without obscuring balances, submit buttons or error messages.
5. Dialogs: focus enters the active dialog, Escape/close works where appropriate, background content is not treated as the active interaction surface, and focus returns on close.
6. Errors: invalid login and invalid form submission announce the error and associate it with the relevant field without relying on color alone.
7. Touch targets: icon-only and compact interactive controls must provide an effective target of at least 44x44 CSS pixels (48x48 preferred for mobile parity).

## Exceptions

Any WCAG exception requires a linked GitHub issue, affected route/control, severity, compensating behavior, owner and planned removal date. Automated checkers supplement rather than replace keyboard and assistive-technology verification.
