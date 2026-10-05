/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retarget.creative.Channel
import com.retarget.creative.RoomExposureLedger
import com.retarget.goal.GoalDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * View model for the Dashboard screen, exposing campaign pacing state.
 *
 * Displays per-channel exposure counts for today, enabling users to audit
 * how their nudge budget is being spent (e.g., "Notifications: 2/3 today").
 */
class DashboardViewModel(
    private val db: GoalDatabase,
    private val ledger: RoomExposureLedger,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** Active goals with their pacing settings. */
    val activeGoals: Flow<List<GoalPacingState>> =
        db.goalDao()
            .observeActive()
            .map { goals ->
                goals.map { goal ->
                    GoalPacingState(
                        id = goal.id,
                        displayName = goal.displayName,
                        presetId = goal.presetId,
                        wallpaperEnabled = goal.settings.wallpaperEnabled,
                        wallpaperTargetPerDay = goal.settings.wallpaperTargetsPerDay,
                        notificationEnabled = goal.settings.notificationEnabled,
                        notificationTargetPerDay = goal.settings.notificationTargetsPerDay,
                    )
                }
            }

    /** Wallpaper pacing summary across all active goals. */
    val wallpaperPacingSummary: Flow<DailyPacingSummary> =
        db.goalDao()
            .observeActive()
            .map { goals ->
                val startOfDayMs = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

                val totalWallpaperToday =
                    goals.sumOf { _ -> ledger.exposuresTodayByChannel(Channel.WALLPAPER, startOfDayMs) }
                // Aggregate target is sum across all goals
                val aggregateTarget = goals.sumOf { it.settings.wallpaperTargetsPerDay }

                DailyPacingSummary(
                    countToday = totalWallpaperToday,
                    targetPerDay = aggregateTarget,
                )
            }

    /** Notification pacing summary across all active goals. */
    val notificationPacingSummary: Flow<DailyPacingSummary> =
        db.goalDao()
            .observeActive()
            .map { goals ->
                val startOfDayMs = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

                val totalNotificationToday =
                    goals.sumOf { _ ->
                        ledger.exposuresTodayByChannel(Channel.NOTIFICATION, startOfDayMs)
                    }
                // Aggregate target is sum across all goals
                val aggregateTarget = goals.sumOf { it.settings.notificationTargetsPerDay }

                DailyPacingSummary(
                    countToday = totalNotificationToday,
                    targetPerDay = aggregateTarget,
                )
            }
}

/** Per-goal pacing state exposed to the UI. */
data class GoalPacingState(
    val id: Long,
    val displayName: String,
    val presetId: String,
    val wallpaperEnabled: Boolean,
    val wallpaperTargetPerDay: Int,
    val notificationEnabled: Boolean,
    val notificationTargetPerDay: Int,
)

/** Daily pacing summary for a channel. */
data class DailyPacingSummary(
    val countToday: Int,
    val targetPerDay: Int,
) {
    val isOverBudget: Boolean = countToday > targetPerDay
}
