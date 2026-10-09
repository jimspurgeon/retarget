/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the M3.3 quiet-hours cancel fix (PHASE3-AGENCY.md §4).
 *
 * NotificationDeliveryWorker.doWork early-returns during quiet hours using
 * BudgetPolicy.isQuietHour() and cancels showing tickers before returning
 * (the ticker is an ONGOING notification that would otherwise linger on the
 * lock screen through the night). The worker itself is Android-bound
 * (CoroutineWorker + Room database + NotificationManager), so a direct unit
 * test of doWork would need heavy WorkManager/Robolectric mocking that mostly
 * tests mocks, not logic. These tests instead pin the two pure predicates
 * doWork's quiet path relies on:
 *
 *   1. BudgetPolicy.isQuietHour is the single source of truth for quiet hours
 *      (no secondhand wallpaper-policy wrapper that can drift).
 *   2. The cancel sweep covers the whole wrap-midnight window — a ticker
 *      delivered at 21:00 is cancelled at every hour through 07:00, because
 *      isQuietHour holds across that span (the "lingering ticker" bug).
 */
class QuietHoursCancelTest {
    @Test
    fun `isQuietHour matches the policy floor used everywhere`() {
        // The delivery worker's precheck is BudgetPolicy.isQuietHour(hour);
        // assert the wrapping-midnight default window directly.
        val quiet = listOf(22, 23, 0, 1, 2, 3, 4, 5, 6)
        val waking = listOf(7, 8, 12, 18, 21)
        quiet.forEach { h ->
            assertTrue("hour $h should be quiet (22:00–07:00 default)", BudgetPolicy.isQuietHour(h))
        }
        waking.forEach { h ->
            assertFalse("hour $h should NOT be quiet", BudgetPolicy.isQuietHour(h))
        }
    }

    @Test
    fun `a ticker delivered at 21h is inside quiet hours at every later cancel sweep`() {
        // Regression shape of the lingering-ticker bug: a ticker delivered at
        // 21:00 (allowed — not yet quiet) must be cancelled by the periodic
        // worker whenever it next fires during the quiet window. Pin the
        // full span of hours in which the cancel sweep executes (i.e. in
        // which isQuietHour forces the early return + cancel path).
        (22..23).plus(0..6).forEach { h ->
            assertTrue(
                "worker firing at hour $h must take the quiet-hours cancel path",
                BudgetPolicy.isQuietHour(h),
            )
        }
    }
}
