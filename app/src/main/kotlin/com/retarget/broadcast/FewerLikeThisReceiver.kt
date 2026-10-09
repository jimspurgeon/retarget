/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.retarget.goal.GoalDatabase
import com.retarget.learning.RewardRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for the "Fewer like this" nudge action (M3.4 Wave 1).
 *
 * Records a strong negative reward (−1.0) against the creative's
 * (bucket, subTheme) learning cell so the bandit steers the goal's
 * creative mix away from themes the user rejected. The action itself is
 * user-initiated, transparent feedback — the opposite of a dark pattern:
 * it exists precisely so the user can train the app.
 *
 * UI wiring (notification action button) lands in Wave 2; this receiver is
 * the registered action target so Wave 2 only needs to add the button.
 */
class FewerLikeThisReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        val creativeId = intent.getStringExtra(EXTRA_CREATIVE_ID) ?: return
        if (goalId <= 0 || creativeId.isEmpty()) return

        // Broadcast receivers are short-lived; learning-state writes are
        // fire-and-forget on IO (losing one to process death is acceptable —
        // the reward is statistical, not transactional, input).
       CoroutineScope(Dispatchers.IO).launch {
            val db = GoalDatabase.get(context)
            RewardRecorder(db.learningStateDao(), db.exposureDao())
                .recordFewerLikeThis(goalId, creativeId, System.currentTimeMillis())
        }
    }

    companion object {
        const val ACTION_FEWER_LIKE_THIS = "com.retarget.action.FEWER_LIKE_THIS"
        const val EXTRA_GOAL_ID = "goal_id"
        const val EXTRA_CREATIVE_ID = "creative_id"
    }
}
