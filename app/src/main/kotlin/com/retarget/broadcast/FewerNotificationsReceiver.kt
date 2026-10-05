/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.retarget.app.R
import com.retarget.goal.GoalConverters
import com.retarget.goal.GoalDatabase
import com.retarget.scheduler.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for "Fewer like this" action in notifications.
 *
 * Per Milestone 2.6 task requirement:
 * - Decrements notificationTargetsPerDay by 1 (floor at 0)
 * - Shows Toast "Reduced to X/day. Change in Settings anytime."
 *
 * This implements the one-tap volume reduction feature from
 * PHASE2-CAMPAIGN.md §6.2, ensuring nudges are controllable.
 */
class FewerNotificationsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        if (goalId <= 0) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = GoalDatabase.get(context)
            val dao = db.goalDao()

            val goal = dao.getById(goalId) ?: return@launch

            try {
                val settings = goal.settings
                val newTargetsPerDay = maxOf(0, settings.notificationTargetsPerDay - 1)

                val updatedSettings = settings.copy(notificationTargetsPerDay = newTargetsPerDay)
                dao.updateSettings(goal.id, GoalConverters().settingsToJson(updatedSettings))

                // Cancel any current WorkManager job and reschedule with reduced frequency
                NotificationScheduler.cancelNotificationForGoal(context, goalId)
                if (updatedSettings.notificationEnabled && newTargetsPerDay > 0) {
                    // Reschedule with longer interval based on reduced target count
                    val intervalAdjustment = (3 - newTargetsPerDay) * 60L // Longer intervals
                    NotificationScheduler.scheduleNotificationForGoal(
                        context,
                        goalId,
                        initialDelayMinutes = intervalAdjustment,
                    )
                }

                // Show feedback toast (must run on main thread)
                val message = context.getString(R.string.toast_notifications_reduced, newTargetsPerDay)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val ACTION_FEWER_NOTIFICATIONS = "com.retarget.action.FEWER_NOTIFICATIONS"
        const val EXTRA_GOAL_ID = "goal_id"
    }
}
