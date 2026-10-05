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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for "Check in" action in notifications.
 *
 * Part of Milestone 2.7 (Check-In Loop): records when a user acts on their goal
 * after receiving a nudge. This converts an exposure into a behavior log.
 *
 * TODO(Milestone 2.7): Implement actual check-in persistence to CheckInDao.
 */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        if (goalId <= 0) {
            return
        }

        // TODO: Record check-in in separate table (see PHASE2-CAMPAIGN.md Open Question 1)
        // For now, just acknowledge the action
        CoroutineScope(Dispatchers.IO).launch {
            // Placeholder for future check-in recording
            // val db = GoalDatabase.getDatabase(context)
            // val checkInDao = db.checkInDao()
            // checkInDao.recordCheckIn(goalId, System.currentTimeMillis())
        }
    }

    companion object {
        const val EXTRA_GOAL_ID = "goal_id"
    }
}
