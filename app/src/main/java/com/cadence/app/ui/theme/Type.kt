package com.cadence.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Semantic accessors so screens can use the token roles (DESIGN-SYSTEM §1). */
val androidx.compose.material3.Typography.headingLarge: TextStyle get() = headlineLarge
val androidx.compose.material3.Typography.headingMedium: TextStyle get() = titleLarge
val androidx.compose.material3.Typography.metadata: TextStyle get() = labelSmall
val androidx.compose.material3.Typography.label: TextStyle get() = titleMedium

/**
 * Token-driven type roles (DESIGN-SYSTEM §1/§2): display / heading / body /
 * label / metadata. All sizes are sp so font-scale reflow is preserved.
 */
object CadenceTypography {

    val displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    )

    val displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )

    val headingLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    val headingMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    val bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    val bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val metadata = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** Material3 mapping onto the above roles. */
    val material: Typography = Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        headlineLarge = headingLarge,
        headlineMedium = headingMedium,
        titleLarge = headingMedium,
        titleMedium = label,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        labelLarge = label,
        labelMedium = labelSmall,
        labelSmall = metadata,
    )
}
