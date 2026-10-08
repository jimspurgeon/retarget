/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import com.retarget.creative.Channel
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-only mapping tests for [WidgetStateComposer]: pure function input →
 * output pins, no Robolectric (no Android framework types touched).
 */
class WidgetStateComposerTest {

    private fun goal(settingsJson: String) =
        GoalEntity(
            id = 1L,
            presetId = "hydration",
            displayName = "Drink water",
            createdAt = 0L,
            active = true,
            settingsJson = settingsJson,
        )

    private fun settingsJson(
        wallpaperEnabled: Boolean = true,
        notificationEnabled: Boolean = false,
        wallpaperTargetsPerDay: Int = 2,
        notificationTargetsPerDay: Int = 1,
    ): String {
        val settings =
            CampaignSettings(
                wallpaperEnabled = wallpaperEnabled,
                notificationEnabled = notificationEnabled,
                wallpaperTargetsPerDay = wallpaperTargetsPerDay,
                notificationTargetsPerDay = notificationTargetsPerDay,
            )
        return kotlinx.serialization.json.Json.encodeToString(
            CampaignSettings.serializer(),
            settings,
        )
    }

    @Test
    fun `null goal maps to Empty`() {
        val snapshot = WidgetStateComposer.compose(null, emptyMap(), 0, null)
        assertTrue(snapshot is WidgetSnapshot.Empty)
    }

    @Test
    fun `active goal with mixed counts maps fields correctly`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal(settingsJson(notificationEnabled = true)),
                exposuresToday =
                    mapOf(
                        Channel.WALLPAPER to 2,
                        Channel.NOTIFICATION to 1,
                    ),
                checkInsToday = 1,
                latestCreativePath = "/cache/creatives/water-01.png",
            )
        val active = snapshot as WidgetSnapshot.Active
        assertEquals("Drink water", active.goalDisplayName)
        assertEquals(2, active.wallpaperShownToday)
        assertEquals(2, active.wallpaperTargetToday)
        assertEquals(1, active.notificationShownToday)
        assertEquals(1, active.notificationTargetToday)
        assertEquals("/cache/creatives/water-01.png", active.creativeImagePath)
        assertTrue(active.checkedInToday)
    }

    @Test
    fun `disabled channels show zero shown and zero target`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal(settingsJson(wallpaperEnabled = false, notificationEnabled = false)),
                exposuresToday =
                    mapOf(
                        Channel.WALLPAPER to 3, // would display if enabled
                        Channel.NOTIFICATION to 2,
                    ),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        val active = snapshot as WidgetSnapshot.Active
        assertEquals(0, active.wallpaperShownToday)
        assertEquals(0, active.wallpaperTargetToday)
        assertEquals(0, active.notificationShownToday)
        assertEquals(0, active.notificationTargetToday)
    }

    @Test
    fun `missing channel entry in exposures map counts as zero`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal(settingsJson(notificationEnabled = true)),
                exposuresToday = mapOf(Channel.WALLPAPER to 1), // NOTIFICATION absent
                checkInsToday = 0,
                latestCreativePath = null,
            )
        val active = snapshot as WidgetSnapshot.Active
        assertEquals(0, active.notificationShownToday)
        assertEquals(1, active.notificationTargetToday)
    }

    @Test
    fun `null creative path passes through as null`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal(settingsJson()),
                exposuresToday = emptyMap(),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        assertNull((snapshot as WidgetSnapshot.Active).creativeImagePath)
    }

    @Test
    fun `checkedInToday false when checkInsToday is zero`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal(settingsJson()),
                exposuresToday = emptyMap(),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        assertFalse((snapshot as WidgetSnapshot.Active).checkedInToday)
    }

    @Test
    fun `checkedInToday true when checkInsToday is positive`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal(settingsJson()),
                exposuresToday = emptyMap(),
                checkInsToday = 2,
                latestCreativePath = null,
            )
        assertTrue((snapshot as WidgetSnapshot.Active).checkedInToday)
    }

    @Test
    fun `malformed settingsJson yields Error state without throwing`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal("{not-valid-json"),
                exposuresToday = mapOf(Channel.WALLPAPER to 1),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        assertEquals(
            WidgetSnapshot.Error(ErrorReason.SettingsUnparseable),
            snapshot,
        )
    }

    @Test
    fun `out-of-range targets in settingsJson yields Error state`() {
        // Valid JSON, but violates CampaignSettings' init-block range invariant.
        val snapshot =
            WidgetStateComposer.compose(
                goal = goal("""{"wallpaperTargetsPerDay":99,"notificationTargetsPerDay":0}"""),
                exposuresToday = emptyMap(),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        assertEquals(
            WidgetSnapshot.Error(ErrorReason.SettingsUnparseable),
            snapshot,
        )
    }
}
