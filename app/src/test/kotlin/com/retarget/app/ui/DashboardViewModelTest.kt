/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retarget.creative.Channel
import com.retarget.creative.RoomExposureLedger
import com.retarget.goal.GoalEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Tests for [DashboardViewModel] focusing on per-channel pacing calculations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var ledger: RoomExposureLedger
    private lateinit var viewModel: DashboardViewModel

    private val testZoneId = ZoneOffset.UTC // Use UTC for predictable testing

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, com.retarget.goal.GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        ledger = RoomExposureLedger(db.exposureDao())

        // Insert test goals
        val goalDao = db.goalDao()
        runBlocking {
            goalDao.insert(
                GoalEntity(
                    presetId = "hydration",
                    displayName = "Hydration Goal",
                    createdAt = 0L,
                    active = true,
                    settingsJson =
                        """
                        {
                            "wallpaperEnabled": true,
                            "notificationEnabled": true,
                            "wallpaperTargetsPerDay": 3,
                            "notificationTargetsPerDay": 2
                        }
                        """.trimIndent(),
                ),
            )
            goalDao.insert(
                GoalEntity(
                    presetId = "fresh-air",
                    displayName = "Fresh Air Goal",
                    createdAt = 1000L,
                    active = true,
                    settingsJson =
                        """
                        {
                            "wallpaperEnabled": true,
                            "notificationEnabled": false,
                            "wallpaperTargetsPerDay": 2,
                            "notificationTargetsPerDay": 1
                        }
                        """.trimIndent(),
                ),
            )
        }

        viewModel = DashboardViewModel(db, ledger, testZoneId)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `activeGoals emits correct goal pacing states`() = runBlocking {
        val goals = viewModel.activeGoals.first()

        assertEquals(2, goals.size)
        val hydrationGoal = goals.find { it.presetId == "hydration" }!!
        val freshAirGoal = goals.find { it.presetId == "fresh-air" }!!

        // Hydration goal settings
        assertTrue(hydrationGoal.wallpaperEnabled)
        assertTrue(hydrationGoal.notificationEnabled)
        assertEquals(3, hydrationGoal.wallpaperTargetPerDay)
        assertEquals(2, hydrationGoal.notificationTargetPerDay)

        // Fresh air goal settings
        assertTrue(freshAirGoal.wallpaperEnabled)
        assertFalse(freshAirGoal.notificationEnabled)
        assertEquals(2, freshAirGoal.wallpaperTargetPerDay)
        assertEquals(1, freshAirGoal.notificationTargetPerDay)
    }

    @Test
    fun `wallpaperPacingSummary calculates aggregate counts correctly`() = runBlocking {
        // Record some wallpaper exposures
        val startOfDayMs = LocalDate.now(testZoneId).atStartOfDay(testZoneId).toInstant().toEpochMilli()
        ledger.recordExposure("hydration/c1", "default", Channel.WALLPAPER, startOfDayMs + 1000L)
        ledger.recordExposure("hydration/c2", "default", Channel.WALLPAPER, startOfDayMs + 2000L)
        ledger.recordExposure("fresh-air/c1", "default", Channel.WALLPAPER, startOfDayMs + 3000L)

        val summary = viewModel.wallpaperPacingSummary.first()

        // Total wallpaper exposures: 3 (2 hydration + 1 fresh-air)
        assertEquals(3, summary.countToday)
        // Aggregate target: 5 (3 hydration + 2 fresh-air)
        assertEquals(5, summary.targetPerDay)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun `notificationPacingSummary respects notificationEnabled flag in target calculation`() = runBlocking {
        // Record some notification exposures (even though fresh-air has notifications disabled)
        val startOfDayMs = LocalDate.now(testZoneId).atStartOfDay(testZoneId).toInstant().toEpochMilli()
        ledger.recordExposure("hydration/c1", "default", Channel.NOTIFICATION, startOfDayMs + 1000L)
        ledger.recordExposure("hydration/c2", "default", Channel.NOTIFICATION, startOfDayMs + 2000L)
        ledger.recordExposure("fresh-air/c1", "default", Channel.NOTIFICATION, startOfDayMs + 3000L)

        val summary = viewModel.notificationPacingSummary.first()

        // Total notification exposures: 3
        assertEquals(3, summary.countToday)
        // Aggregate target: 3 (2 hydration + 1 fresh-air) - based on settings, not enabled flag
        assertEquals(3, summary.targetPerDay)
        assertFalse(summary.isOverBudget)
    }

    @Test
    fun `notificationPacingSummary shows over budget when count exceeds target`() = runBlocking {
        val startOfDayMs = LocalDate.now(testZoneId).atStartOfDay(testZoneId).toInstant().toEpochMilli()

        // Record more notification exposures than the target (3 > 2 for hydration + 1 for fresh-air = 4 total vs 3 target)
        ledger.recordExposure("hydration/c1", "default", Channel.NOTIFICATION, startOfDayMs + 1000L)
        ledger.recordExposure("hydration/c2", "default", Channel.NOTIFICATION, startOfDayMs + 2000L)
        ledger.recordExposure("hydration/c3", "default", Channel.NOTIFICATION, startOfDayMs + 3000L)
        ledger.recordExposure("fresh-air/c1", "default", Channel.NOTIFICATION, startOfDayMs + 4000L)

        val summary = viewModel.notificationPacingSummary.first()

        assertEquals(4, summary.countToday)
        assertEquals(3, summary.targetPerDay)
        assertTrue(summary.isOverBudget)
    }

    @Test
    fun `pacingSummary uses startOfDayMs to filter today's exposures only`() = runBlocking {
        val yesterday = LocalDate.now(testZoneId).minusDays(1).atStartOfDay(testZoneId).toInstant().toEpochMilli()
        val today = LocalDate.now(testZoneId).atStartOfDay(testZoneId).toInstant().toEpochMilli()

        // Record exposures yesterday
        ledger.recordExposure("hydration/c1", "default", Channel.WALLPAPER, yesterday + 1000L)
        ledger.recordExposure("hydration/c2", "default", Channel.WALLPAPER, yesterday + 2000L)

        // Record exposures today
        ledger.recordExposure("hydration/c3", "default", Channel.WALLPAPER, today + 1000L)

        val summary = viewModel.wallpaperPacingSummary.first()

        // Only today's exposure should count
        assertEquals(1, summary.countToday)
    }

    @Test
    fun `empty goals list returns zero pacing`() = runBlocking {
        // Deactivate all goals
        db.goalDao().setActive(1L, false)
        db.goalDao().setActive(2L, false)

        val wallpaperSummary = viewModel.wallpaperPacingSummary.first()
        val notificationSummary = viewModel.notificationPacingSummary.first()

        assertEquals(0, wallpaperSummary.countToday)
        assertEquals(0, wallpaperSummary.targetPerDay)
        assertEquals(0, notificationSummary.countToday)
        assertEquals(0, notificationSummary.targetPerDay)
    }
}
