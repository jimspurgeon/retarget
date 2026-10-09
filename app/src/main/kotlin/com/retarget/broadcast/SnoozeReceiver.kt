/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.retarget.goal.GoalDatabase
import com.retarget.learning.RewardRecorder
import com.retarget.scheduler.NotificationScheduler
import com.retarget.scheduler.SnoozeSuppression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for "Snooze 2h" action in notifications.
 *
 * Suppresses the next notification for this goal for 2 hours. Implemented per
 * PHASE2-CAMPAIGN.md §2.1.
 *
 * Hotfix (v0.3.x scheduling consolidation): this receiver previously
 * re-scheduled a legacy per-goal periodic worker via
 * [NotificationScheduler.scheduleNotificationForGoal], which the global
 * delivery worker ignored — so snoozing had no effect. It now persists a
 * snooze-until timestamp that [com.retarget.channels.notification.NotificationDeliveryWorker]
 * consults before delivering, making the snooze effective regardless of when
 * the global worker fires.
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        if (goalId <= 0) {
            return
        }

        val now = System.currentTimeMillis()

        // Record the snooze window the global delivery worker will honor.
        SnoozeSuppression.snooze(
            context = context,
            goalId = goalId,
            untilMs = now + SNOOZE_DURATION_MINUTES * 60 * 1000L,
        )

        // M3.4: a snooze is a mild negative reward (−0.25) — the nudge was
        // mistimed, not unwanted. Fire-and-forget on IO: losing one reward to
        // process death is acceptable (statistical input, not transactional).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = GoalDatabase.get(context)
                RewardRecorder(db.learningStateDao(), db.exposureDao())
                    .recordSnooze(goalId, now)
            } catch (e: Exception) {
                android.util.Log.w("SnoozeReceiver", "Failed to record snooze reward for goalId=$goalId", e)
            }
        }

        // Clean up any legacy per-goal periodic work left by earlier versions
        // (scheduleNotificationForGoal is now a no-op shim that cancels).
        NotificationScheduler.cancelNotificationForGoal(context, goalId)
    }

    companion object {
        const val ACTION_SNOOZE = "com.retarget.action.SNOOZE"
        const val EXTRA_GOAL_ID = "goal_id"
        const val SNOOZE_DURATION_MINUTES = 120L
    }
}
