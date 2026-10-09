/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import com.retarget.creative.ExposureDao
import com.retarget.creative.ExposureEntity
import com.retarget.creative.RecentExposure
import java.time.ZoneId

/**
 * Records bandit reward signals (M3.4, task 2 of the approved design).
 *
 * Reward binding rule (human-approved 2026-10-09): a signal credits the
 * (bucket, subTheme) of the most recent ledger exposure that is at most
 * [EpsilonGreedyBandit.CREDIT_WINDOW_MS] (2h) old. No qualifying exposure
 * → no credit, no learning update.
 *
 * Attribution caveat: the exposure ledger is goal-blind
 * (TODO(goal-scoped-ledger) in NudgeScheduler — no goalId column), so "the
 * most recent exposure" is taken across all goals. With the app's typical
 * single-active-goal usage this is exact; with several active goals it may
 * attribute to another goal's concurrent nudge. That error is bounded and
 * self-correcting (wrongly-credited cells regress toward the prior), and
 * fixing it properly belongs to the goal-scoped-ledger migration, not this
 * milestone. On-device only; no network; no analytics.
 *
 * Blocking (non-suspend) to mirror the ledger/learning DAO threading
 * pattern: callers run off the main thread (service worker threads,
 * Dispatchers.IO).
 */
class RewardRecorder(
    private val learningDao: LearningStateDao,
    private val exposureDao: ExposureDao,
) {
    /**
     * Credit a reward to the (bucket, subTheme) of the most recent exposure
     * within the 2h credit window.
     *
     * @param goalId  the goal the signal belongs to (rows are per-goal even
     *                though attribution is ledger-wide; see class doc)
     * @param reward  reward magnitude (see EpsilonGreedyBandit constants)
     * @param nowMs   attribution time (injectable for tests)
     * @return true if credit was applied; false when no exposure within the
     *         credit window exists
     */
    fun recordReward(
        goalId: Long,
        reward: Double,
        nowMs: Long,
    ): Boolean {
        val exposure = mostRecentWithinWindow(nowMs) ?: return false
        credit(goalId, exposure, reward)
        return true
    }

    /** +1.0 — the user checked in on this goal (strongest positive). */
    fun recordCheckIn(
        goalId: Long,
        nowMs: Long,
    ): Boolean = recordReward(goalId, EpsilonGreedyBandit.REWARD_CHECK_IN, nowMs)

    /**
     * Strong negative (−1.0) — the user tapped "fewer like this" on a
     * creative (UI wiring in Wave 2; this method is the Wave 1 service
     * surface).
     *
     * Credits the (bucket, subTheme) of [creativeId]'s most recent exposure
     * within the window — creative-scoped, so the negative lands on exactly
     * the theme the user rejected. Falls back to the goal's most recent
     * exposure if the creative has none (defensive).
     */
    fun recordFewerLikeThis(
        goalId: Long,
        creativeId: String,
        nowMs: Long,
    ): Boolean {
        val exposure = creativeExposureWithinWindow(creativeId, nowMs)
            ?: mostRecentWithinWindow(nowMs)
            ?: return false
        credit(goalId, exposure, EpsilonGreedyBandit.REWARD_FEWER_LIKE_THIS)
        return true
    }

    /** Mild negative (−0.25) — the user snoozed the goal's nudge. */
    fun recordSnooze(
        goalId: Long,
        nowMs: Long,
    ): Boolean = recordReward(goalId, EpsilonGreedyBandit.REWARD_SNOOZE, nowMs)

    private fun credit(
        goalId: Long,
        exposure: RecentExposure,
        reward: Double,
    ) {
        val bucket = EpsilonGreedyBandit.bucketOf(exposure.atMs, ZoneId.systemDefault())
        val existing = learningDao.get(goalId, bucket, exposure.subTheme)
        learningDao.upsert(
            EpsilonGreedyBandit.applyReward(existing, goalId, bucket, exposure.subTheme, reward),
        )
    }

    private fun mostRecentWithinWindow(nowMs: Long): RecentExposure? =
        exposureDao.recent(RECENT_LIMIT)
            .asSequence()
            .firstOrNull { nowMs - it.atMs in 0..EpsilonGreedyBandit.CREDIT_WINDOW_MS }
            ?.toRecentExposure()

    private fun creativeExposureWithinWindow(
        creativeId: String,
        nowMs: Long,
    ): RecentExposure? =
        exposureDao.recent(RECENT_LIMIT)
            .asSequence()
            .firstOrNull {
                it.creativeId == creativeId &&
                    nowMs - it.atMs in 0..EpsilonGreedyBandit.CREDIT_WINDOW_MS
            }
            ?.toRecentExposure()

    private fun ExposureEntity.toRecentExposure(): RecentExposure =
        RecentExposure(creativeId = creativeId, subTheme = subTheme, channel = channel, atMs = atMs)

    companion object {
        /** How many recent ledger rows to scan for attribution (2h window). */
        const val RECENT_LIMIT = 50
    }
}
