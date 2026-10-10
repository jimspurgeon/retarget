/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.wallpaper

import androidx.test.core.app.ApplicationProvider
import com.retarget.goal.GoalEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [BundledPackSource] pack-theme coverage in the wallpaper worker
 * (#33): the hydration pack must be mapped here too, mirroring the
 * notification worker's copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BundledPackSourceWallpaperTest {

    @Test
    fun `hydration preset resolves creative candidates from the bundled pack`() {
        val source = BundledPackSource(ApplicationProvider.getApplicationContext())
        val goal = GoalEntity(
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
        val candidates = source.creativesFor(listOf(goal))
        assertTrue(
            "wallpaper worker's hydration goals must map to the bundled hydration pack (#33)",
            candidates.isNotEmpty(),
        )
    }
}
