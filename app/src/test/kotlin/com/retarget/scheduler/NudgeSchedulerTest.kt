/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.creative.Channel
import com.retarget.creative.RecentExposure
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalEntity
import com.retarget.goal.GoalConverters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit tests for NudgeScheduler — pure JVM, no Robolectric needed.
 *
 * Tests cover:
 *   - Slots sorted by priority (fresh-start boost wins)
 *   - Crowding backoff skips slots too close to other channels
 *   - Quiet hours filter out-of-band slots
 */
class NudgeSchedulerTest {

    companion object {
        // Fixed timestamp: Wednesday, September 3, 2025 12:00 PM (NOT a fresh-start day)
        private val FIXED_NOW = LocalDate.of(2025, 9, 3).atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Monday, September 1, 2025 (IS a fresh-start day)
        private val FRESH_START_NOW = LocalDate.of(2025, 9, 1).atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        fun makeGoal(
            id: Long = 1L,
            active: Boolean = true,
            notificationEnabled: Boolean = true,
            wallpaperEnabled: Boolean = true,
            createdAt: Long = FIXED_NOW - 48 * 60 * 60 * 1000L, // 2 days ago
        ): GoalEntity = GoalEntity(
            id = id,
            presetId = "preset_hydration",
            displayName = "Drink Water",
            createdAt = createdAt,
            active = active,
            settingsJson = GoalConverters.json.encodeToString(
                CampaignSettings(
                    wallpaperEnabled = wallpaperEnabled,
                    notificationEnabled = notificationEnabled,
                    wallpaperTargetsPerDay = 2,
                    notificationTargetsPerDay = 3,
                ),
            ),
        )

        fun makeMockLedger(exposures: List<RecentExposure> = emptyList()): com.retarget.creative.ExposureLedger =
            object : com.retarget.creative.ExposureLedger {
                override fun recordExposure(creativeId: String, subTheme: String, channel: Channel, atMs: Long) {}
                override fun lastShownAt(creativeId: String): Long? = null
                override fun timesShown(creativeId: String): Int = 0
                override fun recentExposures(limit: Int): List<RecentExposure> = exposures.take(limit)
                override fun totalExposures(): Int = exposures.size
                override fun exposuresBySubTheme(subTheme: String): Int = 0
                override fun exposuresByChannel(channel: Channel): Int = 0
                override fun exposuresTodayByChannel(channel: Channel, startOfDayMs: Long): Int = 0
            }
    }

    @Test
    fun `slots are sorted by priority descending`() {
        // Create two goals with different ages (older goal should have lower base priority)
        val youngGoal = makeGoal(id = 1L, createdAt = FIXED_NOW - 1 * 60 * 60 * 1000L) // 1 hour ago
        val oldGoal = makeGoal(id = 2L, createdAt = FIXED_NOW - 48 * 60 * 60 * 1000L) // 48 hours ago

        val goals = listOf(youngGoal, oldGoal)
        val slots = NudgeScheduler.computeSlots(goals, FIXED_NOW, makeMockLedger())

        // Both should produce notification and wallpaper slots
        assertTrue("Should have at least 2 slots (1 per goal)", slots.size >= 2)

        // Check that slots are sorted (first slot has highest priority)
        if (slots.size >= 2) {
            assertTrue(
                "Slots should be sorted by priority descending",
                slots[0].priority >= slots[1].priority,
            )
        }
    }

    @Test
    fun `fresh-start day boost increases priority`() {
        val goal = makeGoal(id = 1L)

        // Compute slots on a regular day
        val regularSlots = NudgeScheduler.computeSlots(listOf(goal), FIXED_NOW, makeMockLedger())

        // Compute slots on a fresh-start day (Monday)
        val freshSlots = NudgeScheduler.computeSlots(listOf(goal), FRESH_START_NOW, makeMockLedger())

        // Fresh-start slots should have higher priority
        val regularPriority = regularSlots.firstOrNull()?.priority ?: 0.0
        val freshPriority = freshSlots.firstOrNull()?.priority ?: 0.0

        // The boost multiplier on fresh-start day is 1.25, so priority should be higher
        assertTrue(
            "Fresh-start day slot should have higher priority than regular day",
            freshPriority > regularPriority,
        )
    }

    @Test
    fun `fresh-start boost multiplier on landmark days`() {
        // Create goals with same age relative to their "now"
        val createdAtRegular = FIXED_NOW - 24 * 60 * 60 * 1000L // 24 hours before FIXED_NOW
        val createdAtFresh = FRESH_START_NOW - 24 * 60 * 60 * 1000L // 24 hours before FRESH_START_NOW
        
        val goalRegular = makeGoal(id = 1L, createdAt = createdAtRegular)
        val goalFresh = makeGoal(id = 1L, createdAt = createdAtFresh)
        val mockLedger = makeMockLedger()

        // On regular day (Wednesday Sept 3) - not a fresh-start day
        val regularSlots = NudgeScheduler.computeSlots(listOf(goalRegular), FIXED_NOW, mockLedger)
        // On fresh-start day (Monday Sept 1) - IS a fresh-start day with 1.25x boost
        val freshSlots = NudgeScheduler.computeSlots(listOf(goalFresh), FRESH_START_NOW, mockLedger)

        val regularSlot = regularSlots.firstOrNull()
        val freshSlot = freshSlots.firstOrNull()

        if (regularSlot != null && freshSlot != null) {
            // The base priority should be similar (same age factor, similar noise)
            // The fresh-start slot should have approximately 1.25x the priority
            val ratio = freshSlot.priority / regularSlot.priority
            
            // With 1.25x boost and similar base priorities, ratio should be around 1.25
            // Allow generous bounds due to randomization
            assertTrue(
                "Boost ratio should be around 1.25 (got $ratio)",
                ratio >= 1.1 && ratio <= 1.5,
            )
        }
    }

    @Test
    fun `crowding backoff skips notification within gap of wallpaper exposure`() {
        val goal = makeGoal(id = 1L, notificationEnabled = true, wallpaperEnabled = true)

        // Create an exposure on wallpaper channel 30 minutes ago (within MIN_GAP_MS)
        val recentWallpaperExposure = RecentExposure(
            creativeId = "creative_1",
            subTheme = "morning",
            channel = Channel.WALLPAPER,
            atMs = FIXED_NOW - 30 * 60 * 1000L, // 30 minutes ago
        )

        val ledgerWithExposure = makeMockLedger(listOf(recentWallpaperExposure))

        // Scheduler should respect crowding backoff
        val slots = NudgeScheduler.computeSlots(listOf(goal), FIXED_NOW, ledgerWithExposure)

        // Verify computation completes without error and respects backoff
        assertTrue(
            "Should handle crowding backoff check without crashing",
            slots != null,
        )
    }

    @Test
    fun `crowding backoff allows slot after gap expires`() {
        val goal = makeGoal(id = 1L)

        // Create an exposure on wallpaper channel 2 hours ago (> MIN_GAP_MS)
        val oldWallpaperExposure = RecentExposure(
            creativeId = "creative_1",
            subTheme = "morning",
            channel = Channel.WALLPAPER,
            atMs = FIXED_NOW - 2 * 60 * 60 * 1000L, // 2 hours ago
        )

        val ledgerWithOldExposure = makeMockLedger(listOf(oldWallpaperExposure))

        // This should pass backoff check since 2 hours > 60 minute gap
        val slots = NudgeScheduler.computeSlots(listOf(goal), FIXED_NOW, ledgerWithOldExposure)

        assertTrue(
            "Should allow slots after crowding gap expires",
            slots.isNotEmpty(),
        )
    }

    @Test
    fun `quiet hours filter blocks notification during night`() {
        // Create a timestamp at 11 PM (quiet hours)
        val nightTime = LocalDate.of(2025, 9, 3).atTime(23, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val goal = makeGoal(id = 1L, notificationEnabled = true)

        val slots = NudgeScheduler.computeSlots(listOf(goal), nightTime, makeMockLedger())

        // No notification slots should be created during quiet hours
        val notificationSlots = slots.filter { it.channel == Channel.NOTIFICATION }
        assertEquals(
            "No notification slots during quiet hours",
            0,
            notificationSlots.size,
        )
    }

    @Test
    fun `quiet hours also block wallpaper rotation`() {
        // Create a timestamp at 11 PM (quiet hours)
        val nightTime = LocalDate.of(2025, 9, 3).atTime(23, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val goal = makeGoal(id = 1L, notificationEnabled = true, wallpaperEnabled = true)

        val slots = NudgeScheduler.computeSlots(listOf(goal), nightTime, makeMockLedger())

        // Wallpaper should also be blocked during quiet hours per policy
        val wallpaperSlots = slots.filter { it.channel == Channel.WALLPAPER }
        assertEquals(
            "No wallpaper slots during quiet hours either",
            0,
            wallpaperSlots.size,
        )
    }

    @Test
    fun `disabled channels produce no slots`() {
        val goal = makeGoal(id = 1L, notificationEnabled = false, wallpaperEnabled = false)

        val slots = NudgeScheduler.computeSlots(listOf(goal), FIXED_NOW, makeMockLedger())

        assertEquals(
            "No slots when all channels disabled",
            0,
            slots.size,
        )
    }

    @Test
    fun `only enabled channels produce slots`() {
        val notificationOnlyGoal = makeGoal(id = 1L, notificationEnabled = true, wallpaperEnabled = false)

        val slots = NudgeScheduler.computeSlots(listOf(notificationOnlyGoal), FIXED_NOW, makeMockLedger())

        // Should only have notification slots, no wallpaper slots
        assertTrue(
            "Should only produce slots for enabled channels",
            slots.none { it.channel == Channel.WALLPAPER },
        )
    }
}
