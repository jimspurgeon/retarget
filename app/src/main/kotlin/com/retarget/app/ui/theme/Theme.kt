/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App-wide Material You theme. Uses dynamic (wallpaper-derived) color on
 * Android 12+ and the static baseline schemes below that, so the brand
 * stays calm and legible everywhere. Dark mode follows the system setting.
 */
@Composable
fun RetargetTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        } else {
            if (darkTheme) RetargetDarkColors else RetargetLightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RetargetTypography,
        shapes = RetargetShapes,
        content = content,
    )
}
