/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.creative.Channel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPolicyTest {
    @Test
    fun `quiet hours span midnight correctly`() {
        // 22:00–07:00 quiet window
        assertTrue(BudgetPolicy.isQuietHour(23))
        assertTrue(BudgetPolicy.isQuietHour(2))
        assertTrue(BudgetPolicy.isQuietHour(6))
        assertFalse(BudgetPolicy.isQuietHour(7))
        assertFalse(BudgetPolicy.isQuietHour(12))
        assertFalse(BudgetPolicy.isQuietHour(21))
    }

    @Test
    fun `hard max blocks excessive notifications`() {
        val now = 1_000_000_000L
        assertFalse(
            BudgetPolicy.canDeliver(
                channel = Channel.NOTIFICATION,
                sentTodayOnChannel = BudgetPolicy.NOTIFICATION_HARD_MAX_PER_DAY_PER_GOAL,
                dismissedRecently = emptyList(),
                nowMs = now,
            ),
        )
        assertTrue(
            BudgetPolicy.canDeliver(
                channel = Channel.NOTIFICATION,
                sentTodayOnChannel = BudgetPolicy.NOTIFICATION_HARD_MAX_PER_DAY_PER_GOAL - 1,
                dismissedRecently = emptyList(),
                nowMs = now,
            ),
        )
    }

    @Test
    fun `override can only lower the cap, never raise`() {
        val now = 1_000_000_000L
        // Attempt to raise the notification cap to 10 via override — must not allow 4th.
        assertFalse(
            BudgetPolicy.canDeliver(
                channel = Channel.NOTIFICATION,
                sentTodayOnChannel = 3,
                dismissedRecently = emptyList(),
                nowMs = now,
                hardMaxOverride = 10, // malicious/high value must be clamped
            ),
        )
    }

    @Test
    fun `recent dismissal triggers cooldown`() {
        val now = 1_000_000_000L
        val ninetyMinAgo = now - 90 * 60 * 1000L
        assertFalse(
            BudgetPolicy.canDeliver(
                channel = Channel.NOTIFICATION,
                sentTodayOnChannel = 0,
                dismissedRecently = listOf(ninetyMinAgo),
                nowMs = now,
            ),
        )
        // After cooldown elapses, delivery allowed again.
        val threeHoursLater = now + 3 * 60 * 60 * 1000L
        assertTrue(
            BudgetPolicy.canDeliver(
                channel = Channel.NOTIFICATION,
                sentTodayOnChannel = 0,
                dismissedRecently = listOf(ninetyMinAgo),
                nowMs = threeHoursLater,
            ),
        )
    }

    @Test
    fun `two dismissals within six hours triggers saturation skip`() {
        val now = 1_000_000_000L
        val t1 = now - 5 * 60 * 60 * 1000L
        val t2 = now - 1 * 60 * 60 * 1000L
        // Both dismissals are older than the 2h cooldown but within the 6h window.
        assertFalse(
            BudgetPolicy.canDeliver(
                channel = Channel.NOTIFICATION,
                sentTodayOnChannel = 0,
                dismissedRecently = listOf(t1, t2),
                nowMs = now,
            ),
        )
    }

    @Test
    fun `notification gap allows delivery after 90 min`() {
        val now = 1_000_000_000L
        val ninetyMinAgo = now - 90 * 60 * 1000L

        assertTrue(
            "Exactly 90 min gap should allow",
            BudgetPolicy.isWithinNotificationGap(ninetyMinAgo, now),
        )
        assertTrue(
            "More than 90 min gap should allow",
            BudgetPolicy.isWithinNotificationGap(now - 100 * 60 * 1000L, now),
        )
        assertTrue(
            "No prior notification should always allow",
            BudgetPolicy.isWithinNotificationGap(null, now),
        )
    }

    @Test
    fun `notification gap blocks delivery within 90 min`() {
        val now = 1_000_000_000L
        val eightyFiveMinAgo = now - 85 * 60 * 1000L

        assertFalse(
            "85 min gap should block",
            BudgetPolicy.isWithinNotificationGap(eightyFiveMinAgo, now),
        )
        assertFalse(
            "Same timestamp should block",
            BudgetPolicy.isWithinNotificationGap(now, now),
        )
    }

    // ---- LOCK_SCREEN_TICKER cap wiring (M3.3, PHASE3-AGENCY.md §4) ----

    @Test
    fun `ticker hard max blocks deliveries at cap`() {
        val now = 1_000_000_000L
        assertFalse(
            "At ticker hard max, delivery must be blocked",
            BudgetPolicy.canDeliver(
                channel = Channel.LOCK_SCREEN_TICKER,
                sentTodayOnChannel = BudgetPolicy.TICKER_HARD_MAX_PER_DAY,
                dismissedRecently = emptyList(),
                nowMs = now,
            ),
        )
        assertTrue(
            "Below ticker hard max, delivery must be allowed",
            BudgetPolicy.canDeliver(
                channel = Channel.LOCK_SCREEN_TICKER,
                sentTodayOnChannel = BudgetPolicy.TICKER_HARD_MAX_PER_DAY - 1,
                dismissedRecently = emptyList(),
                nowMs = now,
            ),
        )
    }

    @Test
    fun `ticker override can only lower the cap, never raise`() {
        val now = 1_000_000_000L
        assertFalse(
            "Attempt to raise the ticker cap to 10 via override must be clamped",
            BudgetPolicy.canDeliver(
                channel = Channel.LOCK_SCREEN_TICKER,
                sentTodayOnChannel = BudgetPolicy.TICKER_HARD_MAX_PER_DAY,
                dismissedRecently = emptyList(),
                nowMs = now,
                hardMaxOverride = 10, // malicious/high value must be clamped
            ),
        )
    }
}
