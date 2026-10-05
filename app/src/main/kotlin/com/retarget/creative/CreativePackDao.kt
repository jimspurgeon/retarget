/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for creative pack ingestion and querying.
 * Used by PersistentCreativeRepository for one-time asset loading.
 */
@Dao
interface CreativePackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPack(pack: CreativePackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCreatives(creatives: List<CreativeEntity>)

    @Query("SELECT * FROM creative_packs ORDER BY ingestTimestamp DESC")
    fun loadAllPacks(): Flow<List<CreativePackEntity>>

    @Query("SELECT * FROM creative_packs WHERE id = :packId")
    suspend fun getPackById(packId: String): CreativePackEntity?

    @Query("SELECT * FROM creative_packs WHERE goalTheme = :theme ORDER BY ingestTimestamp DESC")
    fun getPacksByGoalTheme(theme: String): Flow<List<CreativePackEntity>>

    @Query("SELECT * FROM creatives WHERE packId = :packId ORDER BY id")
    suspend fun getCreativesForPack(packId: String): List<CreativeEntity>

    @Query(
        """
        SELECT c.* FROM creatives c
        INNER JOIN creative_packs p ON c.packId = p.id
        WHERE p.goalTheme IN (:goalThemes)
        ORDER BY c.baseAppeal DESC, c.id
    """,
    )
    suspend fun getCandidatesForActiveGoals(goalThemes: List<String>): List<CreativeEntity>

    @Query("SELECT DISTINCT goalTheme FROM creative_packs")
    suspend fun getAvailableGoalThemes(): List<String>

    @Delete
    suspend fun deletePack(pack: CreativePackEntity)

    @Query("DELETE FROM creative_packs WHERE id = :packId")
    suspend fun deletePackById(packId: String)

    @Query("SELECT COUNT(*) FROM creative_packs")
    suspend fun getPackCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM creative_packs WHERE id = :packId)")
    suspend fun packExists(packId: String): Boolean
}
