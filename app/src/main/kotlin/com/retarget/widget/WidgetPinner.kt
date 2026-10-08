/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * Requests pinning the home-screen widget (M3.2, gatekeeper M2 fix).
 *
 * The widget's `android:configure` activity ([WidgetConfigurationActivity],
 * referenced in res/xml/retarget_widget_info.xml) launches as part of the pin
 * flow and persists the per-goal widget preference there — that is the
 * "widget add flow sets per-goal preference" step from PHASE3-AGENCY.md §3.
 *
 * `requestPinAppWidget` is API 26+, which equals our minSdk — the guard is
 * defensive only (Robolectric shadows can under-report the SDK level).
 *
 * Launcher support varies (some OEM launchers disallow pinning); the return
 * value tells the caller whether the system ACCEPTED the request, not whether
 * the user completed the pin — the launcher owns that UI.
 */
object WidgetPinner {
    /**
     * Asks the launcher to pin the widget. Returns false when the device or
     * launcher does not support pinning; callers should tell the user they can
     * add the widget from the launcher's widget picker instead.
     */
    fun pinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false // defensive; minSdk is 26 (O)
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(
            ComponentName(context, RetargetWidgetReceiver::class.java),
            null,
            null,
        )
    }
}
