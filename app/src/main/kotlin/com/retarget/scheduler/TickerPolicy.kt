/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.creative.Channel
import com.retarget.goal.CampaignSettings

/**
 * Lock-screen ticker pacing — PURE KOTLIN, no Android dependencies.
 *
 * Mirrors NotificationPolicy style: a thin, testable policy layer that
 * delegates heavy lifting to BudgetPolicy. The ticker is a low-interruption
 * ambient surface (silent ongoing notification on the lock screen —
 * docs/research/android-platform.md §6), but it is still a delivered
 * impression, so it gets the full gate stack:
 *
 *   - CampaignSettings.tickerEnabled (user opt-in, default OFF —
 *     pre-registered decision #4, PHASE3-AGENCY.md §8)
 *   - BudgetPolicy.isQuietHour() (ticker must be ABSENT during quiet hours —
 *     PHASE3-AGENCY.md §4 acceptance)
 *   - CampaignSettings.tickerTargetsPerDay (user-configurable cap)
 *   - BudgetPolicy.canDeliver() (hard max, cooldowns, saturation)
 *
 * Per PHASE3-AGENCY.md §4 the ticker also coordinates through NudgeScheduler's
 * existing crowding backoff (MIN_GAP_MS); that check lives in NudgeScheduler,
 * alongside the other channels.
 */
object TickerPolicy {
    /**
     * Determines whether a lock-screen ticker nudge can be delivered for the
     * given goal.
     *
     * Consults:
     *   - CampaignSettings.tickerEnabled (user opt-in)
     *   - BudgetPolicy.isQuietHour() (hard silent window — no ticker during
     *     quiet hours, even though the ticker is silent: "absent during quiet
     *     hours" is the acceptance criterion, not merely "silent")
     *   - CampaignSettings.tickerTargetsPerDay (user-configurable cap)
     *   - BudgetPolicy.canDeliver() (ticker hard max, cooldown, saturation)
     *
     * @param goalId         unique identifier for the goal (reserved for ledger lookups)
     * @param nowMs          current timestamp in milliseconds (injectable for tests)
     * @param settings       per-goal campaign settings (contains enablement & caps)
     * @param sentToday      count of ticker exposures already recorded today for this goal
     * @param dismissedSince timestamps of dismissals/cooldown triggers
     * @return true if ticker delivery is currently permitted
     */
    fun canDeliver(
        goalId: Long,
        nowMs: Long,
        settings: CampaignSettings,
        sentToday: Int,
        dismissedSince: List<Long> = emptyList(),
    ): Boolean {
        // Gate 1: User must have explicitly enabled the ticker for this goal.
        if (!settings.tickerEnabled) return false

        // Gate 2: Quiet hours are sacred — the ticker must be ABSENT during the
        // silent window, not merely silent (PHASE3-AGENCY.md §4 acceptance).
        val hourOfDay = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour
        if (BudgetPolicy.isQuietHour(hourOfDay)) return false

        // Gate 3: Enforce daily cap (user setting clamped by BudgetPolicy hard max).
        val userCap = settings.tickerTargetsPerDay
        if (sentToday >= userCap) return false

        // Gate 4: Delegate to BudgetPolicy for cooldown, saturation, and hard-max enforcement.
        return BudgetPolicy.canDeliver(
            channel = Channel.LOCK_SCREEN_TICKER,
            sentTodayOnChannel = sentToday,
            dismissedRecently = dismissedSince,
            nowMs = nowMs,
            hardMaxOverride = userCap,
        )
    }
}
