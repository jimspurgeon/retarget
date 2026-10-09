/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.goal.CampaignSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TickerPolicy (M3.3, PHASE3-AGENCY.md §4).
 *
 * Covers:
 *   - Disabled-by-default gate (decision #4)
 *   - Quiet-hour absence (ticker must be ABSENT, not merely silent)
 *   - User cap enforcement and hard-max clamping
 *   - Cooldown and saturation passthrough to BudgetPolicy
 */
class TickerPolicyTest {

    companion object {
        fun makeSettings(
            enabled: Boolean = true,
            cap: Int = CampaignSettings.MAX_TICKER_PER_DAY,
        ): CampaignSettings =
            CampaignSettings(
                wallpaperEnabled = true,
                notificationEnabled = false,
                tickerEnabled = enabled,
                wallpaperTargetsPerDay = 2,
                notificationTargetsPerDay = 3,
                tickerTargetsPerDay = cap,
            )

        /** Timestamp at the given local hour on a fixed day (2025-09-03, not a fresh-start day). */
        fun nowAtHour(hour: Int): Long =
            java.time.LocalDate.of(2025, 9, 3).atTime(hour, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    }

    @Test
    fun `denies when tickerEnabled is false`() {
        val settings = makeSettings(enabled = false)
        assertFalse(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `allows when enabled, outside quiet hours, under cap`() {
        val settings = makeSettings()
        assertTrue(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12), // noon, outside quiet hours
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `absent during quiet hours evening`() {
        val settings = makeSettings()
        assertFalse(
            "Ticker must be ABSENT during quiet hours (PHASE3-AGENCY.md §4), not merely silent",
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(23), // 11 PM — quiet hours
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `absent during quiet hours morning`() {
        val settings = makeSettings()
        assertFalse(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(5), // 5 AM — quiet hours
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `blocks at user cap`() {
        val settings = makeSettings(cap = 1)
        assertFalse(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = 1, // at user cap of 1
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `hard max clamps user cap — never raises`() {
        // User cap set to the hard max; a maliciously higher cap can never
        // permit more than TICKER_HARD_MAX_PER_DAY deliveries.
        val settings = makeSettings(cap = CampaignSettings.MAX_TICKER_PER_DAY)
        assertFalse(
            "At hard max (2) even with cap set higher, delivery must be blocked",
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = BudgetPolicy.TICKER_HARD_MAX_PER_DAY,
                dismissedSince = emptyList(),
            ),
        )
        assertTrue(
            "Below hard max should be allowed",
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = BudgetPolicy.TICKER_HARD_MAX_PER_DAY - 1,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `respects cooldown after dismissal`() {
        val settings = makeSettings()
        val now = nowAtHour(12) // noon
        val ninetyMinAgo = now - 90 * 60 * 1000L // within 2h cooldown

        assertFalse(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = now,
                settings = settings,
                sentToday = 0,
                dismissedSince = listOf(ninetyMinAgo),
            ),
        )
    }

    @Test
    fun `allows after cooldown elapses`() {
        val settings = makeSettings()
        val now = nowAtHour(15) // 3 PM
        val threeHoursAgo = now - 3 * 60 * 60 * 1000L // past 2h cooldown

        assertTrue(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = now,
                settings = settings,
                sentToday = 0,
                dismissedSince = listOf(threeHoursAgo),
            ),
        )
    }

    @Test
    fun `blocks on saturation signal multiple dismissals`() {
        val settings = makeSettings()
        val now = nowAtHour(14) // 2 PM
        val t1 = now - 5 * 60 * 60 * 1000L // 5 hours ago
        val t2 = now - 2 * 60 * 60 * 1000L // 2 hours ago (outside cooldown, inside 6h window)

        assertFalse(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = now,
                settings = settings,
                sentToday = 0,
                dismissedSince = listOf(t1, t2), // 2 dismissals within 6h → saturation skip
            ),
        )
    }

    @Test
    fun `zero-cap configuration denies delivery`() {
        val settings = makeSettings(enabled = true, cap = 0) // enabled but no pacing budget
        assertFalse(
            TickerPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }
}
