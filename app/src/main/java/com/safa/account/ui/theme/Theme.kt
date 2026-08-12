package com.safa.account.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.BrandOrange,
    onPrimary = AppColors.OnBrandOrange,
    primaryContainer = AppColors.BrandOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = AppColors.BrandGreenLight,
    onSecondary = Color.White,
    secondaryContainer = AppColors.SurfaceVariantDark,
    onSecondaryContainer = AppColors.OnDark,
    tertiary = AppColors.StatusBlue,
    background = AppColors.BackgroundDark,
    surface = AppColors.SurfaceDark,
    surfaceVariant = AppColors.SurfaceVariantDark,
    onBackground = AppColors.OnDark,
    onSurface = AppColors.OnDark,
    onSurfaceVariant = AppColors.OnDarkSecondary,
    outline = AppColors.BorderDark,
    outlineVariant = AppColors.BorderDark,
    error = AppColors.StatusRed,
    onError = Color.White,
    errorContainer = AppColors.StatusRedContainer,
    onErrorContainer = AppColors.StatusRed
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.BrandOrange,
    onPrimary = AppColors.OnBrandOrange,
    primaryContainer = AppColors.BrandOrangeContainer,
    onPrimaryContainer = AppColors.OnBrandOrangeContainer,
    secondary = AppColors.BrandGreen,
    onSecondary = AppColors.OnBrandGreen,
    secondaryContainer = AppColors.BrandGreenContainer,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = AppColors.StatusBlue,
    background = AppColors.BackgroundLight,
    surface = AppColors.SurfaceLight,
    surfaceVariant = AppColors.SurfaceVariantLight,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.BorderStrong,
    outlineVariant = AppColors.BorderSubtle,
    error = AppColors.StatusRed,
    onError = Color.White,
    errorContainer = AppColors.StatusRedContainer,
    onErrorContainer = AppColors.StatusRed
)

/** Shared component shapes. Screens should use MaterialTheme.shapes rather than custom radii. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
