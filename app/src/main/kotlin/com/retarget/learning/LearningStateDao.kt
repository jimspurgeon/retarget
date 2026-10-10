/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/**
 * Data access for bandit learning state (M3.4).
 *
 * Blocking (non-suspend) to mirror [com.retarget.creative.ExposureDao]'s threading
 * pattern: the pure-Kotlin scheduler and rotator read learning state synchronously
 * during scoring, on Room's query executor, off the main thread.
 */
@Dao
interface LearningStateDao {
    /** Insert-or-replace by primary key (goalId, bucket, subTheme). */
    @Upsert
    fun upsert(entity: LearningStateEntity)

    /** Batch insert-or-replace. */
    @Upsert
    fun upsertAll(entities: List<LearningStateEntity>)

    /** Learning state for one (goal, bucket, subTheme) combination. */
    @Query(
        "SELECT * FROM learning_state WHERE goalId = :goalId AND bucket = :bucket AND subTheme = :subTheme LIMIT 1",
    )
    fun get(
        goalId: Long,
        bucket: Int,
        subTheme: String,
    ): LearningStateEntity?

    /** All learning rows for one goal. */
    @Query("SELECT * FROM learning_state WHERE goalId = :goalId")
    fun getByGoal(goalId: Long): List<LearningStateEntity>

    /** All learning rows for one goal's time bucket (any subTheme). */
    @Query("SELECT * FROM learning_state WHERE goalId = :goalId AND bucket = :bucket")
    fun getByGoalAndBucket(
        goalId: Long,
        bucket: Int,
    ): List<LearningStateEntity>

    /** Every learning row, ascending by goal/bucket/subTheme — deterministic order for export. */
    @Query("SELECT * FROM learning_state ORDER BY goalId ASC, bucket ASC, subTheme ASC")
    fun getAllForExport(): List<LearningStateEntity>

    /** Clear learning for one goal (reset learning button, AGENTS.md §2 reversibility). */
    @Query("DELETE FROM learning_state WHERE goalId = :goalId")
    fun clearForGoal(goalId: Long)

    /** One-tap purge of all learning state (AGENTS.md §2: fully deletable). */
    @Query("DELETE FROM learning_state")
    fun clear()
}
