/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retarget.goal.GoalDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for [CheckInDao] and [CheckInEntity].
 *
 * Verifies:
 * - Check-in insertion works correctly
 * - countByGoal returns correct counts within a day window
 * - totalCountByGoal returns cumulative count
 * - Day boundary handling (counts reset at midnight)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckInDaoTest {

    private lateinit var db: GoalDatabase
    private lateinit var checkInDao: CheckInDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GoalDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        checkInDao = db.checkInDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_checkIn_returnsPositiveId() = runBlocking {
        val entity = CheckInEntity(
            goalId = 1L,
            atMs = System.currentTimeMillis(),
            notes = "Morning check-in",
        )

        val id = checkInDao.insert(entity)

        assertTrue("Inserted ID should be positive", id > 0)
    }

    @Test
    fun countByGoal_returnsZeroWhenNoCheckIns() {
        val startOfDayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val count = checkInDao.countByGoal(1L, startOfDayMs)

        assertEquals("Should have zero check-ins when none exist", 0, count)
    }

    @Test
    fun countByGoal_incrementsAfterInsert() = runBlocking {
        val startOfDayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()

        // Insert two check-ins for goal 1
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = nowMs, notes = null))
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = nowMs, notes = null))

        val count = checkInDao.countByGoal(1L, startOfDayMs)

        assertEquals("Should count both check-ins", 2, count)
    }

    @Test
    fun countByGoal_separatesByGoal() = runBlocking {
        val nowMs = System.currentTimeMillis()

        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = nowMs, notes = null))
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = nowMs, notes = null))
        checkInDao.insert(CheckInEntity(goalId = 2L, atMs = nowMs, notes = null))

        val countGoal1 = checkInDao.countByGoal(1L, nowMs)
        val countGoal2 = checkInDao.countByGoal(2L, nowMs)

        assertEquals("Goal 1 should have 2 check-ins", 2, countGoal1)
        assertEquals("Goal 2 should have 1 check-in", 1, countGoal2)
    }

    @Test
    fun countByGoal_excludesPreviousDay() = runBlocking {
        val todayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yesterdayMs = todayMs - 86400000L // 24 hours ago

        // Insert check-in yesterday
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = yesterdayMs, notes = null))
        // Insert check-in today
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = todayMs, notes = null))

        val count = checkInDao.countByGoal(1L, todayMs)

        assertEquals("Should only count today's check-in", 1, count)
    }

    @Test
    fun totalCountByGoal_returnsCumulativeCount() = runBlocking {
        val yesterdayMs = System.currentTimeMillis() - 86400000L
        val todayMs = System.currentTimeMillis()

        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = yesterdayMs, notes = null))
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = todayMs, notes = null))
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = todayMs, notes = null))

        val total = checkInDao.totalCountByGoal(1L)

        assertEquals("Total count should include all days", 3, total)
    }

    @Test
    fun countsByGoalToday_groupsCorrectly() = runBlocking {
        val startOfDayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = System.currentTimeMillis(), notes = null))
        checkInDao.insert(CheckInEntity(goalId = 1L, atMs = System.currentTimeMillis(), notes = null))
        checkInDao.insert(CheckInEntity(goalId = 2L, atMs = System.currentTimeMillis(), notes = null))

        val counts = checkInDao.countsByGoalToday(startOfDayMs)

        val countsList = counts.first()
        assertEquals("Should have counts for 2 goals", 2, countsList.size)

        val goal1Count = countsList.find { it.goalId == 1L }?.cnt ?: 0
        val goal2Count = countsList.find { it.goalId == 2L }?.cnt ?: 0

        assertEquals("Goal 1 should have 2 check-ins", 2, goal1Count)
        assertEquals("Goal 2 should have 1 check-in", 1, goal2Count)
    }

    @Test
    fun checkInEntity_notesAreOptional() = runBlocking {
        val startOfDayMs = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entityWithNotes = CheckInEntity(goalId = 1L, atMs = System.currentTimeMillis(), notes = "Test note")
        val entityWithoutNotes = CheckInEntity(goalId = 1L, atMs = System.currentTimeMillis(), notes = null)

        checkInDao.insert(entityWithNotes)
        checkInDao.insert(entityWithoutNotes)

        val count = checkInDao.countByGoal(1L, startOfDayMs)

        assertEquals("Both check-ins should be counted regardless of notes", 2, count)
    }
}
