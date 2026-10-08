/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [SnoozeSuppression] — the persistence layer that makes the
 * notification "Snooze 2h" action effective against the consolidated global
 * delivery worker (v0.3.x notification-scheduling hotfix).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnoozeSuppressionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SnoozeSuppression.clearAll(context)
    }

    @Test
    fun `goal is snoozed for full 120 minute window`() {
        val now = System.currentTimeMillis()
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 120 * 60 * 1000L)

        // Throughout the 120-minute window the goal stays suppressed...
        assertTrue(SnoozeSuppression.isSnoozed(context, 5L, now + 60 * 60 * 1000L))
        assertTrue(SnoozeSuppression.isSnoozed(context, 5L, now + 119 * 60 * 1000L))
        assertTrue(SnoozeSuppression.isSnoozed(context, 5L, now + 120 * 60 * 1000L - 1))
        // ...and at the exact boundary the window has fully elapsed.
        assertFalse(SnoozeSuppression.isSnoozed(context, 5L, now + 120 * 60 * 1000L))
    }

    @Test
    fun `snooze expires after window elapses`() {
        val now = System.currentTimeMillis()
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 120 * 60 * 1000L)

        // One ms past the window: not snoozed.
        assertFalse(SnoozeSuppression.isSnoozed(context, 5L, now + 120 * 60 * 1000L + 1))
    }

    @Test
    fun `snooze is per goal`() {
        val now = System.currentTimeMillis()
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 120 * 60 * 1000L)

        assertTrue(SnoozeSuppression.isSnoozed(context, 5L, now + 1))
        assertFalse(SnoozeSuppression.isSnoozed(context, 6L, now + 1))
    }

    @Test
    fun `unsnoozed goal reads false and leaves no state`() {
        assertFalse(SnoozeSuppression.isSnoozed(context, 99L, System.currentTimeMillis()))
    }

    @Test
    fun `later snooze overwrites earlier one`() {
        val now = System.currentTimeMillis()
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 30 * 60 * 1000L)
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 120 * 60 * 1000L)

        // The extended window governs: 60 min in, still snoozed.
        assertTrue(SnoozeSuppression.isSnoozed(context, 5L, now + 60 * 60 * 1000L))
    }

    @Test
    fun `expired snooze is lazily cleared`() {
        val now = System.currentTimeMillis()
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 1000L)

        // Read past expiry — clears the entry.
        assertFalse(SnoozeSuppression.isSnoozed(context, 5L, now + 2000L))

        // The prefs file no longer holds the key.
        val prefs = context.getSharedPreferences(SnoozeSuppression.FILE_NAME, Context.MODE_PRIVATE)
        assertFalse(prefs.contains("snooze_goal_5"))
    }

    @Test
    fun `clear removes a single goal snooze`() {
        val now = System.currentTimeMillis()
        SnoozeSuppression.snooze(context, goalId = 5L, untilMs = now + 60 * 60 * 1000L)
        SnoozeSuppression.snooze(context, goalId = 6L, untilMs = now + 60 * 60 * 1000L)

        SnoozeSuppression.clear(context, 5L)

        assertFalse(SnoozeSuppression.isSnoozed(context, 5L, now + 1))
        assertTrue(SnoozeSuppression.isSnoozed(context, 6L, now + 1))
    }
}
