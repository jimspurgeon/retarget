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
import kotlinx.coroutines.test.runTest
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
 * DAO behavior on an in-memory Room instance (JVM via Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningStateDaoTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var dao: LearningStateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Same WorkManager test init as GoalRepositoryTest: Robolectric boots the
        // real Application class, which initializes WorkManager on background
        // threads; without this the uncaught exception pollutes runTest.
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

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert inserts then updates by composite key`() {
        dao.upsert(LearningStateEntity(goalId = 1, bucket = 2, subTheme = "focus", attempts = 3))
        dao.upsert(
            LearningStateEntity(goalId = 1, bucket = 2, subTheme = "focus", attempts = 3, scoreSum = 1.0, scoreCount = 1),
        )

        val got = dao.get(1, 2, "focus")
        assertEquals(1.0, got!!.scoreSum, 0.0)
        assertEquals(1, got.scoreCount)
        assertEquals(3, got.attempts)

        // Different subTheme = different row
        dao.upsert(LearningStateEntity(goalId = 1, bucket = 2, subTheme = "calm"))
        assertEquals(2, dao.getByGoalAndBucket(1, 2).size)
    }

    @Test
    fun `getByGoal scopes rows to one goal`() =
        runTest {
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 0, subTheme = "a"))
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 1, subTheme = "a"))
            dao.upsert(LearningStateEntity(goalId = 2, bucket = 0, subTheme = "a"))

            assertEquals(2, dao.getByGoal(1).size)
            assertEquals(1, dao.getByGoal(2).size)
        }

    @Test
    fun `clearForGoal removes only that goal's learning`() =
        runTest {
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 0, subTheme = "a"))
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 1, subTheme = "b"))
            dao.upsert(LearningStateEntity(goalId = 2, bucket = 0, subTheme = "a"))

            dao.clearForGoal(1)

            assertTrue(dao.getByGoal(1).isEmpty())
            assertEquals(1, dao.getByGoal(2).size)
        }

    @Test
    fun `clear wipes everything`() =
        runTest {
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 0, subTheme = "a"))
            dao.upsert(LearningStateEntity(goalId = 2, bucket = 1, subTheme = "b"))

            dao.clear()

            assertTrue(dao.getAllForExport().isEmpty())
        }

    @Test
    fun `getAllForExport returns deterministic order`() =
        runTest {
            dao.upsert(LearningStateEntity(goalId = 2, bucket = 0, subTheme = "z"))
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 5, subTheme = "a"))
            dao.upsert(LearningStateEntity(goalId = 1, bucket = 1, subTheme = "m"))

            val all = dao.getAllForExport()
            assertEquals(listOf(1L to 1, 1L to 5, 2L to 0), all.map { it.goalId to it.bucket })
        }

    @Test
    fun `get returns null for unknown cell`() {
        assertNull(dao.get(99, 3, "nothing"))
    }
}
