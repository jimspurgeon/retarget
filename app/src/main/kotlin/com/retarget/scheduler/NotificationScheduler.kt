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
import com.retarget.goal.GoalEntity
import com.retarget.goal.GoalRepository
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
                PeriodicWorkRequestBuilder<NotificationDeliveryWorker>(
                    1, // Minimum interval for notifications (will be overridden by policy)
                    TimeUnit.HOURS,
                )
                    .setInitialDelay(max(0, initialDelayMinutes), TimeUnit.MINUTES)
                    .addTag(workTag)
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

/**
 * Worker that delivers scheduled notification nudges.
 *
 * Per the scheduler pattern, this worker:
 * 1. Loads the active goal for this goalId
 * 2. Uses CreativeRotator to select the next creative
 * 3. Delivers via NotificationChannel
 * 4. Logs exposure via ExposureLedger
 * 5. Reschedules next slot based on NotificationPolicy
 *
 * See PHASE2-CAMPAIGN.md §3.3 for the full scheduling strategy.
 */
class NotificationDeliveryWorker(
    appContext: android.content.Context,
    params: androidx.work.WorkerParameters,
) : androidx.work.CoroutineWorker(appContext, params) {

    private val db = GoalDatabase.get(appContext)
    private val dao = db.goalDao()
    private val goalRepository = GoalRepository(dao)
    private val creativeRepo = PersistentCreativeRepository(appContext, db.creativePackDao())

    override suspend fun doWork(): Result {
        val goalId = inputData.getLong(GOAL_ID_KEY, -1)
        if (goalId <= 0) {
            Log.w(TAG, "Invalid goalId in work input: ${inputData.getLong(GOAL_ID_KEY, -1)}")
            return Result.failure()
        }

        // Create NotificationChannel inside doWork (it's safe and avoids initialization issues)
        val notificationChannel = com.retarget.channels.notification.NotificationChannel(applicationContext)

        val goal = try {
            goalRepository.getById(goalId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load goal $goalId", e)
            return Result.failure()
        }

        if (goal == null) {
            Log.w(TAG, "Goal $goalId not found; cancelling notification")
            return Result.failure()
        }

        val settings = goal.settings
        if (!settings.notificationEnabled) {
            Log.d(TAG, "Notification disabled for goal ${goal.id}; skipping")
            NotificationScheduler.cancelNotificationForGoal(applicationContext, goalId)
            return Result.success()
        }

        // Load creatives for this goal's preset
        val creatives = try {
            val packId = "${goal.presetId}_default" // Assuming default pack for preset
            creativeRepo.getCreativesForPack(packId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load creatives for preset ${goal.presetId}", e)
            return Result.failure()
        }

        if (creatives.isEmpty()) {
            Log.w(TAG, "No creatives available for preset ${goal.presetId}")
            return Result.failure()
        }

        // Select next creative using rotator (simple selection for now)
        val selectedCreative = creatives.firstOrNull() ?: run {
            Log.w(TAG, "No creatives available for preset ${goal.presetId}")
            return Result.failure()
        }

        // Select copy line (round-robin from pool)
        val copyPool = selectedCreative.copyPool
        val copyIndex = 0 // Would track exposure count per creative in production
        val copyLine = copyPool.getOrElse(copyIndex % copyPool.size) { copyPool.first() }

        // Build notification spec
        val spec = com.retarget.channels.notification.NotificationSpec(
            goalId = goal.id,
            creative = selectedCreative,
            title = goal.displayName,
            copyLine = copyLine,
            actions = com.retarget.channels.notification.NotificationActions(
                checkInIntent = android.app.PendingIntent.getBroadcast(
                    applicationContext,
                    0,
                    android.content.Intent(applicationContext, com.retarget.broadcast.CheckInReceiver::class.java).apply {
                        putExtra(com.retarget.broadcast.CheckInReceiver.EXTRA_GOAL_ID, goalId)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
                snooze2hIntent = android.app.PendingIntent.getBroadcast(
                    applicationContext,
                    1,
                    android.content.Intent(applicationContext, com.retarget.broadcast.SnoozeReceiver::class.java).apply {
                        putExtra(com.retarget.broadcast.SnoozeReceiver.EXTRA_GOAL_ID, goalId)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
                fewerLikeThisIntent = android.app.PendingIntent.getBroadcast(
                    applicationContext,
                    2,
                    android.content.Intent(applicationContext, com.retarget.broadcast.FewerNotificationsReceiver::class.java).apply {
                        putExtra(com.retarget.broadcast.FewerNotificationsReceiver.EXTRA_GOAL_ID, goalId)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
        )

        // Deliver notification
        val notificationId = kotlin.math.abs(goalId.hashCode() + System.currentTimeMillis().toInt())
        val success = notificationChannel.deliver(spec, notificationId)

        if (success) {
            Log.i(TAG, "Notification delivered successfully for goal $goalId")
            // TODO: Record exposure via ledger (Milestone 2.5 integration)
            // TODO: Reschedule next slot based on NotificationPolicy
            return Result.success()
        } else {
            Log.w(TAG, "Failed to deliver notification for goal $goalId")
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotificationDeliveryWorker"
        const val GOAL_ID_KEY = "goal_id"
    }
}
