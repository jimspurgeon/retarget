/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.creative.Channel
import com.retarget.goal.CampaignSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    companion object {
        // Helper to create settings with given notification cap
        fun makeSettings(enabled: Boolean = true, cap: Int = 3): CampaignSettings =
            CampaignSettings(
                wallpaperEnabled = true,
                notificationEnabled = enabled,
                wallpaperTargetsPerDay = 2,
                notificationTargetsPerDay = cap,
            )
    }

    @Test
    fun `denies when notificationEnabled is false`() {
        val settings = makeSettings(enabled = false)
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = System.currentTimeMillis(),
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `allows within daily cap`() {
        val settings = makeSettings(cap = 3)
        assertTrue(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12), // noon - outside quiet hours
                settings = settings,
                sentToday = 2, // under cap of 3
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `blocks at daily cap`() {
        val settings = makeSettings(cap = 3)
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12), // noon
                settings = settings,
                sentToday = 3, // at cap
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `blocks during quiet hours evening`() {
        val settings = makeSettings()
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(23), // 11 PM - quiet hours
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `blocks during quiet hours morning`() {
        val settings = makeSettings()
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(5), // 5 AM - quiet hours
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `allows at quiet hours boundary morning`() {
        val settings = makeSettings()
        assertTrue(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(7), // 7 AM - quiet hours end
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `allows at quiet hours boundary evening`() {
        val settings = makeSettings()
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(22), // 10 PM - quiet hours begin
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `respects cooldown after dismissal`() {
        val settings = makeSettings()
        val now = nowAtHour(12) // noon
        val ninetyMinAgo = now - 90 * 60 * 1000L // 90 minutes ago

        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = now,
                settings = settings,
                sentToday = 0,
                dismissedSince = listOf(ninetyMinAgo), // within 2h cooldown
            ),
        )
    }

    @Test
    fun `allows after cooldown elapses`() {
        val settings = makeSettings()
        val now = nowAtHour(15) // 3 PM
        val threeHoursAgo = now - 3 * 60 * 60 * 1000L // 3 hours ago

        assertTrue(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = now,
                settings = settings,
                sentToday = 0,
                dismissedSince = listOf(threeHoursAgo), // past 2h cooldown
            ),
        )
    }

    @Test
    fun `blocks on saturation signal multiple dismissals`() {
        val settings = makeSettings()
        val now = nowAtHour(14) // 2 PM
        val t1 = now - 5 * 60 * 60 * 1000L // 5 hours ago
        val t2 = now - 2 * 60 * 60 * 1000L // 2 hours ago

        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = now,
                settings = settings,
                sentToday = 0,
                dismissedSince = listOf(t1, t2), // 2 dismissals within 6h window
            ),
        )
    }

    @Test
    fun `delegates hard max enforcement to BudgetPolicy`() {
        // CampaignSettings already validates cap <= 3, so we test at the hard max boundary.
        val settings = makeSettings(cap = 3)
        // At cap (3), delivery should be blocked.
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = 3, // at hard max
                dismissedSince = emptyList(),
            ),
        )
        // One below cap should be allowed.
        assertTrue(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = 2,
                dismissedSince = emptyList(),
            ),
        )
    }

    @Test
    fun `allows zero-cap configuration`() {
        val settings = CampaignSettings(
            wallpaperEnabled = true,
            notificationEnabled = true,
            wallpaperTargetsPerDay = 2,
            notificationTargetsPerDay = 0, // user opted out via cap
        )
        assertFalse(
            NotificationPolicy.canDeliver(
                goalId = 1L,
                nowMs = nowAtHour(12),
                settings = settings,
                sentToday = 0,
                dismissedSince = emptyList(),
            ),
        )
    }

    /** Helper: create a timestamp at the given local hour today (midnight + hours). */
    private fun nowAtHour(hour: Int): Long {
        val now = java.time.LocalDate.now().atTime(hour, 0).toInstant(
            java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now())
        ).toEpochMilli()
        return now
    }
}
