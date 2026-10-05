/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.retarget.goal.GoalDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration test: pre-bundled packs (fresh-air, fruit) load from real APK
 * assets into Room via [PersistentCreativeRepository.ensureLoaded], and are
 * queryable through the pack/theme/candidate methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistentCreativeRepositoryTest {
    private lateinit var db: GoalDatabase
    private lateinit var repo: PersistentCreativeRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build(),
        )
        db =
            Room
                .inMemoryDatabaseBuilder(context, GoalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = PersistentCreativeRepository(context, db.creativePackDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `bundled fresh-air and fruit packs ingest from assets`() =
        runTest {
            repo.ensureLoaded(nowMs = 1_000L)

            val packs = repo.loadAllPacks().first()
            assertTrue("Expected at least 2 ingested packs, got ${packs.map { it.id }}", packs.size >= 2)
            val ids = packs.map { it.id }
            assertTrue(ids.contains("fresh-air"))
            assertTrue(ids.contains("fruit"))

            val freshAir = packs.first { it.id == "fresh-air" }
            assertEquals(GoalTheme.NATURE_TIME.name, freshAir.goalTheme)
            assertEquals(61, freshAir.imageCount)

            val fruit = packs.first { it.id == "fruit" }
            assertEquals(GoalTheme.PLANT_BASED_WHOLE_FOODS.name, fruit.goalTheme)
            assertEquals(60, fruit.imageCount)
        }

    @Test
    fun `ensureLoaded is idempotent - second call adds nothing`() =
        runTest {
            repo.ensureLoaded(nowMs = 1_000L)
            val first = repo.loadAllPacks().first()
            val firstCount = repo.getCreativesForPack("fresh-air").size

            repo.ensureLoaded(nowMs = 2_000L)
            val second = repo.loadAllPacks().first()
            val secondCount = repo.getCreativesForPack("fresh-air").size

            assertEquals(first.size, second.size)
            assertEquals(firstCount, secondCount)
        }

    @Test
    fun `getPacksByGoalTheme filters to NATURE_TIME`() =
        runTest {
            repo.ensureLoaded(nowMs = 0)
            val packs = repo.getPacksByGoalTheme(GoalTheme.NATURE_TIME).first()
            assertEquals(listOf("fresh-air"), packs.map { it.id })
        }

    @Test
    fun `getCandidatesForActiveGoals returns themed creatives`() =
        runTest {
            repo.ensureLoaded(nowMs = 0)

            val natureCreatives = repo.getCandidatesForActiveGoals(listOf(GoalTheme.NATURE_TIME))
            assertEquals(61, natureCreatives.size)
            assertTrue(natureCreatives.all { it.goalTheme == GoalTheme.NATURE_TIME })
            assertTrue(natureCreatives.all { it.imagePath.endsWith(".jpg") })

            val plantBased = repo.getCandidatesForActiveGoals(listOf(GoalTheme.PLANT_BASED_WHOLE_FOODS))
            assertEquals(60, plantBased.size)
            assertTrue(plantBased.all { it.goalTheme == GoalTheme.PLANT_BASED_WHOLE_FOODS })
        }

    @Test
    fun `ingested creatives carry license metadata`() =
        runTest {
            repo.ensureLoaded(nowMs = 0)
            val creatives = repo.getCreativesForPack("fruit")
            assertTrue(creatives.isNotEmpty())
            assertTrue(creatives.all { it.licenseUrl.isNotBlank() })
            assertTrue(creatives.all { it.attribution != null })
        }
}
