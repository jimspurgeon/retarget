/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.retarget.analytics.CheckInDao
import com.retarget.creative.Channel
import com.retarget.creative.RoomExposureLedger
import com.retarget.goal.GoalDatabase
import com.retarget.goal.PresetCatalog
import com.retarget.learning.CheckInWithReward
import com.retarget.learning.RewardRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * View model for the Dashboard screen.
 *
 * Exposes per-goal card state (one card per active goal, with today's
 * check-in count), aggregate channel pacing for today, and an in-app
 * check-in action that shares the notification/widget code path
 * (CheckInWithReward: transactional with bandit credit).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val db: GoalDatabase,
    private val ledger: RoomExposureLedger,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val checkInDao: CheckInDao = db.checkInDao()

    private fun startOfDayMs(): Long =
        LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** Per-goal card state: one entry per active goal with today's check-in count. */
    val perGoalState: Flow<List<GoalCardState>> =
        db.goalDao()
            .observeActive()
            .combine(checkInDao.countsByGoalToday(startOfDayMs())) { goals, counts ->
                val checkInMap = counts.associateBy({ it.goalId }, { it.cnt })
                goals.map { goal ->
                    val preset = PresetCatalog.ALL.firstOrNull { it.id == goal.presetId }
                    GoalCardState(
                        id = goal.id,
                        displayName = goal.displayName,
                        emoji = preset?.emoji ?: "🎯",
                        checkInsToday = checkInMap[goal.id] ?: 0,
                    )
                }
            }.flowOn(Dispatchers.IO)

    /**
     * Records an in-app check-in for [goalId]. Same path as the notification
     * "Check in" action and the widget button: one transaction writes the
     * check-in row and (best-effort) the bandit reward. UI updates arrive
     * reactively via [perGoalState] (Room invalidation).
     */
    fun checkIn(goalId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CheckInWithReward(db, RewardRecorder(db.learningStateDao(), db.exposureDao()))
                    .checkIn(goalId)
            } catch (e: Exception) {
                // Parity with the widget/receiver paths: degrade to a no-op
                // with a log line rather than crashing the UI.
                Log.w(TAG, "In-app check-in failed for goalId=$goalId", e)
            }
        }
    }

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
                val startOfDayMs = startOfDayMs()

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
                val startOfDayMs = startOfDayMs()

                val totalNotificationToday = ledger.exposuresTodayByChannel(Channel.NOTIFICATION, startOfDayMs)
                // Aggregate target is sum across all goals
                val aggregateTarget = goals.sumOf { it.settings.notificationTargetsPerDay }

                DailyPacingSummary(
                    countToday = totalNotificationToday,
                    targetPerDay = aggregateTarget,
                )
            }
            .flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "DashboardViewModel"
    }
}

/** State for one goal card on the dashboard. */
data class GoalCardState(
    val id: Long,
    val displayName: String,
    val emoji: String,
    val checkInsToday: Int,
)

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
