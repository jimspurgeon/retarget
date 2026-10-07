/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Home-screen widget host receiver (M3.2).
 *
 * Registered in the manifest for APPWIDGET_UPDATE; widget metadata
 * (res/xml/retarget_widget_info.xml) sets `updatePeriodMillis 0` because
 * updates are driven by our WorkManager tick ([scheduleUpdates]), not the
 * system — no extra wakeups beyond the existing scheduler cadence.
 */
class RetargetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RetargetWidget()

    companion object {
        private const val WORK_NAME = "retarget_widget_update"
        private const val CHECK_INTERVAL_HOURS = 2L

        /**
         * Schedule widget updates on the same ~2h cadence as the notification
         * scheduler (NotificationSchedulerManager.CHECK_INTERVAL_HOURS), using
         * KEEP so rescheduling never displaces other periodic work.
         *
         * Guards on WorkManager initialization because Application.onCreate
         * runs before test harnesses (WorkManagerTestInitHelper) install their
         * instance — in production the default initializer has already run.
         */
        fun scheduleUpdates(context: android.content.Context) {
            if (!androidx.work.WorkManager.isInitialized()) {
                android.util.Log.w(TAG, "WorkManager not initialized; skipping widget schedule")
                return
            }
            val constraints =
                androidx.work.Constraints
                    .Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                    .build()
            val request =
                androidx.work
                    .PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                        CHECK_INTERVAL_HOURS,
                        java.util.concurrent.TimeUnit.HOURS,
                    ).setConstraints(constraints)
                    .addTag(WORK_NAME)
                    .build()
            androidx.work.WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }

        /** Cancel widget update work (parity with the channel scheduler managers). */
        fun cancelUpdates(context: android.content.Context) {
            androidx.work.WorkManager
                .getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }

        private const val TAG = "RetargetWidgetReceiver"
    }
}
