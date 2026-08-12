package com.safa.account.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SAFA design tokens.
 *
 * Brand direction:
 * - Dark green is the structural/foundation brand colour.
 * - Orange is the primary logo/action accent.
 * - Semantic colours are reserved for status meaning and must not be used as brand substitutes.
 */
object AppColors {
    // Brand
    val BrandGreen = Color(0xFF064E3B)
    val BrandGreenDark = Color(0xFF043A2C)
    val BrandGreenLight = Color(0xFF0B6B52)
    val BrandGreenContainer = Color(0xFFDDF5ED)
    val OnBrandGreen = Color(0xFFFFFFFF)

    val BrandOrange = Color(0xFFF97316)
    val BrandOrangeDark = Color(0xFFEA580C)
    val BrandOrangeLight = Color(0xFFFF8A3D)
    val BrandOrangeContainer = Color(0xFFFFE9D8)
    val OnBrandOrange = Color(0xFFFFFFFF)
    val OnBrandOrangeContainer = Color(0xFF7C2D12)

    // Semantic status colours
    val StatusGreen = Color(0xFF15803D)
    val StatusGreenContainer = Color(0xFFDCFCE7)
    val StatusRed = Color(0xFFB91C1C)
    val StatusRedContainer = Color(0xFFFEE2E2)
    val StatusAmber = Color(0xFFD97706)
    val StatusAmberContainer = Color(0xFFFEF3C7)
    val StatusBlue = Color(0xFF0369A1)
    val StatusBlueContainer = Color(0xFFE0F2FE)

    // Surfaces / content
    val SurfaceWhite = Color(0xFFFFFFFF)
    val BackgroundLight = Color(0xFFF7FAF8)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceVariantLight = Color(0xFFEEF4F1)
    val BorderSubtle = Color(0xFFDCE7E2)
    val BorderStrong = Color(0xFFB9CCC4)
    val TextPrimary = Color(0xFF10231C)
    val TextSecondary = Color(0xFF5F7069)
    val TextMuted = Color(0xFF87968F)

    // Dark surfaces
    val BackgroundDark = Color(0xFF071A14)
    val SurfaceDark = Color(0xFF0D241C)
    val SurfaceVariantDark = Color(0xFF16352A)
    val OnDark = Color(0xFFE8F2EE)
    val OnDarkSecondary = Color(0xFFB8C9C2)
    val BorderDark = Color(0xFF29483B)

    // Kept for existing callers; semantic status colours should be preferred.
    val WhatsAppGreen = Color(0xFF25D366)
    val TerminalSurface = Color(0xFF06120E)

    // Legacy names retained to avoid breaking existing screen code while migrating.
    val PrimaryRed = BrandGreen
    val PrimaryRedContainer = BrandGreenContainer
    val PrimaryOnRedContainer = Color(0xFF064E3B)
    val GoldSecondary = BrandOrange
    val GoldSecondaryContainer = BrandOrangeContainer
    val GoldOnSecondaryContainer = OnBrandOrangeContainer
    val BackgroundSlate = BackgroundLight
    val DarkSlateHeader = BrandGreen
}

// Legacy theme aliases. New code should use AppColors directly.
val BluePrimary = AppColors.BrandOrange
val BlueOnPrimary = AppColors.OnBrandOrange
val BluePrimaryContainer = AppColors.BrandOrangeContainer
val BlueOnPrimaryContainer = AppColors.OnBrandOrangeContainer

val GovGreen = AppColors.StatusGreen
val LightGreenContainer = AppColors.StatusGreenContainer
val GovRed = AppColors.StatusRed
val LightRedContainer = AppColors.StatusRedContainer

val SlateSecondary = AppColors.BrandGreen
val SlateOnSecondary = AppColors.OnBrandGreen
val SlateSecondaryContainer = AppColors.BrandGreenContainer
val SlateOnSecondaryContainer = Color(0xFF064E3B)

val LightBackground = AppColors.BackgroundLight
val LightSurface = AppColors.SurfaceLight
val LightOnBackground = AppColors.TextPrimary
val LightOnSurface = AppColors.TextPrimary

val DarkBluePrimary = AppColors.BrandOrange
val DarkBlueOnPrimary = AppColors.OnBrandOrange
val DarkBluePrimaryContainer = AppColors.BrandOrangeDark
val DarkBlueOnPrimaryContainer = Color.White
val DarkSlateSecondary = AppColors.BrandGreenLight
val DarkSlateOnSecondary = Color.White
val DarkSlateSecondaryContainer = AppColors.SurfaceVariantDark
val DarkSlateOnSecondaryContainer = AppColors.OnDark
val DarkBackground = AppColors.BackgroundDark
val DarkSurface = AppColors.SurfaceDark
val DarkOnBackground = AppColors.OnDark
val DarkOnSurface = AppColors.OnDark
