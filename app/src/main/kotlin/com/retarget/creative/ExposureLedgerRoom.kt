/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Room-backed exposure ledger — append-only impression log.
 *
 * Every row is one immutable exposure event (event log, not mutable state),
 * which enables both the rotator's fatigue/diversity math and future local
 * analysis without schema migrations (DEVELOPMENT.md architecture §3).
 * Local-only by design: the table never leaves the device, and it is
 * purgeable in one tap via [ExposureDao.clear].
 */
@Entity(tableName = "exposures")
data class ExposureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creativeId: String,
    val subTheme: String, // denormalized so each event is self-describing
    val channel: Channel,
    val atMs: Long,
)

/**
 * Query surface over [ExposureEntity]. Deliberately blocking (non-suspend):
 * the [ExposureLedger] contract is synchronous because the pure-Kotlin rotator
 * reads it during scoring. Room serves blocking DAO calls on its own query
 * executor, so callers must be off the main thread (the wallpaper rotation
 * worker guarantees this — it runs on Dispatchers.IO).
 */
@Dao
interface ExposureDao {
    @Insert
    fun insert(entity: ExposureEntity)

    @Query("SELECT atMs FROM exposures WHERE creativeId = :creativeId ORDER BY atMs DESC LIMIT 1")
    fun lastShownAt(creativeId: String): Long?

    @Query("SELECT COUNT(*) FROM exposures WHERE creativeId = :creativeId")
    fun timesShown(creativeId: String): Int

    /** Most recent [limit] exposures, newest first. */
    @Query("SELECT * FROM exposures ORDER BY atMs DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): List<ExposureEntity>

    /** Total number of exposures recorded. */
    @Query("SELECT COUNT(*) FROM exposures")
    fun totalExposures(): Int

    /** Every exposure event, oldest first — deterministic order for export diffs. */
    @Query("SELECT * FROM exposures ORDER BY atMs ASC, id ASC")
    suspend fun getAllExposuresForExport(): List<ExposureEntity>

    /** Number of exposures for a specific sub-theme. */
    @Query("SELECT COUNT(*) FROM exposures WHERE subTheme = :subTheme")
    fun exposuresBySubTheme(subTheme: String): Int

    /** One-tap purge (AGENTS.md §2: data must be fully deletable). */
    @Query("DELETE FROM exposures")
    fun clear()

    /** Count of exposures for a specific channel. */
    @Query("SELECT COUNT(*) FROM exposures WHERE channel = :channel")
    fun exposuresByChannel(channel: Channel): Int

    /**
     * Count of exposures for a specific channel within the day starting at
     * [startOfDayMs] (window [startOfDayMs, startOfDayMs + 24h)). Bounded so
     * past-day counts do not grow as future days accumulate rows.
     */
    @Query("SELECT COUNT(*) FROM exposures WHERE channel = :channel AND atMs >= :startOfDayMs AND atMs < :startOfDayMs + 86400000")
    fun exposuresTodayByChannel(channel: Channel, startOfDayMs: Long): Int
}

/** [ExposureLedger] adapter over [ExposureDao]; see [ExposureDao] threading notes. */
class RoomExposureLedger(
    private val dao: ExposureDao,
) : ExposureLedger {
    override fun recordExposure(
        creativeId: String,
        subTheme: String,
        channel: Channel,
        atMs: Long,
    ) {
        dao.insert(ExposureEntity(creativeId = creativeId, subTheme = subTheme, channel = channel, atMs = atMs))
    }

    override fun lastShownAt(creativeId: String): Long? = dao.lastShownAt(creativeId)

    override fun timesShown(creativeId: String): Int = dao.timesShown(creativeId)

    override fun totalExposures(): Int = dao.totalExposures()

    override fun exposuresBySubTheme(subTheme: String): Int = dao.exposuresBySubTheme(subTheme)

    override fun recentExposures(limit: Int): List<RecentExposure> =
        dao.recent(limit).map { RecentExposure(it.creativeId, it.subTheme, it.channel, it.atMs) }

    override fun exposuresByChannel(channel: Channel): Int = dao.exposuresByChannel(channel)

    override fun exposuresTodayByChannel(channel: Channel, startOfDayMs: Long): Int =
        dao.exposuresTodayByChannel(channel, startOfDayMs)
}
