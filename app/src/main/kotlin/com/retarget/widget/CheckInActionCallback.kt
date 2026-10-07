/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Check-in action for the home-screen widget (M3.2).
 *
 * PATH CHOICE (logged decision per spec): reuses the SAME persistence path as
 * the notification check-in flow — a `CheckInDao.insert` row — but invoked via
 * [WidgetStateSource.checkIn] rather than the [com.retarget.analytics.CheckInService]
 * IntentService indirection. Rationale: CheckInService is a deprecated-API
 * trampoline whose only logic is exactly this insert (record + log); going
 * through a broadcast → service chain from a coroutine context would add two
 * hops and an extra manifest component for no behavioral gain. The insert is
 * the same table, same shape, same semantics as CheckInService.onHandleIntent.
 * Both paths remain functional; consolidating CheckInService onto
 * WidgetStateSource is deferred to the Phase 3 cleanup noted in the manifest.
 */
class CheckInActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withContext(Dispatchers.IO) {
            val entryPoint = WidgetEntryPoint.get(context)
            val stateSource = entryPoint.widgetStateSource()

            val goalId = stateSource.checkIn()
            if (goalId != null) {
                Log.i(TAG, "Check-in recorded for goalId=$goalId")
                // Refresh immediately so the checked-in state shows on the widget.
                RetargetWidget().updateAll(context)
            } else {
                Log.w(TAG, "Check-in skipped: no active goal to attribute it to")
            }
        }
    }

    companion object {
        private const val TAG = "CheckInActionCallback"

        /** Display-state flag passed from the widget (unused for persistence, kept for debugging). */
        val CHECKED_IN_TODAY = ActionParameters.Key<Boolean>("checked_in_today")
    }
}
