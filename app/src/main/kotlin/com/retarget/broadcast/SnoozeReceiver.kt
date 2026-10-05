/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.retarget.scheduler.NotificationScheduler
import java.util.concurrent.TimeUnit

/**
 * Receiver for "Snooze 2h" action in notifications.
 *
 * Defers the next notification for this goal by 2 hours, respecting cooldown
 * policies from BudgetPolicy. Implemented per PHASE2-CAMPAIGN.md §2.1.
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        if (goalId <= 0) {
            return
        }

        // Cancel current scheduled work
        NotificationScheduler.cancelNotificationForGoal(context, goalId)

        // Reschedule with 2-hour delay
        NotificationScheduler.scheduleNotificationForGoal(
            context,
            goalId,
            initialDelayMinutes = SNOOZE_DURATION_MINUTES,
        )
    }

    companion object {
        const val EXTRA_GOAL_ID = "goal_id"
        const val SNOOZE_DURATION_MINUTES = 120L
    }
}
