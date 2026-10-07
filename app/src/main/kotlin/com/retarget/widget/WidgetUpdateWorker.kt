/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic widget-refresh worker (M3.2).
 *
 * `updateAll` re-runs `provideGlance` for every placed widget, which pulls a
 * fresh [WidgetSnapshot] via [WidgetStateSource] — so this worker is a
 * one-liner: state collection lives in the source, composition in the
 * composer, rendering in [RetargetWidget]. Aligned with the notification
 * scheduler's ~2h cadence (RetargetWidgetReceiver.scheduleUpdates);
 * today-boundary math lives in DefaultWidgetStateSource (ZoneId injection).
 *
 * Plain CoroutineWorker (no @HiltWorker): matches the existing channel
 * workers' construction pattern and needs no injected dependencies — the
 * widget resolves its graph via [WidgetEntryPoint] inside provideGlance.
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        try {
            Log.d(TAG, "Refreshing widget snapshots")
            RetargetWidget().updateAll(applicationContext)
            Log.i(TAG, "Widget refresh complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget refresh failed", e)
            Result.retry()
        }

    companion object {
        private const val TAG = "WidgetUpdateWorker"
    }
}
