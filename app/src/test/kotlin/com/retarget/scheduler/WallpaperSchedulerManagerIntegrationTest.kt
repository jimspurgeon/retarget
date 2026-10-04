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
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalConverters
import com.retarget.goal.GoalDatabase
import com.retarget.goal.GoalEntity
import com.retarget.goal.PresetCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration test for [WallpaperSchedulerManager] verifying that:
 * 1. Scheduler starts when a wallpaper-enabled goal becomes active
 * 2. Scheduler stops when all wallpaper-enabled goals are deactivated
 * 3. Scheduler responds to goal activation changes in real-time
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperSchedulerManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: GoalDatabase
    private lateinit var repository: com.retarget.goal.GoalRepository
    private lateinit var schedulerManager: WallpaperSchedulerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Setup database
        db = GoalDatabase.get(context)
        repository = com.retarget.goal.GoalRepository(db.goalDao())
        schedulerManager = WallpaperSchedulerManager(context, repository)

        // Clear any existing data
        runBlocking {
            withContext(Dispatchers.IO) {
                db.goalDao().observeActive().first()
                // Note: Room doesn't provide a clear-all method, but we're testing fresh DB
            }
        }
    }

    @After
    fun tearDown() {
        schedulerManager.stopMonitoring()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `scheduler starts when wallpaper-enabled goal is activated`() = runBlocking {
        schedulerManager.startMonitoring()

        // Install a goal with wallpaper enabled
        installGoalWithWallpaper(true)

        // Wait a short time for flow to emit and scheduler to react
        kotlinx.coroutines.delay(100)

        // Verify scheduler work is enqueued
        val workManager = androidx.work.WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        
        assertTrue(
            "Scheduler should be running when wallpaper-enabled goal is active",
            infoList.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || 
                          it.state == androidx.work.WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `scheduler stops when wallpaper-enabled goal is deactivated`() = runBlocking {
        schedulerManager.startMonitoring()

        // First, activate a goal with wallpaper
        val goalId = installGoalWithWallpaper(true)
        kotlinx.coroutines.delay(100)

        // Verify scheduler is running
        var workManager = androidx.work.WorkManager.getInstance(context)
        var infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertTrue(
            "Scheduler should run initially",
            infoList.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || 
                          it.state == androidx.work.WorkInfo.State.RUNNING }
        )

        // Deactivate the goal
        repository.setActive(goalId, false)
        kotlinx.coroutines.delay(100)

        // Verify scheduler stopped
        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        
        assertTrue(
            "Scheduler should stop when no wallpaper-enabled goals exist",
            infoList.all { it.state != androidx.work.WorkInfo.State.ENQUEUED && 
                           it.state != androidx.work.WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `scheduler continues when non-wallpaper goal is activated`() = runBlocking {
        schedulerManager.startMonitoring()

        // Install a goal WITHOUT wallpaper enabled
        installGoalWithWallpaper(false)
        kotlinx.coroutines.delay(100)

        // Verify scheduler is NOT running
        val workManager = androidx.work.WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        
        assertTrue(
            "Scheduler should NOT run when no wallpaper-enabled goals exist",
            infoList.all { it.state != androidx.work.WorkInfo.State.ENQUEUED && 
                           it.state != androidx.work.WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `scheduler toggles correctly with multiple goals`() = runBlocking {
        schedulerManager.startMonitoring()

        // Activate goal 1 WITHOUT wallpaper
        val goalId1 = installGoalWithWallpaper(false)
        kotlinx.coroutines.delay(100)

        var workManager = androidx.work.WorkManager.getInstance(context)
        var infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertTrue(
            "Should not run with only non-wallpaper goals",
            infoList.all { it.state != androidx.work.WorkInfo.State.ENQUEUED && 
                           it.state != androidx.work.WorkInfo.State.RUNNING }
        )

        // Activate goal 2 WITH wallpaper
        val goalId2 = installGoalWithWallpaper(true)
        kotlinx.coroutines.delay(100)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertTrue(
            "Should start running when wallpaper goal added",
            infoList.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || 
                          it.state == androidx.work.WorkInfo.State.RUNNING }
        )

        // Deactivate goal 1 (non-wallpaper), goal 2 still active
        repository.setActive(goalId1, false)
        kotlinx.coroutines.delay(100)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertTrue(
            "Should continue running when non-wallpaper goal deactivated",
            infoList.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || 
                          it.state == androidx.work.WorkInfo.State.RUNNING }
        )

        // Deactivate goal 2 (wallpaper) - now no wallpaper goals
        repository.setActive(goalId2, false)
        kotlinx.coroutines.delay(100)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertTrue(
            "Should stop when last wallpaper goal deactivated",
            infoList.all { it.state != androidx.work.WorkInfo.State.ENQUEUED && 
                           it.state != androidx.work.WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `refresh forces immediate scheduler update`() = runBlocking {
        schedulerManager.startMonitoring()

        // Initially no goals - scheduler off
        var workManager = androidx.work.WorkManager.getInstance(context)
        var infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertFalse("Initially no scheduler", 
            infoList.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || 
                          it.state == androidx.work.WorkInfo.State.RUNNING })

        // Insert goal directly into DB (bypassing repository to simulate direct DB change)
        val goal = GoalEntity(
            presetId = PresetCatalog.entries.first().id,
            displayName = "Test Goal",
            createdAt = System.currentTimeMillis(),
            settingsJson = GoalConverters().settingsToJson(
                CampaignSettings(wallpaperEnabled = true)
            ),
            active = true
        )
        db.goalDao().insert(goal)

        // Immediately check - flow might not have emitted yet
        // refresh() should force the update
        schedulerManager.refresh()
        kotlinx.coroutines.delay(50)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_wallpaper_rotation").get()
        assertTrue(
            "Refresh should trigger scheduler start after DB change",
            infoList.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || 
                          it.state == androidx.work.WorkInfo.State.RUNNING }
        )
    }

    /** Helper to install a goal with specified wallpaper setting */
    private suspend fun installGoalWithWallpaper(enabled: Boolean): Long {
        val preset = PresetCatalog.entries.first()
        val settings = CampaignSettings(wallpaperEnabled = enabled)
        val entity = GoalEntity(
            presetId = preset.id,
            displayName = preset.displayName,
            createdAt = System.currentTimeMillis(),
            settingsJson = GoalConverters().settingsToJson(settings),
            active = true
        )
        return db.goalDao().insert(entity)
    }
}
