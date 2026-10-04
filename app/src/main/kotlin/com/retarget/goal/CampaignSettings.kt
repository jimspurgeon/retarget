/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import kotlinx.serialization.Serializable

/**
 * Per-channel pacing and enablement knobs for one campaign.
 *
 * Values must respect BudgetPolicy hard caps at write time; the UI clamps and
 * the scheduler re-validates (defense in depth — see digital-wellbeing.md §3.4).
 * Stored as JSON inside GoalEntity (decision: embedded, not normalized).
 */
@Serializable
data class CampaignSettings(
    val wallpaperEnabled: Boolean = true,
    val notificationEnabled: Boolean = false, // off by default until Phase 2 lands the channel
    val wallpaperTargetsPerDay: Int,
    val notificationTargetsPerDay: Int,
) {
    init {
        require(wallpaperTargetsPerDay in 1..MAX_WALLPAPER_PER_DAY) {
            "wallpaperTargetsPerDay $wallpaperTargetsPerDay out of range 1..$MAX_WALLPAPER_PER_DAY"
        }
        require(notificationTargetsPerDay in 0..MAX_NOTIFICATION_PER_DAY) {
            "notificationTargetsPerDay $notificationTargetsPerDay out of range 0..$MAX_NOTIFICATION_PER_DAY"
        }
    }

    companion object {
        /** Mirror of BudgetPolicy hard caps; kept in lockstep by CampaignSettingsTest. */
        const val MAX_WALLPAPER_PER_DAY = 4
        const val MAX_NOTIFICATION_PER_DAY = 3

        /** Factory defaults derived from a preset's pacing. */
        fun fromPreset(preset: PresetCampaign): CampaignSettings =
            CampaignSettings(
                wallpaperEnabled = true,
                notificationEnabled = false,
                wallpaperTargetsPerDay = preset.wallpaperTargetsPerDay,
                notificationTargetsPerDay = preset.notificationTargetsPerDay,
            )
    }
}
