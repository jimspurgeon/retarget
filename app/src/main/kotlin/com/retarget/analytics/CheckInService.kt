/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.util.Log
import com.retarget.goal.GoalDatabase
import com.retarget.learning.RewardRecorder

/**
 * Service for recording check-in events from notification actions.
 *
 * Uses IntentService to handle check-in recordings on a background thread.
 * Called when user taps "Check in" on a notification.
 */
class CheckInService : IntentService("CheckInService") {
    override fun onHandleIntent(intent: Intent?) {
        val goalId = intent?.getLongExtra(EXTRA_GOAL_ID, -1L) ?: -1L
        val notes = intent?.getStringExtra(EXTRA_NOTES)

        if (goalId == -1L) {
            Log.w(TAG, "Invalid goalId; cannot record check-in")
            return
        }

        // IntentService.onHandleIntent runs on a background worker thread — safe to block on the suspend DAO call
        kotlinx.coroutines.runBlocking {
        val db = GoalDatabase.get(applicationContext)
        val checkInDao = db.checkInDao()

        try {
            val nowMs = System.currentTimeMillis()
            checkInDao.insert(
                CheckInEntity(
                    goalId = goalId,
                    atMs = nowMs,
                    notes = notes,
                ),
            )
            // M3.4: bind the check-in to the bandit (+1.0 to the goal's most
            // recent ≤2h-old exposure's (bucket, subTheme), if any).
            RewardRecorder(db.learningStateDao(), db.exposureDao())
                .recordCheckIn(goalId, nowMs)
            Log.i(TAG, "Check-in recorded: goalId=$goalId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record check-in for goalId=$goalId", e)
        }
        }
    }

    companion object {
        private const val TAG = "CheckInService"
        const val EXTRA_GOAL_ID = "com.retarget.extra.GOAL_ID"
        const val EXTRA_NOTES = "com.retarget.extra.NOTES"

        /**
         * Create an intent to launch this service and record a check-in.
         *
         * @param context Context for creating the intent
         * @param goalId The goal ID being checked into
         * @param notes Optional notes about the check-in
         * @return Intent ready to be started as service
         */
        fun createActionIntent(context: Context, goalId: Long, notes: String? = null): Intent {
            return Intent(context, CheckInService::class.java).apply {
                putExtra(EXTRA_GOAL_ID, goalId)
                putExtra(EXTRA_NOTES, notes)
            }
        }
    }
}
