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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningExporterTest {
    private lateinit var db: com.retarget.goal.GoalDatabase
    private lateinit var exporter: LearningExporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Same WorkManager test init as LearningStateDaoTest (Robolectric boots
        // the real Application class which initializes WorkManager).
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        db =
            Room
                .inMemoryDatabaseBuilder(context, com.retarget.goal.GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        exporter = LearningExporter(db.learningStateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `exportData returns an ordered list of LearningRows`() {
        db.learningStateDao().upsertAll(
            listOf(
                LearningStateEntity(2, 1, "chill", 5, 2.0, 2),
                LearningStateEntity(1, 0, "focus", 10, 3.0, 3),
                LearningStateEntity(1, 0, "active", 8, 1.5, 2),
            )
        )

        val rows = exporter.exportData()

        assertEquals(3, rows.size)
        // Deterministic order: goalId asc, bucket asc, subTheme asc
        assertEquals(1L, rows[0].goalId)
        assertEquals("active", rows[0].subTheme)
        assertEquals(1L, rows[1].goalId)
        assertEquals("focus", rows[1].subTheme)
        assertEquals(2L, rows[2].goalId)
    }

    @Test
    fun `resetLearning clears only the specified goal`() {
        db.learningStateDao().upsertAll(
            listOf(
                LearningStateEntity(1, 0, "focus", 10, 3.0, 3),
                LearningStateEntity(2, 0, "chill", 5, 2.0, 2),
            )
        )

        val cleared = exporter.resetLearning(1)

        assertEquals(1, cleared)
        val remaining = exporter.exportData()
        assertEquals(1, remaining.size)
        assertEquals(2L, remaining[0].goalId)
    }

    @Test
    fun `wipeAllLearning clears every row`() {
        db.learningStateDao().upsertAll(
            listOf(
                LearningStateEntity(1, 0, "focus", 10, 3.0, 3),
                LearningStateEntity(2, 0, "chill", 5, 2.0, 2),
            )
        )

        val cleared = exporter.wipeAllLearning()

        assertEquals(2, cleared)
        assertTrue(exporter.exportData().isEmpty())
    }
}
