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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Transactional check-in + reward tests (M3.4, task 2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInWithRewardTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var checkInWithReward: CheckInWithReward

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
        checkInWithReward =
            CheckInWithReward(
                db,
                RewardRecorder(db.learningStateDao(), db.exposureDao()),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `checkIn inserts a row and credits learning state`() =
        runTest {
            val now = 10_000_000L
            val bucket = EpsilonGreedyBandit.bucketOf(now, java.time.ZoneId.systemDefault())
            // Seed exposure within 2h window
            db.exposureDao().insert(
                ExposureEntity(creativeId = "a1", subTheme = "focus", channel = Channel.NOTIFICATION, atMs = now - 60 * 60_000L),
            )

            val id = checkInWithReward.checkIn(goalId = 7, nowMs = now)

            assertTrue(id > 0)
            val checkIn = db.checkInDao().getAllCheckInsForExport().firstOrNull { it.goalId == 7L }
            assertNotNull(checkIn)
            assertEquals(now, checkIn?.atMs ?: 0L)

            val state = db.learningStateDao().get(7, bucket, "focus")
            assertEquals(1.0, state?.scoreSum ?: 0.0, 1e-9)
            assertEquals(1, state?.scoreCount ?: 0)
        }

    @Test
    fun `checkIn inserts a row but skips credit when no exposure in window`() =
        runTest {
            val now = 10_000_000L
            // Old exposure outside window
            db.exposureDao().insert(
                ExposureEntity(creativeId = "a1", subTheme = "focus", channel = Channel.NOTIFICATION, atMs = now - 5 * 3_600_000L),
            )

            val id = checkInWithReward.checkIn(goalId = 9, nowMs = now)

            assertTrue(id > 0)
            val checkIn = db.checkInDao().getAllCheckInsForExport().firstOrNull { it.goalId == 9L }
            assertNotNull(checkIn)

            val state = db.learningStateDao().get(9, bucket = 0, subTheme = "focus")
            // No learning cell created — no credit earned
            assertTrue(state == null || state.scoreCount == 0)
        }
}
