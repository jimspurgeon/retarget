/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import android.util.Log
import com.retarget.goal.GoalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Manages wallpaper scheduler lifecycle in response to goal activation.
 *
 * Watches for active goals and automatically starts/stops the wallpaper rotation
 * worker based on whether any goal has the wallpaper channel enabled. This ensures
 * the scheduler only runs when there's a user-initiated campaign to support
 * (AGENTS.md §2: user-initiated, reversible nudges).
 *
 * Thread safety: all public methods are safe to call from any thread. Internal
 * flow collection happens on a dedicated coroutine scope with SupervisorJob.
 */
class WallpaperSchedulerManager(
    private val context: Context,
    private val goalRepository: GoalRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var isActive = false

    /**
     * Start observing goal activation changes and manage scheduler lifecycle.
     * Call this from Application.onCreate() or early in app startup.
     * Idempotent: subsequent calls are no-ops.
     */
    fun startMonitoring() {
        if (isActive) {
            Log.d(TAG, "Scheduler manager already active; skipping startMonitoring")
            return
        }
        isActive = true
        scope.launch {
            goalRepository.observeActive().collectLatest { goals ->
                handleGoalUpdate(goals)
            }
        }
        Log.i(TAG, "WallpaperSchedulerManager started monitoring goals")
    }

    /**
     * Stop monitoring and cancel any active scheduler.
     * Safe to call multiple times.
     */
    fun stopMonitoring() {
        if (!isActive) return
        isActive = false
        scope.cancel()
        WallpaperScheduler.cancelWallpaperRotation(context)
        Log.i(TAG, "WallpaperSchedulerManager stopped; scheduler cancelled")
    }

    /**
     * Force immediate refresh of scheduler state based on current goals.
     * Useful after programmatic goal changes that may not yet be reflected
     * in the flow emission.
     */
    fun refresh() {
        if (!isActive) {
            Log.w(TAG, "refresh() called but manager not active; call startMonitoring first")
            return
        }
        // Re-read from database to get latest state
        scope.launch {
            val goals = goalRepository.observeActive().firstOrNull() ?: emptyList()
            handleGoalUpdate(goals)
        }
    }

    private fun handleGoalUpdate(goals: List<com.retarget.goal.GoalEntity>) {
        val shouldRun = goals.any { goal ->
            try {
                val settings = com.retarget.goal.GoalConverters().jsonToSettings(goal.settingsJson)
                settings.wallpaperEnabled
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse settings for goal ${goal.id}; treating as disabled", e)
                false
            }
        }

        if (shouldRun) {
            WallpaperScheduler.scheduleWallpaperRotation(context)
            Log.d(TAG, "Started wallpaper rotation (active goals with wallpaper=${goals.size})")
        } else {
            WallpaperScheduler.cancelWallpaperRotation(context)
            Log.d(TAG, "Cancelled wallpaper rotation (no active wallpaper-enabled goals)")
        }
    }

    companion object {
        private const val TAG = "WallpaperSchedulerManager"
    }
}
