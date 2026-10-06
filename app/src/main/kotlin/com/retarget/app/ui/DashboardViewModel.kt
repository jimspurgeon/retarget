/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.retarget.analytics.CheckInDao
import com.retarget.creative.Channel
import com.retarget.creative.RoomExposureLedger
import com.retarget.goal.GoalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * View model for the Dashboard screen, exposing campaign pacing state.
 *
 * Displays per-channel exposure counts for today, enabling users to audit
 * how their nudge budget is being spent (e.g., "Notifications: 2/3 today").
 * Also shows check-in rates (check-ins / exposures) per goal.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val db: GoalDatabase,
    private val ledger: RoomExposureLedger,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val checkInDao: CheckInDao = db.checkInDao()

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

                val totalWallpaperToday = ledger.exposuresTodayByChannel(Channel.WALLPAPER, startOfDayMs)
                // Aggregate target is sum across all goals
                val aggregateTarget = goals.sumOf { it.settings.wallpaperTargetsPerDay }

                DailyPacingSummary(
                    countToday = totalWallpaperToday,
                    targetPerDay = aggregateTarget,
                )
            }
            .flowOn(Dispatchers.IO)

    /** Notification pacing summary across all active goals. */
    val notificationPacingSummary: Flow<DailyPacingSummary> =
        db.goalDao()
            .observeActive()
            .map { goals ->
                val startOfDayMs = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

                val totalNotificationToday = ledger.exposuresTodayByChannel(Channel.NOTIFICATION, startOfDayMs)
                // Aggregate target is sum across all goals
                val aggregateTarget = goals.sumOf { it.settings.notificationTargetsPerDay }

                DailyPacingSummary(
                    countToday = totalNotificationToday,
                    targetPerDay = aggregateTarget,
                )
            }
            .flowOn(Dispatchers.IO)

    /** Check-in rates per goal (check-ins today / notification exposures today). */
    val checkInRates: Flow<List<GoalCheckInRate>> =
        db.goalDao()
            .observeActive()
            .combine(checkInDao.countsByGoalToday(LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli())) { goals, checkInCounts ->
                val startOfDayMs = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val checkInMap = checkInCounts.associateBy({ it.goalId }, { it.cnt })

                goals.map { goal ->
                    val checkInsToday = checkInMap[goal.id] ?: 0
                    val exposuresToday = ledger.exposuresTodayByChannel(Channel.NOTIFICATION, startOfDayMs)
                    // For per-goal exposure count, we'd need to extend ExposureDao
                    // For now, distribute proportionally or use total as denominator
                    GoalCheckInRate(
                        goalId = goal.id,
                        checkInsToday = checkInsToday,
                        exposuresToday = exposuresToday, // TODO: refine to per-goal
                        checkInRate = if (exposuresToday > 0) checkInsToday.toDouble() / exposuresToday else 0.0,
                    )
                }
            }
            .flowOn(Dispatchers.IO)
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

/** Check-in rate statistics for a goal. */
data class GoalCheckInRate(
    val goalId: Long,
    val checkInsToday: Int,
    val exposuresToday: Int,
    val checkInRate: Double, // 0.0 to 1.0, or >1.0 if multiple check-ins per exposure
) {
    val percentageDisplay: String
        get() = "${(checkInRate * 100).toInt()}%"
}
