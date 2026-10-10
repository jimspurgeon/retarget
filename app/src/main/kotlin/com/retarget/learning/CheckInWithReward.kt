/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import androidx.room.withTransaction
import com.retarget.analytics.CheckInDao
import com.retarget.analytics.CheckInEntity
import com.retarget.goal.GoalDatabase
import java.time.ZoneId

/**
 * Persists check-ins AND binds the M3.4 bandit reward signal in one
 * transaction (task 2 of the approved design): recording a check-in for a
 * goal credits +1.0 to the (bucket, subTheme) of the goal's most recent
 * exposure within the 2h credit window, if any.
 *
 * Transactional so a check-in row and its learning credit can never diverge
 * (an outcome logged without learning, or learning without an outcome).
 * Check-in persistence semantics are identical to [CheckInDao.insert].
 */
class CheckInWithReward(
    private val db: GoalDatabase,
    private val rewardRecorder: RewardRecorder,
) {
    /**
     * Records a check-in and, if a qualifying exposure exists, credits the
     * bandit.
     *
     * @return the inserted check-in row id
     */
    suspend fun checkIn(
        goalId: Long,
        nowMs: Long = System.currentTimeMillis(),
        notes: String? = null,
    ): Long =
        db.withTransaction {
            val id =
                db.checkInDao().insert(
                    CheckInEntity(goalId = goalId, atMs = nowMs, notes = notes),
                )
            // Reward attribution is best-effort by design: a check-in with no
            // recent exposure earns no credit (the action wasn't nudged).
            rewardRecorder.recordCheckIn(goalId, nowMs)
            id
        }
}
