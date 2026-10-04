/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app

import android.app.Application
import com.retarget.scheduler.WallpaperScheduler
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt root; nothing else belongs here — feature
 * initialization happens lazily as phases land.
 */
@HiltAndroidApp
class RetargetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase 1: keep the billboard rotating (~8h cadence, quiet-hours-aware).
        WallpaperScheduler.scheduleWallpaperRotation(this)
    }
}
