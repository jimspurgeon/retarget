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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Gatekeeper B1 regression pin (2026-10-10): the production write path must
 * eventually reach MIN_OBSERVATIONS so the bandit can activate. Mirrors what
 * NotificationDeliveryWorker does at exposure time: upsert with
 * EpsilonGreedyBandit.incrementObservation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObservationPathTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var dao: LearningStateDao

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
        dao = db.learningStateDao()
    }

    @Test
    fun `exposure-path increments let the observation floor activate timing weight`() {
        val bucket = EpsilonGreedyBandit.bucketOf(0L, ZoneId.of("UTC"))

        // Simulate the delivery worker's exposure loop: one increment per nudge.
        repeat(EpsilonGreedyBandit.MIN_OBSERVATIONS) {
            val existing = dao.get(1L, bucket, "focus")
                ?: LearningStateEntity(goalId = 1L, bucket = bucket, subTheme = "focus")
            dao.upsert(EpsilonGreedyBandit.incrementObservation(existing))
        }

        val states = dao.getByGoalAndBucket(1L, bucket)
        assertEquals(EpsilonGreedyBandit.MIN_OBSERVATIONS, EpsilonGreedyBandit.bucketAttempts(states))

        // With rewards mixed in, the timing weight must now depart from neutral
        // (below the floor it would be exactly 1.0).
        dao.upsert(
            EpsilonGreedyBandit.applyReward(
                dao.get(1L, bucket, "focus"),
                1L, bucket, "focus",
                EpsilonGreedyBandit.REWARD_CHECK_IN,
            ),
        )
        val weight = EpsilonGreedyBandit.timingWeight(dao.getByGoalAndBucket(1L, bucket), bucket)
        assertTrue("weight=$weight should exceed 1.0 after floor + positive reward", weight > 1.0)
    }

    @Test
    fun `applyReward alone never inflates attempts`() {
        val bucket = 0
        dao.upsert(
            EpsilonGreedyBandit.applyReward(null, 1L, bucket, "focus", 1.0),
        )
        assertEquals(0, dao.getByGoalAndBucket(1L, bucket).sumOf { it.attempts })
    }
}
