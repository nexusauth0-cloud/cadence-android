package com.cadence.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Design tokens (DESIGN-SYSTEM §1). Central source for spacing, radius,
 * elevation and motion — components reference these, never magic numbers.
 */
object CadenceTokens {

    // space.* — 4pt scale.
    val Space2 = 2.dp
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space48 = 48.dp

    // radius.*
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 20.dp
    val RadiusFull = 100.dp

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(RadiusSm),
        small = RoundedCornerShape(RadiusSm),
        medium = RoundedCornerShape(RadiusMd),
        large = RoundedCornerShape(RadiusLg),
        extraLarge = RoundedCornerShape(RadiusLg),
    )
}

/** Motion duration families (DESIGN-SYSTEM §7). */
object CadenceMotion {
    const val Fast = 150
    const val Medium = 300
    const val Slow = 450
}
