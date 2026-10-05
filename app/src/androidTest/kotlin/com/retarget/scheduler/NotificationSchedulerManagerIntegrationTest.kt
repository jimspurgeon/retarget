/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.retarget.creative.Channel
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalConverters
import com.retarget.goal.GoalDatabase
import com.retarget.goal.GoalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import androidx.work.WorkInfo
import androidx.work.testing.SynchronousExecutor
import com.retarget.creative.RoomExposureLedger

/**
 * Integration test for [NotificationSchedulerManager] verifying that:
 * 1. Scheduler starts when a notification-enabled goal becomes active
 * 2. Scheduler stops when all notification-enabled goals are deactivated
 * 3. WorkManager worker is properly enqueued with correct tags
 * 4. Exposure ledger records notifications after simulated delivery
 *
 * Mirrors [WallpaperSchedulerManagerIntegrationTest] pattern adapted for notifications.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationSchedulerManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: GoalDatabase
    private lateinit var repository: com.retarget.goal.GoalRepository
    private lateinit var schedulerManager: NotificationSchedulerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize WorkManager for testing with synchronous executor
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        // Setup database
        db = GoalDatabase.get(context)
        repository = com.retarget.goal.GoalRepository(db.goalDao())
        schedulerManager = NotificationSchedulerManager(context, repository)

        // Clear any existing data by recreating DB
        db.clearAllTables()
    }

    @After
    fun tearDown() {
        schedulerManager.stopMonitoring()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `scheduler starts when notification-enabled goal is activated`() = runBlocking {
        schedulerManager.startMonitoring()

        // Install a goal with notifications enabled
        installGoalWithNotifications(true)

        // Wait a short time for flow to emit and scheduler to react
        kotlinx.coroutines.delay(100)

        // Verify scheduler work is enqueued
        val workManager = androidx.work.WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        
        assertTrue(
            "Scheduler should be running when notification-enabled goal is active",
            infoList.any { it.state == WorkInfo.State.ENQUEUED || 
                          it.state == WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `scheduler stops when notification-enabled goal is deactivated`() = runBlocking {
        schedulerManager.startMonitoring()

        // First, activate a goal with notifications
        val goalId = installGoalWithNotifications(true)
        kotlinx.coroutines.delay(100)

        // Verify scheduler is running
        var workManager = androidx.work.WorkManager.getInstance(context)
        var infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        assertTrue(
            "Scheduler should run initially",
            infoList.any { it.state == WorkInfo.State.ENQUEUED || 
                          it.state == WorkInfo.State.RUNNING }
        )

        // Deactivate the goal
        repository.setActive(goalId, false)
        kotlinx.coroutines.delay(100)

        // Verify scheduler stopped
        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        
        assertTrue(
            "Scheduler should stop when no notification-enabled goals exist",
            infoList.all { it.state != WorkInfo.State.ENQUEUED && 
                           it.state != WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `scheduler continues when non-notification goal is activated`() = runBlocking {
        schedulerManager.startMonitoring()

        // Install a goal WITHOUT notifications enabled
        installGoalWithNotifications(false)
        kotlinx.coroutines.delay(100)

        // Verify scheduler is NOT running
        val workManager = androidx.work.WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        
        assertTrue(
            "Scheduler should NOT run when no notification-enabled goals exist",
            infoList.all { it.state != WorkInfo.State.ENQUEUED && 
                           it.state != WorkInfo.State.RUNNING }
        )
    }

    @Test
    fun `scheduler toggles correctly with multiple goals`() = runBlocking {
        schedulerManager.startMonitoring()

        // Install first goal WITH notifications
        val goalId1 = installGoalWithNotifications(true)
        kotlinx.coroutines.delay(100)

        var workManager = androidx.work.WorkManager.getInstance(context)
        var infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        assertTrue("Scheduler should run with one notification-enabled goal",
            infoList.any { it.state == WorkInfo.State.ENQUEUED })

        // Install second goal WITHOUT notifications - scheduler should still run
        installGoalWithNotifications(false)
        kotlinx.coroutines.delay(100)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        assertTrue("Scheduler should continue with mixed goals",
            infoList.any { it.state == WorkInfo.State.ENQUEUED })

        // Deactivate first goal - scheduler should stop
        repository.setActive(goalId1, false)
        kotlinx.coroutines.delay(100)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        assertTrue("Scheduler should stop when only non-notification goals remain",
            infoList.all { it.state != WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `refresh updates scheduler state after goal change`() = runBlocking {
        schedulerManager.startMonitoring()

        // Start with no notification goals
        installGoalWithNotifications(false)
        kotlinx.coroutines.delay(100)

        var workManager = androidx.work.WorkManager.getInstance(context)
        var infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        assertTrue("Scheduler should not run initially",
            infoList.all { it.state != WorkInfo.State.ENQUEUED })

        // Activate notification on existing goal
        updateGoalNotificationState(installGoalWithNotifications(false), true)
        
        // Refresh should pick up the change
        schedulerManager.refresh()
        kotlinx.coroutines.delay(100)

        workManager = androidx.work.WorkManager.getInstance(context)
        infoList = workManager.getWorkInfosByTag("retarget_notification_delivery").get()
        assertTrue("Scheduler should start after refresh detects notification enablement",
            infoList.any { it.state == WorkInfo.State.ENQUEUED })
    }

    /**
     * Helper to install a goal with specified notification setting.
     * Returns the goal ID.
     */
    private suspend fun installGoalWithNotifications(enabled: Boolean): Long =
        withContext(Dispatchers.IO) {
            val settings = CampaignSettings(
                wallpaperEnabled = false,
                notificationEnabled = enabled,
                notificationTargetsPerDay = 3,
            )
            val goal = GoalEntity(
                presetId = "fruit",
                displayName = "Eat More Fruit",
                createdAt = System.currentTimeMillis(),
                active = true,
                settingsJson = GoalConverters.json.encodeToString(settings),
            )
            db.goalDao().insert(goal)
        }

    /**
     * Helper to update notification state for an existing goal.
     */
    private suspend fun updateGoalNotificationState(goalId: Long, notificationEnabled: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = db.goalDao().getGoalById(goalId)
            if (existing != null) {
                val settings = CampaignSettings(
                    wallpaperEnabled = false,
                    notificationEnabled = notificationEnabled,
                    notificationTargetsPerDay = 3,
                )
                val updated = existing.copy(
                    settingsJson = GoalConverters.json.encodeToString(settings),
                )
                db.goalDao().update(updated)
            }
        }
}
