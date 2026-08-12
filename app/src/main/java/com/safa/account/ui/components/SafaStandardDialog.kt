package com.safa.account.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safa.account.ui.theme.AppColors
import com.safa.account.ui.theme.SafaDimensions

/**
 * Unified confirmation language for important and destructive actions.
 * Labels are deliberately compact so English/Bengali translations stay on one line.
 */
@Composable
fun SafaStandardConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    language: String? = null
) {
    val compactTitle = UiCopy.compact(title, language)
    val compactConfirm = UiCopy.compact(confirmLabel, language)
    val compactDismiss = UiCopy.compact(dismissLabel, language)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = compactTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = AppColors.StatusRed,
                        contentColor = AppColors.OnBrandGreen
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = AppColors.BrandOrange,
                        contentColor = AppColors.OnBrandOrange
                    )
                },
                modifier = Modifier.height(SafaDimensions.compactButtonHeight)
            ) {
                Text(
                    text = compactConfirm,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(SafaDimensions.compactButtonHeight)
            ) {
                Text(
                    text = compactDismiss,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    )
}

/** Compact standardized action row for custom sheets/dialog bodies. */
@Composable
fun SafaDialogActions(
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    language: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.height(SafaDimensions.compactButtonHeight)
        ) {
            Text(
                UiCopy.compact(dismissLabel, language),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(SafaDimensions.sm))
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(containerColor = AppColors.StatusRed)
            } else {
                ButtonDefaults.buttonColors(containerColor = AppColors.BrandOrange)
            },
            modifier = Modifier.height(SafaDimensions.compactButtonHeight)
        ) {
            Text(
                UiCopy.compact(confirmLabel, language),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
