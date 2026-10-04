/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO + repository behavior on an in-memory Room instance (JVM via Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalRepositoryTest {
    private lateinit var db: GoalDatabase
    private lateinit var repo: GoalRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = GoalRepository(db.goalDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `installPreset creates an active goal with preset defaults`() =
        runTest {
            val id = repo.installPreset("hydration", nowMs = 1_000L)

            val goals = repo.observeActive().first()
            assertEquals(1, goals.size)
            assertEquals("hydration", goals[0].presetId)
            assertEquals("Hydration", goals[0].displayName)
            assertTrue(goals[0].settings.wallpaperEnabled)
            assertFalse(goals[0].settings.notificationEnabled)
            assertEquals(4, goals[0].settings.wallpaperTargetsPerDay)
        }

    @Test
    fun `installPreset rejects unknown presets`() =
        runTest {
            var threw = false
            try {
                repo.installPreset("not-a-preset", nowMs = 0)
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
        }

    @Test
    fun `installPreset is idempotent - reactivates instead of duplicating`() =
        runTest {
            val first = repo.installPreset("fruit", nowMs = 1)
            repo.setActive(first, false)
            assertTrue(repo.observeActive().first().isEmpty())

            val second = repo.installPreset("fruit", nowMs = 2)
            assertEquals(first, second) // same row, reactivated
            assertEquals(1, repo.observeActive().first().size)
        }

    @Test
    fun `deactivation hides goal from active queries`() =
        runTest {
            val id = repo.installPreset("fresh-air", nowMs = 0)
            repo.setActive(id, false)
            assertTrue(repo.observeActive().first().isEmpty())
            assertFalse(repo.hasAnyActiveGoal())
        }

    @Test
    fun `settings JSON roundtrip preserves values`() =
        runTest {
            repo.installPreset("vegetables", nowMs = 0)
            val goal = db.goalDao().byPresetId("vegetables")
            assertNotNull(goal)
            val settings = goal!!.settings
            assertEquals(1, settings.notificationTargetsPerDay)
            assertEquals(3, settings.wallpaperTargetsPerDay)
        }

    @Test
    fun `campaign settings enforce BudgetPolicy caps`() {
        var threw = false
        try {
            CampaignSettings(wallpaperTargetsPerDay = 99, notificationTargetsPerDay = 0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `updateSettings persists channel toggles`() =
        runTest {
            val id = repo.installPreset("hydration", nowMs = 0)
            val goal = db.goalDao().byPresetId("hydration")
            assertNotNull(goal)

            val initialSettings = goal!!.settings
            assertTrue(initialSettings.wallpaperEnabled)

            val updatedSettings = initialSettings.copy(wallpaperEnabled = false)
            repo.updateSettings(id, updatedSettings)

            val refreshedGoal = db.goalDao().byPresetId("hydration")
            assertFalse(refreshedGoal!!.settings.wallpaperEnabled)
        }
}
