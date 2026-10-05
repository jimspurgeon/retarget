/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.retarget.creative.Creative
import com.retarget.creative.CreativeImageCache
import com.retarget.creative.CreativeRotator
import com.retarget.creative.PersistentCreativeRepository
import com.retarget.goal.GoalDatabase
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Scheduling helpers for notification nudges (Phase 2, Milestone 2.4).
 *
 * - [scheduleNotificationForGoal] registers a periodic work request per goal.
 * - [cancelNotificationForGoal] cancels the worker by unique tag (goal-specific).
 * - [cancelAllNotificationsForGoal] cancels all notification work for a goal.
 *
 * Follows the same pattern as WallpaperScheduler for consistency and testability.
 * Per PHASE2-CAMPAIGN.md §3.3, each goal gets its own WorkManager tag.
 */
object NotificationScheduler {
    private const val WORKER_TAG_PREFIX = "retarget_notification_"

    /**
     * Registers a periodic notification worker for a specific goal.
     *
     * Idempotent: cancels any prior work with the same tag before enqueuing.
     *
     * @param goalId The goal ID this notification is for.
     * @param context Android context.
     * @param initialDelayMinutes Delay before first execution (0 for immediate).
     */
    fun scheduleNotificationForGoal(
        context: Context,
        goalId: Long,
        initialDelayMinutes: Long = 0,
    ) {
        val workTag = "${WORKER_TAG_PREFIX}${goalId}"
        Log.d(TAG, "Scheduling notification for goal $goalId with tag $workTag")

        WorkManager.getInstance(context).let { wm ->
            wm.cancelUniqueWork(workTag)
            val request =
                PeriodicWorkRequestBuilder<com.retarget.channels.notification.NotificationDeliveryWorker>(
                    1, // Minimum interval for notifications (will be overridden by policy)
                    TimeUnit.HOURS,
                )
                    .setInitialDelay(max(0, initialDelayMinutes), TimeUnit.MINUTES)
                    .addTag(workTag)
                    .addTag(NotificationSchedulerManager.WORK_TAG)
                    .build()
            wm.enqueueUniquePeriodicWork(
                workTag,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
            Log.i(TAG, "Notification worker enqueued for goal $goalId")
        }
    }

    /**
     * Cancels any scheduled notification work for a specific goal.
     *
     * @param goalId The goal ID to cancel notifications for.
     */
    fun cancelNotificationForGoal(context: Context, goalId: Long) {
        val workTag = "${WORKER_TAG_PREFIX}${goalId}"
        Log.d(TAG, "Cancelling notification for goal $goalId with tag $workTag")
        WorkManager.getInstance(context).cancelUniqueWork(workTag)
        Log.i(TAG, "Notification worker cancelled for goal $goalId")
    }

    /**
     * Cancels ALL notification work associated with a goal (including any legacy tags).
     *
     * This is the public API surface used when a user disables notifications
     * or deletes a goal. Ensures cleanup even if tag naming evolves.
     *
     * @param goalId The goal ID to fully cancel notifications for.
     */
    fun cancelAllNotificationsForGoal(context: Context, goalId: Long) {
        // Primary method: cancel by known tag
        cancelNotificationForGoal(context, goalId)

        // Future-proof: if we add additional worker types, cancel them here too
        // Example: cancelUniqueWork("${WORKER_TAG_PREFIX}${goalId}_retry")
    }

    private const val TAG = "NotificationScheduler"
}
