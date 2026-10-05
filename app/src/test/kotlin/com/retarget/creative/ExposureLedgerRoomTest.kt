/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round-trip tests for the Room-backed exposure ledger (in-memory DB via
 * Robolectric). Exercises the append-only event log end to end: insert →
 * lastShownAt / timesShown / recentExposures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExposureLedgerRoomTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var ledger: RoomExposureLedger
    private lateinit var dao: ExposureDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, com.retarget.goal.GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.exposureDao()
        ledger = RoomExposureLedger(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recordExposure round-trips lastShownAt and timesShown`() {
        ledger.recordExposure("fresh-air/abc", "forest", Channel.WALLPAPER, atMs = 1_000L)
        ledger.recordExposure("fresh-air/abc", "forest", Channel.WALLPAPER, atMs = 5_000L)
        ledger.recordExposure("fruit/xyz", "berries", Channel.NOTIFICATION, atMs = 3_000L)

        assertEquals(5_000L, ledger.lastShownAt("fresh-air/abc"))
        assertEquals(2, ledger.timesShown("fresh-air/abc"))
        assertEquals(1, ledger.timesShown("fruit/xyz"))
    }

    @Test
    fun `unseen creative has no lastShownAt and zero count`() {
        assertNull(ledger.lastShownAt("never-shown"))
        assertEquals(0, ledger.timesShown("never-shown"))
    }

    @Test
    fun `recentExposures returns newest first up to limit`() {
        val events =
            listOf(
                Triple("c1", "alpha", 100L),
                Triple("c2", "beta", 300L),
                Triple("c3", "alpha", 200L),
                Triple("c4", "gamma", 400L),
            )
        events.forEach { (id, theme, at) -> ledger.recordExposure(id, theme, Channel.WALLPAPER, at) }

        val recent = ledger.recentExposures(limit = 3)
        assertEquals(listOf("c4", "c2", "c3"), recent.map { it.creativeId })
        assertEquals(listOf(400L, 300L, 200L), recent.map { it.atMs })

        assertEquals(4, ledger.recentExposures(limit = 10).size)
    }

    @Test
    fun `clear purges all exposure history`() {
        ledger.recordExposure("c1", "alpha", Channel.WALLPAPER, atMs = 1L)
        ledger.recordExposure("c2", "beta", Channel.WALLPAPER, atMs = 2L)

        dao.clear()

        assertEquals(0, ledger.timesShown("c1"))
        assertEquals(0, ledger.recentExposures(limit = 100).size)
        assertTrue(ledger.recentExposures(limit = 100).isEmpty())
    }

    // ---- Channel-based exposure counting (Milestone 2.5) ----

    @Test
    fun `exposuresByChannel returns correct count per channel`() {
        ledger.recordExposure("c1", "default", Channel.WALLPAPER, atMs = 1_000L)
        ledger.recordExposure("c2", "default", Channel.WALLPAPER, atMs = 2_000L)
        ledger.recordExposure("c3", "default", Channel.NOTIFICATION, atMs = 3_000L)
        ledger.recordExposure("c4", "default", Channel.WIDGET, atMs = 4_000L)
        ledger.recordExposure("c5", "default", Channel.NOTIFICATION, atMs = 5_000L)

        assertEquals(2, ledger.exposuresByChannel(Channel.WALLPAPER))
        assertEquals(2, ledger.exposuresByChannel(Channel.NOTIFICATION))
        assertEquals(1, ledger.exposuresByChannel(Channel.WIDGET))
        assertEquals(0, ledger.exposuresByChannel(Channel.OVERLAY))
    }

    @Test
    fun `exposuresTodayByChannel filters by startOfDayMs`() {
        // Exposures before the day starts
        ledger.recordExposure("c1", "default", Channel.NOTIFICATION, atMs = 1_000L)
        ledger.recordExposure("c2", "default", Channel.WALLPAPER, atMs = 2_000L)

        // Exposures during this day (startOfDayMs = 5_000L)
        ledger.recordExposure("c3", "default", Channel.NOTIFICATION, atMs = 5_000L)
        ledger.recordExposure("c4", "default", Channel.NOTIFICATION, atMs = 6_000L)
        ledger.recordExposure("c5", "default", Channel.WALLPAPER, atMs = 7_000L)

        // Today's notification count
        assertEquals(2, ledger.exposuresTodayByChannel(Channel.NOTIFICATION, 5_000L))

        // Today's wallpaper count
        assertEquals(1, ledger.exposuresTodayByChannel(Channel.WALLPAPER, 5_000L))

        // Earlier day boundary excludes more exposures
        assertEquals(1, ledger.exposuresTodayByChannel(Channel.NOTIFICATION, 2_000L))
    }

    @Test
    fun `channel counts work correctly across day boundaries`() {
        // Day 1
        val day1Start = 0L
        ledger.recordExposure("c1", "default", Channel.NOTIFICATION, atMs = 1_000L)
        ledger.recordExposure("c2", "default", Channel.NOTIFICATION, atMs = 2_000L)
        ledger.recordExposure("c3", "default", Channel.NOTIFICATION, atMs = 3_000L)

        assertEquals(3, ledger.exposuresTodayByChannel(Channel.NOTIFICATION, day1Start))

        // Day 2 (86400000 ms = 1 day later)
        val day2Start = 86_400_000L
        ledger.recordExposure("c4", "default", Channel.NOTIFICATION, atMs = day2Start + 1_000L)
        ledger.recordExposure("c5", "default", Channel.NOTIFICATION, atMs = day2Start + 2_000L)

        // Counts should be separate per day
        assertEquals(3, ledger.exposuresTodayByChannel(Channel.NOTIFICATION, day1Start))
        assertEquals(2, ledger.exposuresTodayByChannel(Channel.NOTIFICATION, day2Start))

        // Total channel count includes all days
        assertEquals(5, ledger.exposuresByChannel(Channel.NOTIFICATION))
    }
}
