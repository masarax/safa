# Android sensitive-window policy

SAFA treats every application activity as sensitive by default because authentication, customer/supplier identity, receiver details, balances, rates and transaction data can appear through normal navigation or system recent-app previews.

## Default controls

- `FLAG_SECURE` is applied to every activity at creation time. Ordinary screenshots, recent-app thumbnails and display on non-secure virtual displays must not expose SAFA content.
- On Android 12/API 31 and newer, `Window.setHideOverlayWindows(true)` is enabled and the app declares `android.permission.HIDE_OVERLAY_WINDOWS` to reduce tapjacking/hostile application-overlay risk.
- Backup remains disabled and cleartext networking remains disabled independently of these UI controls.

These controls are defense in depth. They do not replace authentication, server authorization, device/session revocation, TLS or business-action confirmation.

## Accessibility and support

The policy does not block Android accessibility services or change Compose semantics. Release validation must still cover TalkBack/keyboard-equivalent flows and supported password/biometric authentication. If future support workflows require screenshots, they must use explicitly redacted diagnostics rather than weakening the global financial-data default.

## Release checks

Instrumentation/release smoke must continue to launch the real signed/minified application. Security regression tests verify the secure-window flag, and the manifest must retain the overlay-hiding permission on supported API levels.
