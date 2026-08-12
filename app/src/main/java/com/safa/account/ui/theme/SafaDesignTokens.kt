package com.safa.account.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Shared spacing and component metrics used by SAFA screens. */
object SafaDimensions {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    val minTouchTarget = 48.dp
    val fieldHeight = 52.dp
    val compactFieldHeight = 44.dp
    val buttonHeight = 48.dp
    val compactButtonHeight = 40.dp
    val iconButtonSize = 48.dp
    val screenHorizontalPadding = 16.dp
    val cardPadding = 16.dp
    val dialogPadding = 24.dp

    val standardButtonContentPadding = PaddingValues(horizontal = 20.dp)
    val compactButtonContentPadding = PaddingValues(horizontal = 16.dp)
}

object SafaMotion {
    const val fast = 150
    const val standard = 220
    const val emphasized = 300

    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val enterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

@Immutable
data class SafaButtonMetrics(
    val height: androidx.compose.ui.unit.Dp = SafaDimensions.buttonHeight,
    val contentPadding: PaddingValues = SafaDimensions.standardButtonContentPadding
)

object SafaButtonDefaults {
    val metrics = SafaButtonMetrics()
    val compactMetrics = SafaButtonMetrics(
        height = SafaDimensions.compactButtonHeight,
        contentPadding = SafaDimensions.compactButtonContentPadding
    )

    fun minSize() = ButtonDefaults.MinHeight
}
