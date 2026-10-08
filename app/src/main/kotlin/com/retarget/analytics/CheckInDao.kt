/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Check-in event entity — records when a user taps "Check in" on a notification.
 *
 * Per PHASE2-CAMPAIGN.md Open Question 1, we store check-ins in a separate table
 * from exposures (Option A: clean domain separation — check-ins are outcomes,
 * not impressions). This enables calculation of check-in rate (acceptance rate)
 * for the dashboard.
 */
@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val atMs: Long,
    val notes: String?, // optional user-provided context for the check-in
)

/**
 * DAO for check-in events.
 *
 * Provides queries for calculating check-in rates on the dashboard.
 */
@Dao
interface CheckInDao {
    @Insert
    suspend fun insert(entity: CheckInEntity): Long

    /**
     * Count check-ins for a specific goal within today (local date in system default zone).
     *
     * @param goalId The goal ID to filter by.
     * @param startOfDayMs Milliseconds since epoch for the start of today.
     * @return Number of check-ins for this goal today.
     */
    @Query("SELECT COUNT(*) FROM check_ins WHERE goalId = :goalId AND atMs >= :startOfDayMs AND atMs < :startOfDayMs + 86400000")
    fun countByGoal(goalId: Long, startOfDayMs: Long): Int

    /**
     * Total check-in count for a specific goal (all time).
     *
     * @param goalId The goal ID to filter by.
     * @return Total number of check-ins for this goal.
     */
    @Query("SELECT COUNT(*) FROM check_ins WHERE goalId = :goalId")
    fun totalCountByGoal(goalId: Long): Int

    /** Every check-in, oldest first — deterministic order for export diffs. */
    @Query("SELECT * FROM check_ins ORDER BY atMs ASC, id ASC")
    suspend fun getAllCheckInsForExport(): List<CheckInEntity>

    /**
     * Count check-ins for all active goals within today (reactive; recomputes when
     * the table changes so dashboards stay live).
     *
     * @param startOfDayMs Milliseconds since epoch for the start of today.
     * @return Flow of goalId to check-in count for today.
     */
    @Query("SELECT goalId, COUNT(*) as cnt FROM check_ins WHERE atMs >= :startOfDayMs AND atMs < :startOfDayMs + 86400000 GROUP BY goalId")
    fun countsByGoalToday(startOfDayMs: Long): Flow<List<CheckInCount>>
}

/**
 * Helper data class for bulk check-in counts.
 */
data class CheckInCount(
    val goalId: Long,
    val cnt: Int,
)
