/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.retarget.goal.GoalDatabase
import com.retarget.goal.GoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the M3.3 gatekeeper blocker B1:
 *
 * A goal with ONLY the lock-screen ticker enabled (notificationEnabled=false,
 * tickerEnabled=true) — exactly the configuration the onboarding opt-in
 * (PHASE3-AGENCY.md §8 decision #4) creates — must keep the global
 * NotificationDeliveryWorker scheduled. Previously handleGoalUpdate consulted
 * only notificationEnabled, so ticker-only goals cancelled all delivery work
 * and the ticker could never fire (gatekeeper review, 2026-10-09).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TickerOnlySchedulingTest {

    private lateinit var context: Context
    private lateinit var db: GoalDatabase
    private lateinit var repo: GoalRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(context, GoalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = GoalRepository(db.goalDao())
    }

    @After
    fun tearDown() {
        db.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
        Dispatchers.resetMain()
    }

    private fun workStates(): List<WorkInfo.State> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(NotificationSchedulerManager.WORK_TAG)
            .get()
            .map { it.state }

    /** Waits until the manager's own SupervisorJob scope settles its enqueues. */
    private fun awaitWorkerState(expectedScheduled: Boolean): List<WorkInfo.State> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val states = workStates()
            val scheduled = states.isNotEmpty() && states.all { !it.isFinished }
            if (scheduled == expectedScheduled) return states
            Thread.sleep(50)
        }
        return workStates()
    }

    @Test
    fun `ticker-only goal keeps delivery worker scheduled`() = runTest(testDispatcher) {
        // The onboarding opt-in shape: preset defaults leave notificationEnabled
        // false; the user ticks the ticker box; installPreset applies ticker on top.
        repo.installPreset("hydration", nowMs = 0L, tickerEnabled = true)

        val manager = NotificationSchedulerManager(context, repo)
        manager.startMonitoring()
        try {
            val states = awaitWorkerState(expectedScheduled = true)
            assertTrue(
                "ticker-only goal must keep the delivery worker scheduled, got: $states",
                states.isNotEmpty() && states.all { !it.isFinished },
            )
        } finally {
            manager.stopMonitoring()
        }
    }

    @Test
    fun `no channel-enabled goals cancels delivery worker`() = runTest(testDispatcher) {
        // Preset default: both notification and ticker off.
        repo.installPreset("hydration", nowMs = 0L)

        val manager = NotificationSchedulerManager(context, repo)
        manager.startMonitoring()
        try {
            val states = awaitWorkerState(expectedScheduled = false)
            assertTrue(
                "all-disabled goal must not schedule delivery work, got: $states",
                states.isEmpty() || states.all { it.isFinished },
            )
        } finally {
            manager.stopMonitoring()
        }
    }
}
