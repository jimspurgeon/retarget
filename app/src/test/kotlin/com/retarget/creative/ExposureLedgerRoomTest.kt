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
}
