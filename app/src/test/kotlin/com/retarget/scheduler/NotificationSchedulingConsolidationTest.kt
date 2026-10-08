/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.retarget.channels.notification.NotificationDeliveryWorker
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the v0.3.x notification-scheduling consolidation:
 *
 * 1. [NotificationScheduler.scheduleNotificationForGoal] is now a no-op shim —
 *    it must NOT enqueue any periodic work.
 * 2. [NotificationSchedulerManager.sweepLegacyGoalWork] cancels legacy per-goal
 *    periodic work enqueued under the shared `retarget_notification_delivery` tag.
 * 3. After the sweep, only the single global periodic worker remains.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationSchedulingConsolidationTest {

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

    /** Enqueues legacy-style per-goal periodic work exactly as v0.3.x did. */
    private fun enqueueLegacyGoalWork(goalId: Long) {
        val workTag = "${NotificationScheduler.LEGACY_WORK_TAG_PREFIX}${goalId}"
        val request =
            PeriodicWorkRequestBuilder<NotificationDeliveryWorker>(1, TimeUnit.HOURS)
                .addTag(workTag)
                .addTag(NotificationSchedulerManager.WORK_TAG)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(workTag, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun workStatesByTag(tag: String): List<WorkInfo.State> =
        WorkManager.getInstance(context)
            .getWorkInfosByTag(tag).get()
            .map { it.state }

    private fun activeStates(states: List<WorkInfo.State>): List<WorkInfo.State> =
        states.filter { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.RUNNING }

    @Test
    fun `scheduleNotificationForGoal no longer enqueues per-goal periodic work`() {
        // Shim must be callable (compile compatibility) but enqueue nothing.
        NotificationScheduler.scheduleNotificationForGoal(context, goalId = 42L)

        val workManager = WorkManager.getInstance(context)
        val allWork = workManager.getWorkInfosForUniqueWork(
            "${NotificationScheduler.LEGACY_WORK_TAG_PREFIX}42",
        ).get()
        assertTrue(
            "Legacy per-goal unique work must not exist after shim call",
            activeStates(allWork.map { it.state }).isEmpty(),
        )
    }

    @Test
    fun `sweep cancels legacy per-goal work`() {
        // Simulate an upgraded install: legacy per-goal work for goals 1, 2, 3.
        enqueueLegacyGoalWork(1L)
        enqueueLegacyGoalWork(2L)
        enqueueLegacyGoalWork(3L)

        val wm = WorkManager.getInstance(context)
        assertTrue(
            "Sanity: legacy goal work should be enqueued before sweep",
            activeStates(workStatesByTag("${NotificationScheduler.LEGACY_WORK_TAG_PREFIX}1")).isNotEmpty(),
        )

        NotificationSchedulerManager.sweepLegacyGoalWork(context)

        // Unique-name lookups now show cancelled work only.
        listOf(1L, 2L, 3L).forEach { goalId ->
            val states = wm.getWorkInfosForUniqueWork(
                "${NotificationScheduler.LEGACY_WORK_TAG_PREFIX}$goalId",
            ).get().map { it.state }
            assertTrue(
                "Legacy work for goal $goalId must be cancelled",
                states.all { it == WorkInfo.State.CANCELLED },
            )
        }
    }

    @Test
    fun `after sweep only the single global delivery worker remains`() {
        enqueueLegacyGoalWork(1L)
        enqueueLegacyGoalWork(2L)

        // Sweep (as startMonitoring performs on first run after update)...
        NotificationSchedulerManager.sweepLegacyGoalWork(context)

        // ...then the manager (re)enqueues the single global worker.
        NotificationSchedulerManager.scheduleNotificationDelivery(context)

        val wm = WorkManager.getInstance(context)

        // Exactly one ENQUEUED/RUNNING periodic work may exist for the whole
        // delivery pipeline: the global unique work.
        val globalStates = wm.getWorkInfosByTag(NotificationSchedulerManager.WORK_TAG).get()
        val activeGlobal = globalStates.count {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }
        assertTrue(
            "Global worker must be enqueued",
            globalStates.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING },
        )
        assertTrue(
            "Exactly one active periodic worker allowed (got $activeGlobal)",
            activeGlobal == 1,
        )

        // Legacy per-goal work: all cancelled.
        listOf(1L, 2L).forEach { goalId ->
            val states = wm.getWorkInfosForUniqueWork(
                "${NotificationScheduler.LEGACY_WORK_TAG_PREFIX}$goalId",
            ).get().map { it.state }
            assertTrue(
                "Legacy work for goal $goalId must be cancelled",
                states.all { it == WorkInfo.State.CANCELLED },
            )
        }
    }
}
