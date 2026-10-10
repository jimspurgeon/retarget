/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.retarget.creative.Channel
import com.retarget.creative.ExposureEntity
import com.retarget.creative.RoomExposureLedger
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M3.4 reward-signal tests (task 2).
 *
 * Simulated environments with in-memory Room; all data synthetic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RewardRecorderTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var recorder: RewardRecorder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        db =
            Room
                .inMemoryDatabaseBuilder(context, com.retarget.goal.GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        recorder = RewardRecorder(db.learningStateDao(), db.exposureDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------------
    // Check-in (+1.0) within credit window
    // ---------------------------------------------------------------------

    @Test
    fun `recordCheckIn credits +1 when an exposure exists within the 2h window`() =
        runTest {
            val now = 10_000_000L // arbitrary epoch ms
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            // Seed an exposure 1h ago on subTheme "focus"
            db.exposureDao().insert(
                ExposureEntity(
                    creativeId = "a1",
                    subTheme = "focus",
                    channel = Channel.NOTIFICATION,
                    atMs = now - 3_600_000L, // 1h ago
                ),
            )

            val applied = recorder.recordCheckIn(goalId = 1, nowMs = now)
            assertTrue(applied)

            val state = db.learningStateDao().get(1, bucket, "focus")
            assertEquals(1.0, state?.scoreSum ?: 0.0, 1e-9)
            assertEquals(1, state?.scoreCount ?: 0)
            assertEquals(0, state?.attempts ?: 0) // attempts are incremented at exposure time, not reward
        }

    @Test
    fun `recordCheckIn skips credit when no exposure in the window`() =
        runTest {
            val now = 10_000_000L
            // Exposure older than 2h
            db.exposureDao().insert(
                ExposureEntity(
                    creativeId = "a1",
                    subTheme = "focus",
                    channel = Channel.NOTIFICATION,
                    atMs = now - 4 * 3_600_000L, // 4h ago
                ),
            )

            val applied = recorder.recordCheckIn(goalId = 1, nowMs = now)
            assertFalse(applied)

            val state = db.learningStateDao().get(1, EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault()), "focus")
            assertNull(state)
        }

    @Test
    fun `recordCheckIn uses most recent exposure for attribution`() =
        runTest {
            val now = 10_000_000L
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            // Two exposures within window, most recent on "calm"
            db.exposureDao().insert(
                ExposureEntity(creativeId = "a1", subTheme = "focus", channel = Channel.NOTIFICATION, atMs = now - 2 * 3_600_000L),
            )
            db.exposureDao().insert(
                ExposureEntity(creativeId = "a2", subTheme = "calm", channel = Channel.NOTIFICATION, atMs = now - 30 * 60_000L), // 30 min ago
            )

            val applied = recorder.recordCheckIn(goalId = 1, nowMs = now)
            assertTrue(applied)

            val calmState = db.learningStateDao().get(1, bucket, "calm")
            assertEquals(1.0, calmState?.scoreSum ?: 0.0, 1e-9)
            val focusState = db.learningStateDao().get(1, bucket, "focus")
            assertTrue(focusState == null || focusState.scoreCount == 0)
        }

    // ---------------------------------------------------------------------
    // Fewer like this (strong negative -1.0)
    // ---------------------------------------------------------------------

    @Test
    fun `recordFewerLikeThis applies strong negative to creative's subTheme`() =
        runTest {
            val now = 10_000_000L
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            db.exposureDao().insert(
                ExposureEntity(creativeId = "creative-x", subTheme = "energetic", channel = Channel.WALLPAPER, atMs = now - 45 * 60_000L),
            )

            val applied = recorder.recordFewerLikeThis(goalId = 1, creativeId = "creative-x", nowMs = now)
            assertTrue(applied)

            val state = db.learningStateDao().get(1, bucket, "energetic")
            assertEquals(-1.0, state?.scoreSum ?: 0.0, 1e-9)
            assertEquals(1, state?.scoreCount ?: 0)
        }

    @Test
    fun `recordFewerLikeThis falls back to goal's most recent exposure if creative not found`() =
        runTest {
            val now = 10_000_000L
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            // Creative X was never shown, but goal has a recent exposure
            db.exposureDao().insert(
                ExposureEntity(creativeId = "${1}:ticker", subTheme = "ticker", channel = Channel.LOCK_SCREEN_TICKER, atMs = now - 30 * 60_000L),
            )

            val applied = recorder.recordFewerLikeThis(goalId = 1, creativeId = "never-seen", nowMs = now)
            assertTrue(applied)

            val state = db.learningStateDao().get(1, bucket, "ticker")
            assertEquals(-1.0, state?.scoreSum ?: 0.0, 1e-9)
        }

    // ---------------------------------------------------------------------
    // Snooze (mild negative -0.25)
    // ---------------------------------------------------------------------

    @Test
    fun `recordSnooze applies mild negative within the window`() =
        runTest {
            val now = 10_000_000L
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            db.exposureDao().insert(
                ExposureEntity(creativeId = "c1", subTheme = "mindful", channel = Channel.NOTIFICATION, atMs = now - 90 * 60_000L),
            )

            val applied = recorder.recordSnooze(goalId = 1, nowMs = now)
            assertTrue(applied)

            val state = db.learningStateDao().get(1, bucket, "mindful")
            assertEquals(-0.25, state?.scoreSum ?: 0.0, 1e-9)
            assertEquals(1, state?.scoreCount ?: 0)
        }

    // ---------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------

    @Test
    fun `recordReward with no exposure returns false and leaves state untouched`() =
        runTest {
            val applied = recorder.recordReward(goalId = 9, reward = 1.0, nowMs = 10_000_000L)
            assertFalse(applied)
            assertEquals(0, db.learningStateDao().getAllForExport().size)
        }

    @Test
    fun `credit accumulates across multiple signals`() =
        runTest {
            val now = 10_000_000L
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            db.exposureDao().insert(
                ExposureEntity(creativeId = "c1", subTheme = "stack", channel = Channel.NOTIFICATION, atMs = now - 30 * 60_000L),
            )

            recorder.recordCheckIn(1, now)
            recorder.recordSnooze(1, now) // different signal type

            val state = db.learningStateDao().get(1, bucket, "stack")
            assertEquals(0.75, state?.scoreSum ?: 0.0, 1e-9) // +1 - 0.25
            assertEquals(2, state?.scoreCount ?: 0)
        }
}
