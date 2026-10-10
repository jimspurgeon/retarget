/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import androidx.room.Entity
import androidx.room.Index

/**
 * Learning state for bandit-style adaptation (M3.4).
 *
 * Tracks per-goal, per-time-bucket, per-subTheme observations:
 * - `attempts`: how many nudges delivered in this (bucket, subTheme) combination
 * - `scoreSum`: accumulated reward signal (e.g., +1.0 for check-in within 2h)
 * - `scoreCount`: how many reward signals received
 *
 * Time buckets are 4-hour windows: 0-3, 4-7, 8-11, 12-15, 16-19, 20-23 (values 0-5).
 *
 * Primary key is composite (goalId, bucket, subTheme) to track each combination
 * independently. The table is on-device only, exported with goal data, and
 * cleared by resetLearning(goalId).
 *
 * @param goalId The goal this learning row belongs to
 * @param bucket Time bucket (0-5) at delivery time
 * @param subTheme Creative subTheme (e.g., "refresh", "discipline")
 * @param attempts Total nudges delivered in this combination
 * @param scoreSum Sum of reward signals received
 * @param scoreCount Number of reward signals recorded
 */
@Entity(
    tableName = "learning_state",
    primaryKeys = ["goalId", "bucket", "subTheme"],
    indices = [
        Index(value = ["goalId"]), // Fast lookup for reset/export — covers (goalId, bucket) prefixes too
    ],
)
data class LearningStateEntity(
    val goalId: Long,
    val bucket: Int, // 0-5 representing 4-hour time windows
    val subTheme: String,
    val attempts: Int = 0,
    val scoreSum: Double = 0.0,
    val scoreCount: Int = 0,
) {
    /** Computed success rate (scoreSum / scoreCount), or 0.0 if no observations. */
    fun successRate(): Double {
        return if (scoreCount > 0) scoreSum / scoreCount else 0.0
    }

    /** Total observations (attempts) for this (goal, bucket, subTheme). */
    fun totalObservations(): Int = attempts
}
