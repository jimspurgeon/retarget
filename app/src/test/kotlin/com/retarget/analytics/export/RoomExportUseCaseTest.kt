/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics.export

import com.retarget.analytics.CheckInDao
import com.retarget.analytics.CheckInEntity
import com.retarget.creative.Channel
import com.retarget.creative.ExposureDao
import com.retarget.creative.ExposureEntity
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalDao
import com.retarget.goal.GoalEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM tests for the Room-backed [ExportUseCase] wiring, using fakes for the
 * DAOs (they are plain interfaces). All data is synthetic (AGENTS.md §1).
 */
class RoomExportUseCaseTest {

    private class FakeGoalDao(private val goals: List<GoalEntity>) : GoalDao {
        override suspend fun insert(goal: GoalEntity): Long = 0

        override fun observeActive(): kotlinx.coroutines.flow.Flow<List<GoalEntity>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override fun getAllActiveGoals(): List<GoalEntity> = goals.filter { it.active }

        override suspend fun byPresetId(presetId: String): GoalEntity? = goals.firstOrNull { it.presetId == presetId }

        override suspend fun getAllGoalsForExport(): List<GoalEntity> = goals

        override suspend fun getById(id: Long): GoalEntity? = goals.firstOrNull { it.id == id }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) {
        }

        override suspend fun activeCount(): Int = goals.count { it.active }

        override suspend fun updateSettings(
            id: Long,
            settingsJson: String,
        ) {
        }
    }

    private class FakeExposureDao(private val exposures: List<ExposureEntity>) : ExposureDao {
        override fun insert(entity: ExposureEntity) {}

        override fun lastShownAt(creativeId: String): Long? = null

        override fun timesShown(creativeId: String): Int = 0

        override fun recent(limit: Int): List<ExposureEntity> = exposures.take(limit)

        override fun totalExposures(): Int = exposures.size

        override suspend fun getAllExposuresForExport(): List<ExposureEntity> = exposures

        override fun exposuresBySubTheme(subTheme: String): Int = 0

        override fun clear() {}

        override fun exposuresByChannel(channel: Channel): Int = 0

        override fun exposuresTodayByChannel(channel: Channel, startOfDayMs: Long): Int = 0
    }

    private class FakeCheckInDao(private val checkIns: List<CheckInEntity>) : CheckInDao {
        override suspend fun insert(entity: CheckInEntity): Long = 0

        override fun countByGoal(goalId: Long, startOfDayMs: Long): Int = 0

        override fun totalCountByGoal(goalId: Long): Int = 0

        override fun countsByGoalToday(startOfDayMs: Long): kotlinx.coroutines.flow.Flow<List<com.retarget.analytics.CheckInCount>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun getAllCheckInsForExport(): List<CheckInEntity> = checkIns
    }

    private fun goal(
        id: Long,
        presetId: String,
        active: Boolean = true,
    ): GoalEntity =
        GoalEntity(
            id = id,
            presetId = presetId,
            displayName = "goal-$id",
            createdAt = 1_700_000_000_000L + id,
            active = active,
            settingsJson =
                Json.encodeToString(
                    CampaignSettings(wallpaperEnabled = true, notificationEnabled = false, wallpaperTargetsPerDay = 2, notificationTargetsPerDay = 0),
                ),
        )

    @Test
    fun `export includes inactive goals`() = runTest {
        // An export is a backup: archived goals must not vanish from it.
        val useCase =
            RoomExportUseCase(
                FakeGoalDao(listOf(goal(1, "a.active"), goal(2, "b.archived", active = false))),
                FakeExposureDao(emptyList()),
                FakeCheckInDao(emptyList()),
            )

        val json = useCase.buildExport()
        val doc = Json.parseToJsonElement(json).jsonObject

        assertEquals(2, doc["goals"]!!.jsonArray.size)
    }

    @Test
    fun `export carries rows from all three tables`() = runTest {
        val useCase =
            RoomExportUseCase(
                FakeGoalDao(listOf(goal(1, "a.active"))),
                FakeExposureDao(
                    listOf(
                        ExposureEntity(creativeId = "w-1", subTheme = "water", channel = Channel.WALLPAPER, atMs = 42L),
                        ExposureEntity(creativeId = "w-2", subTheme = "water", channel = Channel.NOTIFICATION, atMs = 43L),
                    ),
                ),
                FakeCheckInDao(listOf(CheckInEntity(goalId = 1, atMs = 44L, notes = "felt good"))),
            )

        val doc = Json.parseToJsonElement(useCase.buildExport()).jsonObject

        assertEquals(2, doc["exposureEvents"]!!.jsonArray.size)
        assertEquals(1, doc["checkIns"]!!.jsonArray.size)
        assertEquals(1, doc["goals"]!!.jsonArray.size)
    }

    @Test
    fun `export is deterministic across calls`() = runTest {
        val useCase =
            RoomExportUseCase(
                FakeGoalDao(listOf(goal(1, "a.active"), goal(2, "b.other"))),
                FakeExposureDao(listOf(ExposureEntity(creativeId = "w-1", subTheme = "water", channel = Channel.WALLPAPER, atMs = 42L))),
                FakeCheckInDao(listOf(CheckInEntity(goalId = 1, atMs = 44L, notes = null))),
            )

        assertEquals(useCase.buildExport(), useCase.buildExport())
    }

    @Test
    fun `schema version is stamped`() = runTest {
        val useCase =
            RoomExportUseCase(FakeGoalDao(emptyList()), FakeExposureDao(emptyList()), FakeCheckInDao(emptyList()))

        val doc = Json.parseToJsonElement(useCase.buildExport()).jsonObject

        assertEquals(
            ExportSerializer.CURRENT_SCHEMA_VERSION,
            doc["schemaVersion"]!!.jsonPrimitive.int,
        )
    }
}
