/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import com.retarget.creative.Channel
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalEntity
import kotlinx.serialization.json.Json

/**
 * Pure function that composes the widget display state from domain entities.
 *
 * The caller supplies all day-boundary calculations and pre-computed counts:
 * this function performs only structural mapping so it remains fully testable
 * on the JVM without time-zone or database plumbing. Today-boundary semantics
 * (start-of-day instant) come from the caller, not this object.
 *
 * @param goal The currently active goal, or null when none exists.
 * @param exposuresToday Pre-counted exposures for today keyed by channel.
 *   Callers compute these via ExposureLedger.exposuresTodayByChannel(...)
 *   using a single consistent start-of-day instant.
 * @param checkInsToday Number of check-ins recorded today for this goal.
 * @param latestCreativePath Absolute path of the freshest creative image,
 *   or null when no cached creative exists.
 *
 * @return [WidgetSnapshot] ready for Glance consumption by Worker B.
 */
object WidgetStateComposer {

    private val settingsJsonParser = Json { ignoreUnknownKeys = true }

    fun compose(
        goal: GoalEntity?,
        exposuresToday: Map<Channel, Int>,
        checkInsToday: Int,
        latestCreativePath: String?,
    ): WidgetSnapshot {
        if (goal == null) {
            return WidgetSnapshot.Empty
        }

        // Try to parse campaign settings; malformed JSON yields error state.
        val settings: CampaignSettings = runCatching {
            settingsJsonParser.decodeFromString(CampaignSettings.serializer(), goal.settingsJson)
        }.getOrElse {
            return WidgetSnapshot.Error(ErrorReason.SettingsUnparseable)
        }

        // Channel disabled → show zero for both actual/target.
        val wallpaperActual = if (settings.wallpaperEnabled) exposuresToday[Channel.WALLPAPER] ?: 0 else 0
        val wallpaperTarget = if (settings.wallpaperEnabled) settings.wallpaperTargetsPerDay else 0

        val notificationActual =
            if (settings.notificationEnabled) exposuresToday[Channel.NOTIFICATION] ?: 0 else 0
        val notificationTarget =
            if (settings.notificationEnabled) settings.notificationTargetsPerDay else 0

        return WidgetSnapshot.Active(
            goalDisplayName = goal.displayName,
            wallpaperShownToday = wallpaperActual,
            wallpaperTargetToday = wallpaperTarget,
            notificationShownToday = notificationActual,
            notificationTargetToday = notificationTarget,
            creativeImagePath = latestCreativePath,
            checkedInToday = checkInsToday > 0,
        )
    }
}
