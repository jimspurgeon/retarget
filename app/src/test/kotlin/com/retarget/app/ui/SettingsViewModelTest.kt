/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retarget.goal.GoalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [SettingsViewModel] ticker toggle wiring (M3.3 UI):
 * the settings toggle reflects and mutates CampaignSettings.tickerEnabled,
 * and installPreset's tickerEnabled param wires the onboarding opt-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var repo: com.retarget.goal.GoalRepository
    private lateinit var viewModel: SettingsViewModel

    private val testMainDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main; route it to an eager test
        // dispatcher so toggleChannel's launch completes inside runBlocking.
        Dispatchers.setMain(testMainDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, com.retarget.goal.GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = com.retarget.goal.GoalRepository(db.goalDao())
        viewModel = SettingsViewModel(repo)

        runBlocking {
            db.goalDao().insert(
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
                            "tickerEnabled": false,
                            "wallpaperTargetsPerDay": 3,
                            "notificationTargetsPerDay": 2,
                            "tickerTargetsPerDay": 2
                        }
                        """.trimIndent(),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState includes Ticker channel setting`() = runBlocking {
        // uiState starts at the empty default; wait for the populated emission.
        val state = viewModel.uiState.first { it.channelSettings.isNotEmpty() }

        val ticker = state.channelSettings.first { it.channelName == "Ticker" }
        assertFalse(ticker.enabled)
        assertEquals(2, ticker.targetsPerDay)
    }

    @Test
    fun `toggleChannel Ticker flips tickerEnabled in persisted settings`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            viewModel.toggleChannel("Ticker", context)
            // Wait until the reflected UI state shows the flip, then check persistence.
            viewModel.uiState.first { s -> s.channelSettings.first { it.channelName == "Ticker" }.enabled }
            assertTrue(repo.observeActive().first().single().settings.tickerEnabled)

            viewModel.toggleChannel("Ticker", context)
            viewModel.uiState.first { s -> !s.channelSettings.first { it.channelName == "Ticker" }.enabled }
            assertFalse(repo.observeActive().first().single().settings.tickerEnabled)
        }

    @Test
    fun `installPreset without opt-in leaves ticker disabled`() = runBlocking {
        repo.installPreset("fresh-air", nowMs = 1000L)
        val goal = repo.observeActive().first().single { it.presetId == "fresh-air" }
        assertFalse(goal.settings.tickerEnabled)
    }

    @Test
    fun `installPreset with ticker opt-in enables ticker for new goal`() = runBlocking {
        repo.installPreset("fresh-air", nowMs = 1000L, tickerEnabled = true)
        val goal = repo.observeActive().first().single { it.presetId == "fresh-air" }
        assertTrue(goal.settings.tickerEnabled)
    }

    @Test
    fun `reactivating existing goal preserves its saved ticker setting`() = runBlocking {
        repo.setActive(1L, false)
        repo.installPreset("hydration", nowMs = 2000L, tickerEnabled = true)

        val goal = repo.observeActive().first().single { it.presetId == "hydration" }
        // Saved setting (false) wins over the onboarding opt-in on reactivation
        assertFalse(goal.settings.tickerEnabled)
    }
}
