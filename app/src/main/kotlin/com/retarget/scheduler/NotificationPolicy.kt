/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.creative.Channel
import com.retarget.goal.CampaignSettings

/**
 * Notification channel pacing — PURE KOTLIN, no Android dependencies.
 *
 * Mirrors WallpaperRotationPolicy style: a thin, testable policy layer that
 * delegates heavy lifting to BudgetPolicy. The notification channel is a
 * high-interrupt surface, so we enforce strict frequency caps, quiet hours,
 * and cooldowns (Development.md "Pre-registered decisions" #6).
 *
 * All decisions derive from CampaignSettings + BudgetPolicy hard limits.
 */
object NotificationPolicy {
    /**
     * Determines whether a notification nudge can be delivered for the given goal.
     *
     * Consults:
     *   - CampaignSettings.notificationEnabled (user opt-in)
     *   - CampaignSettings.notificationTargetsPerDay (user-configurable cap)
     *   - BudgetPolicy.isQuietHour() (hard silent window)
     *   - BudgetPolicy.canDeliver() (caps, cooldowns, saturation)
     *
     * @param goalId         unique identifier for the goal (for ledger lookups)
     * @param nowMs          current timestamp in milliseconds (injectable for tests)
     * @param settings       per-goal campaign settings (contains enablement & caps)
     * @param sentToday      count of notifications already sent today for this goal
     * @param dismissedSince Midnight timestamps of dismissals/cooldown triggers (for ledger-backed callers)
     * @return true if notification delivery is currently permitted
     */
    fun canDeliver(
        goalId: Long,
        nowMs: Long,
        settings: CampaignSettings,
        sentToday: Int,
        dismissedSince: List<Long> = emptyList(),
    ): Boolean {
        // Gate 1: User must have explicitly enabled notifications for this goal.
        if (!settings.notificationEnabled) return false

        // Gate 2: Quiet hours are sacred — no notifications during silent window.
        val hourOfDay = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour
        if (BudgetPolicy.isQuietHour(hourOfDay)) return false

        // Gate 3: Enforce daily cap (user setting clamped by BudgetPolicy hard max).
        val userCap = settings.notificationTargetsPerDay
        if (sentToday >= userCap) return false

        // Gate 4: Delegate to BudgetPolicy for cooldown, saturation, and hard-max enforcement.
        return BudgetPolicy.canDeliver(
            channel = Channel.NOTIFICATION,
            sentTodayOnChannel = sentToday,
            dismissedRecently = dismissedSince,
            nowMs = nowMs,
            hardMaxOverride = userCap,
        )
    }
}
