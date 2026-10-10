/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.retarget.analytics.CheckInEntity
import com.retarget.goal.GoalDatabase
import com.retarget.learning.RewardRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for the "Check in" action on notifications (Milestone 2.7 check-in
 * loop, completed in M3.4 per gatekeeper review M2).
 *
 * Records a check-in row and, when the tap lands within the 2h credit window
 * of a delivered nudge, credits the bandit's positive reward (+1.0) to the
 * exposed (bucket, subTheme) cell — same wiring as the widget path
 * ([com.retarget.widget.DefaultWidgetStateSource.checkIn]).
 */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        if (goalId <= 0) {
            return
        }

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = GoalDatabase.get(appContext)
                val nowMs = System.currentTimeMillis()
                db.checkInDao().insert(
                    CheckInEntity(goalId = goalId, atMs = nowMs, notes = null),
                )
                RewardRecorder(db.learningStateDao(), db.exposureDao())
                    .recordCheckIn(goalId, nowMs)
                Log.i(TAG, "Check-in recorded from notification: goalId=$goalId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record check-in for goalId=$goalId", e)
            }
        }
    }

    companion object {
        private const val TAG = "CheckInReceiver"
        const val ACTION_CHECK_IN = "com.retarget.action.CHECK_IN"
        const val EXTRA_GOAL_ID = "goal_id"
    }
}
