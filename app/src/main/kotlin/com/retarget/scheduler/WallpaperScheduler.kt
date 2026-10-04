/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.retarget.channels.wallpaper.WallpaperRotationWorker
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Scheduling helpers for the wallpaper engine (DEVELOPMENT.md Phase 1).
 *
 * - [scheduleWallpaperRotation] registers the PeriodicWorkRequest (~8h cadence,
 *   respecting quiet hours internally).
 * - [cancelWallpaperRotation] cancels the worker by unique tag.
 *
 * Pure Kotlin API surface; Android plumbing lives in the caller (the
 * Application class, or settings when a user toggles the channel).
 */
object WallpaperScheduler {
    private const val WORKER_TAG = "retarget_wallpaper_rotation"
    private const val INTERVAL_HOURS = 8L

    /**
     * Registers a periodic wallpaper rotation worker.
     *
     * Idempotent: cancels any prior work with the same tag before enqueuing.
     * @param initialDelayMinutes 0 to start immediately; otherwise delay before first execution.
     */
    fun scheduleWallpaperRotation(context: Context, initialDelayMinutes: Long = 0) {
        WorkManager.getInstance(context).let { wm ->
            wm.cancelUniqueWork(WORKER_TAG)
            val request =
                PeriodicWorkRequestBuilder<WallpaperRotationWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                    .setInitialDelay(max(0, initialDelayMinutes), TimeUnit.MINUTES)
                    .addTag(WORKER_TAG)
                    .build()
            wm.enqueueUniquePeriodicWork(
                WORKER_TAG,
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
        }
    }

    /** Cancels any scheduled wallpaper rotation. */
    fun cancelWallpaperRotation(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORKER_TAG)
    }
}
