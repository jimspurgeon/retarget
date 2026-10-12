/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Baseline brand palette. Serves two roles:
 * 1. The complete scheme on devices below Android 12 (S), where dynamic
 *    color is unavailable.
 * 2. The seed-scheme fallback if [android.os.Build.VERSION.SDK_INT] checks
 *    ever gate dynamic color incorrectly.
 *
 * Tones chosen from Material You guidance: muted primary suitable for a
 * wellbeing nudging app — calm teal, warm amber accent, no saturated reds
 * (we deliberately avoid urgency-signaling colors; see AGENTS.md §2).
 */
val GreenPrimary = Color(0xFF00696B)
val OnGreenPrimary = Color(0xFFFFFFFF)
val GreenPrimaryContainer = Color(0xFF6FF6FB)
val OnGreenPrimaryContainer = Color(0xFF002021)

val AmberSecondary = Color(0xFF7A5900)
val AmberOnSecondary = Color(0xFFFFFFFF)
val AmberSecondaryContainer = Color(0xFFFFDF9E)
val OnAmberSecondaryContainer = Color(0xFF261A00)

val TealTertiary = Color(0xFF4B607C)
val OnTealTertiary = Color(0xFFFFFFFF)
val TealTertiaryContainer = Color(0xFFD3E4FF)
val OnTealTertiaryContainer = Color(0xFF041C35)

// Dark tones: same hues as light, tone-shifted per Material You dark-scheme guidance.
val GreenPrimaryDark = Color(0xFF4CDADC)
val OnGreenPrimaryDark = Color(0xFF003738)
val GreenPrimaryContainerDark = Color(0xFF004F51)
val OnGreenPrimaryContainerDark = Color(0xFF6FF6FB)

val AmberSecondaryDark = Color(0xFFF3BF48)
val OnAmberSecondaryDark = Color(0xFF412D00)
val AmberSecondaryContainerDark = Color(0xFF5B4300)
val OnAmberSecondaryContainerDark = Color(0xFFFFDF9E)

val TealTertiaryDark = Color(0xFFB4C9E8)
val OnTealTertiaryDark = Color(0xFF1C314B)
val TealTertiaryContainerDark = Color(0xFF334863)
val OnTealTertiaryContainerDark = Color(0xFFD3E4FF)

/** Light scheme for phones without dynamic color (pre-Android 12). */
val RetargetLightColors: ColorScheme =
    lightColorScheme(
        primary = GreenPrimary,
        onPrimary = OnGreenPrimary,
        primaryContainer = GreenPrimaryContainer,
        onPrimaryContainer = OnGreenPrimaryContainer,
        secondary = AmberSecondary,
        onSecondary = AmberOnSecondary,
        secondaryContainer = AmberSecondaryContainer,
        onSecondaryContainer = OnAmberSecondaryContainer,
        tertiary = TealTertiary,
        onTertiary = OnTealTertiary,
        tertiaryContainer = TealTertiaryContainer,
        onTertiaryContainer = OnTealTertiaryContainer,
    )

/** Dark scheme for phones without dynamic color (pre-Android 12). */
val RetargetDarkColors: ColorScheme =
    darkColorScheme(
        primary = GreenPrimaryDark,
        onPrimary = OnGreenPrimaryDark,
        primaryContainer = GreenPrimaryContainerDark,
        onPrimaryContainer = OnGreenPrimaryContainerDark,
        secondary = AmberSecondaryDark,
        onSecondary = OnAmberSecondaryDark,
        secondaryContainer = AmberSecondaryContainerDark,
        onSecondaryContainer = OnAmberSecondaryContainerDark,
        tertiary = TealTertiaryDark,
        onTertiary = OnTealTertiaryDark,
        tertiaryContainer = TealTertiaryContainerDark,
        onTertiaryContainer = OnTealTertiaryContainerDark,
    )
