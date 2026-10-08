/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(goal: GoalEntity): Long

    @Query("SELECT * FROM goals WHERE active = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<GoalEntity>>

    /** Get all active goals as a list (blocking call for internal use). */
    @Query("SELECT * FROM goals WHERE active = 1 ORDER BY createdAt DESC")
    fun getAllActiveGoals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE presetId = :presetId LIMIT 1")
    suspend fun byPresetId(presetId: String): GoalEntity?

    /** Every goal, including inactive ones, oldest first — the export is a full backup. */
    @Query("SELECT * FROM goals ORDER BY createdAt ASC, id ASC")
    suspend fun getAllGoalsForExport(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GoalEntity?

    @Query("UPDATE goals SET active = :active WHERE id = :id")
    suspend fun setActive(
        id: Long,
        active: Boolean,
    )

    @Query("SELECT COUNT(*) FROM goals WHERE active = 1")
    suspend fun activeCount(): Int

    @Query("UPDATE goals SET settingsJson = :settingsJson WHERE id = :id")
    suspend fun updateSettings(
        id: Long,
        settingsJson: String,
    )
}
