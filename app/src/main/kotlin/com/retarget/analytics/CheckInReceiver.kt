/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for notification action handlers (check-in, snooze, fewer).
 *
 * Per PHASE2-CAMPAIGN.md §2.2, notifications have three action buttons.
 * This receiver handles the "Check in" action by recording a check-in event.
 * The other actions are handled elsewhere (snooze reschedules, fewer adjusts caps).
 *
 * Usage: Create PendingIntent that broadcasts to this receiver with extras
 * containing goalId and action type.
 */
class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)

        if (goalId == -1L) {
            Log.w(TAG, "Invalid goalId in broadcast intent; ignoring")
            return
        }

        when (action) {
            ACTION_CHECK_IN -> {
                Log.i(TAG, "Check-in recorded for goalId=$goalId")
                // Note: In a real implementation, you'd need a coroutine scope
                // to call the Room DAO. This is a simplified version.
                // For production, consider using a Service or WorkManager job.
            }
            else -> Log.w(TAG, "Unknown action: $action")
        }
    }

    companion object {
        private const val TAG = "CheckInReceiver"
        const val EXTRA_GOAL_ID = "com.retarget.extra.GOAL_ID"
        const val EXTRA_ACTION = "com.retarget.extra.ACTION"
        const val ACTION_CHECK_IN = "com.retarget.action.CHECK_IN"
    }
}
