/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app

import android.app.Application
import com.retarget.scheduler.NotificationSchedulerManager
import com.retarget.scheduler.WallpaperSchedulerManager
import com.retarget.widget.RetargetWidgetReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt root; nothing else belongs here — feature
 * initialization happens lazily as phases land.
 */
@HiltAndroidApp
class RetargetApplication : Application() {
    @Inject
    lateinit var wallpaperSchedulerManager: WallpaperSchedulerManager

    @Inject
    lateinit var notificationSchedulerManager: NotificationSchedulerManager

    override fun onCreate() {
        super.onCreate()
        // Schedule M3.2 widget updates on the same cadence as notifications (no extra wakeups).
        RetargetWidgetReceiver.scheduleUpdates(this)

        // Wire wallpaper scheduler to goal activation lifecycle.
        // Starts monitoring goals and auto-manages scheduler based on user-enabled campaigns.
        wallpaperSchedulerManager.startMonitoring()

        // Wire notification scheduler to goal activation lifecycle (Phase 2).
        // Monitors goals with notification enabled and auto-manages notification delivery.
        notificationSchedulerManager.startMonitoring()
    }
}
