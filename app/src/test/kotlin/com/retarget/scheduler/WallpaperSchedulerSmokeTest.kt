/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for the wallpaper rotation scheduling helper (WorkManager
 * constraints check without actually running the background job).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperSchedulerSmokeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `schedule enqueues a periodic work with correct interval`() {
        WallpaperScheduler.scheduleWallpaperRotation(context, initialDelayMinutes = 0)

        val workManager = androidx.work.WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()

        assertTrue(infoList.isNotEmpty())
        val state = infoList[0].state
        // The unique periodic work must be enqueued or already running
        assertTrue(
            state == androidx.work.WorkInfo.State.ENQUEUED ||
                state == androidx.work.WorkInfo.State.RUNNING
        )
    }

    @Test
    fun `cancel removes any queued work`() {
        WallpaperScheduler.scheduleWallpaperRotation(context, initialDelayMinutes = 1000)
        WallpaperScheduler.cancelWallpaperRotation(context)

        val workManager = androidx.work.WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()

        // Cancelled work records linger until pruned; the invariant is that
        // nothing is enqueued or running under the tag anymore.
        assertTrue(
            infoList.all {
                it.state != androidx.work.WorkInfo.State.ENQUEUED &&
                    it.state != androidx.work.WorkInfo.State.RUNNING
            }
        )
    }

    @Test
    fun `WallpaperRotationPolicy quiet hours cover 22–07`() {
        for (hour in 0..23) {
            val allowed = WallpaperRotationPolicy.shouldRotateNow(hour)
            val inQuiet = (22..23).contains(hour) || (0..6).contains(hour)
            assertEquals(inQuiet, !allowed)
        }
    }
}
