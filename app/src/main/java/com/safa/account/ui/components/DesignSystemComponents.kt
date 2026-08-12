package com.safa.account.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safa.account.ui.theme.AppColors
import com.safa.account.ui.theme.SafaDimensions

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderWidth: Dp = 1.dp,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(Modifier.padding(SafaDimensions.cardPadding), content = content)
    }
}

@Composable
fun AppStatusChip(
    text: String,
    statusType: String = "INFO",
    modifier: Modifier = Modifier
) {
    val (background, content) = when (statusType.uppercase()) {
        "SUCCESS", "COMPLETED", "PAID", "PROFIT" -> AppColors.StatusGreenContainer to AppColors.StatusGreen
        "ERROR", "CANCELLED", "DEFICIT", "DUE" -> AppColors.StatusRedContainer to AppColors.StatusRed
        "WARNING", "PENDING" -> AppColors.StatusAmberContainer to AppColors.StatusAmber
        "PRIMARY" -> AppColors.BrandOrangeContainer to AppColors.BrandOrangeDark
        else -> AppColors.StatusBlueContainer to AppColors.StatusBlue
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = background
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun AppMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SafaDimensions.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(SafaDimensions.xs))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SafaDimensions.xs)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (actionText != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = SafaDimensions.xs, vertical = 2.dp)
            ) {
                Text(
                    actionText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = SafaDimensions.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.BrandOrange,
            contentColor = AppColors.OnBrandOrange
        ),
        contentPadding = SafaDimensions.standardButtonContentPadding
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(SafaDimensions.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = SafaDimensions.buttonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = SafaDimensions.standardButtonContentPadding
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(SafaDimensions.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().heightIn(min = SafaDimensions.fieldHeight),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.BrandOrange,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = AppColors.BrandOrange
        )
    )
}

@Composable
fun SafaDestructiveDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    cancelText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
