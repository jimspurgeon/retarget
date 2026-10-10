/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.notification

import androidx.test.core.app.ApplicationProvider
import com.retarget.goal.GoalEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [BundledPackSource] pack-theme coverage (#33): every bundled
 * creative pack must be mapped to the goal theme it serves, so no preset's
 * goals hit the empty-candidates path.
 *
 * The worker's empty-candidates behavior (skip with success, not retry) is
 * Android-bound (CoroutineWorker + Room); per the QuietHoursCancelTest
 * precedent, this suite pins the pure predicate the worker's dispatch relies
 * on instead: every bundled asset pack is reachable through packThemes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BundledPackSourceTest {

    private fun hydrationGoal() = GoalEntity(
        id = 1L,
        presetId = "hydration",
        displayName = "Hydration",
        createdAt = 0L,
        settingsJson = kotlinx.serialization.json.Json.encodeToString(
            com.retarget.goal.CampaignSettings(
                wallpaperTargetsPerDay = 4,
                notificationTargetsPerDay = 2,
            ),
        ),
    )

    @Test
    fun `hydration preset resolves creative candidates from the bundled pack`() {
        val source = BundledPackSource(ApplicationProvider.getApplicationContext())
        val candidates = source.creativesFor(listOf(hydrationGoal()))
        assertTrue(
            "hydration goals must map to the bundled hydration pack; empty candidates caused the #33 retry loop",
            candidates.isNotEmpty(),
        )
    }
}
