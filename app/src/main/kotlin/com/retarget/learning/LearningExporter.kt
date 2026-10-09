/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import kotlinx.serialization.Serializable

/**
 * M3.4 integration point: exports learning state and supports per-goal resets.
 *
 * - `exportData()`: serializes per-(goal, bucket, subTheme) cells as JSON (order
 *   deterministic). On-device only; no network.
 * - `resetLearning(goalId)`: clears learned signals for a specific goal.
 * - `wipeAllLearning()`: one-tap purge of all learning data (transparency/reversibility).
 *
 * All operations are idempotent and safe to call from UI threads (DAOs use
 * Room's IO dispatchers).
 */
class LearningExporter(
    private val learningDao: LearningStateDao,
) {
    /**
     * Exports all learning state as a list of rows suitable for serialization.
     * Ordered deterministically (goal, bucket, subTheme) so checksums match
     * across identical datasets.
     */
    fun exportData(): List<LearningRow> =
        learningDao.getAllForExport().map { LearningRow.from(it) }

    /**
     * Clears learning state for a single goal (reversibility per AGENTS.md §2).
     * The user can retrain their model by continuing to use the app normally.
     *
     * @return number of rows cleared (useful for audit logs, not required).
     */
    fun resetLearning(goalId: Long): Int {
        val count = learningDao.getByGoal(goalId).size
        learningDao.clearForGoal(goalId)
        return count
    }

    /**
     * Wipes all learning state (one-tap factory-reset style, reversible only by
     * re-learning from future behavior).
     *
     * @return number of rows cleared (useful for audit logs, not required).
     */
    fun wipeAllLearning(): Int {
        val count = learningDao.getAllForExport().size
        learningDao.clear()
        return count
    }

    /** Serializable snapshot row for export (JSON-friendly). */
    @Serializable
    data class LearningRow(
        val goalId: Long,
        val bucket: Int,
        val subTheme: String,
        val attempts: Int,
        val scoreSum: Double,
        val scoreCount: Int,
    ) {
        companion object {
            fun from(e: LearningStateEntity): LearningRow =
                LearningRow(e.goalId, e.bucket, e.subTheme, e.attempts, e.scoreSum, e.scoreCount)
        }
    }
}
