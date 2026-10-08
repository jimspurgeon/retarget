/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import android.util.Log
import androidx.work.WorkManager

/**
 * Legacy per-goal notification scheduling surface, converted to a consolidation
 * shim (hotfix for v0.3.x notification spam).
 *
 * v0.3.x shipped TWO independent periodic schedulers for notification delivery:
 * this one (unique work `retarget_notification_<goalId>`, hourly, per goal) and
 * [NotificationSchedulerManager]'s global worker (`retarget_notification_delivery`,
 * every 2h). Running concurrently they produced up to ~36 delivery attempts/day
 * per goal and back-to-back notifications. The global periodic worker is now
 * the single scheduling path; this object no longer enqueues any periodic work.
 *
 * Historical API kept as no-op / cancellation shims so existing callers
 * ([SnoozeReceiver], [FewerNotificationsReceiver], settings UI) compile and
 * cleanly cancel leftover legacy per-goal work instead of re-enqueuing it.
 * The migration sweep ([NotificationSchedulerManager.sweepLegacyGoalWork])
 * removes any remaining `retarget_notification_<id>` work on first run after
 * update.
 */
object NotificationScheduler {
    private const val TAG = "NotificationScheduler"

    /** Prefix of the legacy per-goal unique-work names this shim cancels. */
    const val LEGACY_WORK_TAG_PREFIX = "retarget_notification_"

    /**
     * Legacy: used to enqueue per-goal periodic notification work.
     *
     * Now a no-op shim: only cancels any leftover per-goal work with the legacy
     * tag so a call from an outdated code path can never resurrect the second
     * scheduling path. Delivery scheduling is exclusively owned by
     * [NotificationSchedulerManager.scheduleNotificationDelivery].
     */
    fun scheduleNotificationForGoal(
        context: Context,
        goalId: Long,
        initialDelayMinutes: Long = 0,
    ) {
        Log.w(
            TAG,
            "scheduleNotificationForGoal is a legacy no-op shim (consolidated to global delivery " +
                "worker); cancelling leftover per-goal work for goal $goalId",
        )
        cancelNotificationForGoal(context, goalId)
    }

    /**
     * Cancels any legacy per-goal periodic work for [goalId].
     *
     * The global delivery worker picks up eligibility (including this goal) on
     * its next periodic pass — nothing is rescheduled here.
     */
    fun cancelNotificationForGoal(
        context: Context,
        goalId: Long,
    ) {
        val workTag = "${LEGACY_WORK_TAG_PREFIX}${goalId}"
        WorkManager.getInstance(context).cancelUniqueWork(workTag)
        Log.i(TAG, "Legacy notification work cancelled for goal $goalId")
    }

    /**
     * Cancels ALL notification work associated with a goal (legacy tags included).
     *
     * Kept as the public API for "user disabled notifications for this goal".
     * The global periodic worker survives; the delivery worker filters goals
     * by their per-goal settings, so a disabled goal receives no notifications.
     */
    fun cancelAllNotificationsForGoal(
        context: Context,
        goalId: Long,
    ) {
        cancelNotificationForGoal(context, goalId)
    }
}
