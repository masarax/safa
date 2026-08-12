package com.safa.account.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * Single confirmation-dialog language for destructive and important actions.
 * Screens should prefer this component over bespoke AlertDialog layouts.
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
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
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
                    text = confirmLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.height(SafaDimensions.compactButtonHeight)
            ) {
                Text(
                    text = dismissLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge
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
    modifier: Modifier = Modifier
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
                dismissLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
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
                confirmLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
