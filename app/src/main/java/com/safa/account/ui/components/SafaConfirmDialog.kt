package com.safa.account.ui.components

import androidx.compose.runtime.Composable

/**
 * Backwards-compatible wrapper for the shared destructive confirmation dialog.
 * Existing screens can keep their current call sites while receiving the
 * unified spacing, typography, button sizing and overflow behaviour.
 */
@Composable
fun SafaConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    SafaStandardConfirmDialog(
        title = title,
        message = message,
        confirmLabel = confirmText,
        dismissLabel = cancelText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        destructive = true
    )
}
