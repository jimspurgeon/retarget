/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.retarget.channels.notification.NotificationDeliveryWorker
import com.retarget.goal.GoalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Manages notification scheduler lifecycle in response to goal activation (Phase 2).
 *
 * Watches for active goals with notification enabled and automatically starts/stops
 * the notification delivery worker. This ensures the scheduler only runs when there's
 * a user-initiated campaign to support (AGENTS.md §2: user-initiated, reversible nudges).
 *
 * Thread safety: all public methods are safe to call from any thread. Internal
 * flow collection happens on a dedicated coroutine scope with SupervisorJob.
 */
class NotificationSchedulerManager(
    private val context: Context,
    private val goalRepository: GoalRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var isActive = false

    /**
     * Start observing goal activation changes and manage notification scheduler lifecycle.
     * Call this from Application.onCreate() or early in app startup.
     * Idempotent: subsequent calls are no-ops.
     */
    fun startMonitoring() {
        if (isActive) {
            Log.d(TAG, "Notification scheduler manager already active; skipping startMonitoring")
            return
        }
        isActive = true
        scope.launch {
            goalRepository.observeActive().collectLatest { goals ->
                handleGoalUpdate(goals)
            }
        }
        Log.i(TAG, "NotificationSchedulerManager started monitoring goals")
    }

    /**
     * Stop monitoring and cancel any active notification scheduler.
     * Safe to call multiple times.
     */
    fun stopMonitoring() {
        if (!isActive) return
        isActive = false
        scope.cancel()
        cancelAllNotificationSchedules(context)
        Log.i(TAG, "NotificationSchedulerManager stopped; all notification schedules cancelled")
    }

    /**
     * Force immediate refresh of scheduler state based on current goals.
     * Useful after programmatic goal changes that may not yet be reflected
     * in the flow emission.
     */
    fun refresh() {
        if (!isActive) {
            Log.w(TAG, "refresh() called but manager not active; call startMonitoring first")
            return
        }
        // Re-read from database to get latest state
        scope.launch {
            val goals = goalRepository.observeActive().firstOrNull() ?: emptyList()
            handleGoalUpdate(goals)
        }
    }

    private fun handleGoalUpdate(goals: List<com.retarget.goal.GoalEntity>) {
        val shouldRun = goals.any { goal ->
            try {
                val settings = com.retarget.goal.GoalConverters().jsonToSettings(goal.settingsJson)
                settings.notificationEnabled
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse settings for goal ${goal.id}; treating as disabled", e)
                false
            }
        }

        if (shouldRun) {
            scheduleNotificationDelivery(context)
            Log.d(TAG, "Started notification delivery (active goals with notification=${goals.size})")
        } else {
            cancelAllNotificationSchedules(context)
            Log.d(TAG, "Cancelled notification delivery (no active notification-enabled goals)")
        }
    }

    companion object {
        private const val TAG = "NotificationSchedulerManager"
        const val WORK_TAG = "retarget_notification_delivery"
        private const val CHECK_INTERVAL_HOURS = 2L

        /**
         * Schedules periodic notification delivery checks.
         *
         * Uses a ~2 hour periodic check to balance:
         * - Timeliness (responding to user goals within acceptable window)
         * - Battery efficiency (avoiding excessive wake-ups)
         * - Drift tolerance (±30 min acceptable per PHASE2-CAMPAIGN.md Risk 1)
         *
         * WorkManager handles Doze/idle mode automatically.
         */
        fun scheduleNotificationDelivery(context: Context, initialDelayMinutes: Long = 0) {
            WorkManager.getInstance(context).let { wm ->
                wm.cancelUniqueWork(WORK_TAG)

                // Constraints: only run on battery and network (notifications need to be timely)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Local-only, no network needed
                    .setRequiresBatteryNotLow(false)
                    .build()

                val request = PeriodicWorkRequestBuilder<NotificationDeliveryWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInitialDelay(maxOf(0, initialDelayMinutes), TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .build()

                wm.enqueueUniquePeriodicWork(
                    WORK_TAG,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request,
                )

                Log.i(TAG, "Notification delivery scheduled every ${CHECK_INTERVAL_HOURS} hours")
            }
        }

        /**
         * Cancels all scheduled notification delivery work.
         */
        fun cancelAllNotificationSchedules(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
            Log.d(TAG, "Notification delivery cancelled")
        }
    }
}
