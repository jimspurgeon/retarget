/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import com.retarget.creative.Channel
import com.retarget.goal.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the M3.2 glance state mapping acceptance criterion:
 * snapshot → UI text ([WidgetUiTextMapper]) and repository data → snapshot
 * ([WidgetStateComposer]). Pure JVM — no Robolectric needed since the mapper
 * takes resolved strings, keeping the Glance runtime out of the test.
 */
class WidgetUiTextMapperTest {
    private val strings =
        object : WidgetUiTextMapper.Strings {
            override val emptyTitle = "No campaign yet"
            override val emptyHint = "Add a goal to see it here"
            override val errorMessage = "Couldn't load your campaign"
            override val checkInButton = "Check in"
            override val checkInDoneButton = "Checked in ✓"
            override val pacingFormat = "%1\$d of %2\$d nudges"
        }

    // ---- Empty state ----

    @Test
    fun `empty snapshot maps to onboarding headline and hint`() {
        val ui = WidgetUiTextMapper.snapshotToUiText(WidgetSnapshot.Empty, strings)
        assertEquals("No campaign yet", ui.headline)
        assertEquals("Add a goal to see it here", ui.body)
        assertNull("Empty state must not offer check-in", ui.checkInLabel)
    }

    // ---- Error state ----

    @Test
    fun `error snapshot maps to error message without check-in`() {
        val ui = WidgetUiTextMapper.snapshotToUiText(WidgetSnapshot.Error(ErrorReason.SettingsUnparseable), strings)
        assertEquals("Couldn't load your campaign", ui.headline)
        assertNull(ui.body)
        assertNull("Error state must not offer check-in", ui.checkInLabel)
    }

    // ---- Active state: pacing aggregation across ENABLED channels ----

    @Test
    fun `active snapshot aggregates pacing across both enabled channels`() {
        val active =
            WidgetSnapshot.Active(
                goalDisplayName = "Hydration",
                wallpaperShownToday = 2,
                wallpaperTargetToday = 4,
                notificationShownToday = 1,
                notificationTargetToday = 3,
                creativeImagePath = null,
                checkedInToday = false,
            )
        val ui = WidgetUiTextMapper.snapshotToUiText(active, strings, wallpaperEnabled = true, notificationEnabled = true)
        assertEquals("Hydration", ui.headline)
        assertEquals("3 of 7 nudges", ui.body)
        assertEquals("Check in", ui.checkInLabel)
    }

    @Test
    fun `active snapshot excludes disabled channels from pacing`() {
        val active =
            WidgetSnapshot.Active(
                goalDisplayName = "Hydration",
                wallpaperShownToday = 2,
                wallpaperTargetToday = 4,
                notificationShownToday = 1,
                notificationTargetToday = 3,
                creativeImagePath = null,
                checkedInToday = false,
            )
        // Notification disabled: pacing must count wallpaper only.
        val ui = WidgetUiTextMapper.snapshotToUiText(active, strings, wallpaperEnabled = true, notificationEnabled = false)
        assertEquals("2 of 4 nudges", ui.body)
    }

    @Test
    fun `all channels disabled shows zero pacing`() {
        val active =
            WidgetSnapshot.Active(
                goalDisplayName = "Hydration",
                wallpaperShownToday = 2,
                wallpaperTargetToday = 4,
                notificationShownToday = 1,
                notificationTargetToday = 3,
                creativeImagePath = null,
                checkedInToday = false,
            )
        val ui = WidgetUiTextMapper.snapshotToUiText(active, strings, wallpaperEnabled = false, notificationEnabled = false)
        assertEquals("0 of 0 nudges", ui.body)
    }

    @Test
    fun `checked in today swaps button label`() {
        val active =
            WidgetSnapshot.Active(
                goalDisplayName = "Hydration",
                wallpaperShownToday = 0,
                wallpaperTargetToday = 4,
                notificationShownToday = 0,
                notificationTargetToday = 0,
                creativeImagePath = null,
                checkedInToday = true,
            )
        val ui = WidgetUiTextMapper.snapshotToUiText(active, strings)
        assertEquals("Checked in ✓", ui.checkInLabel)
    }

    @Test
    fun `blank goal name falls back to empty title`() {
        val active =
            WidgetSnapshot.Active(
                goalDisplayName = "",
                wallpaperShownToday = 0,
                wallpaperTargetToday = 4,
                notificationShownToday = 0,
                notificationTargetToday = 0,
                creativeImagePath = null,
                checkedInToday = false,
            )
        val ui = WidgetUiTextMapper.snapshotToUiText(active, strings)
        assertEquals("No campaign yet", ui.headline)
    }

    // ---- Composer seam (shared state layer, pinned here for the renderer) ----

    private fun goal(settingsJson: String) =
        GoalEntity(
            id = 1,
            presetId = "hydration",
            displayName = "Hydration",
            createdAt = 0,
            active = true,
            settingsJson = settingsJson,
        )

    private val bothChannelsJson =
        """{"wallpaperEnabled":true,"notificationEnabled":true,"wallpaperTargetsPerDay":4,"notificationTargetsPerDay":3}"""

    @Test
    fun `composer maps null goal to Empty`() {
        val snapshot = WidgetStateComposer.compose(null, emptyMap(), 0, null)
        assertTrue(snapshot is WidgetSnapshot.Empty)
    }

    @Test
    fun `composer maps mixed counts into Active fields`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal(bothChannelsJson),
                mapOf(Channel.WALLPAPER to 2, Channel.NOTIFICATION to 1),
                checkInsToday = 2,
                latestCreativePath = "/cache/img.jpg",
            )
        val active = snapshot as WidgetSnapshot.Active
        assertEquals("Hydration", active.goalDisplayName)
        assertEquals(2, active.wallpaperShownToday)
        assertEquals(4, active.wallpaperTargetToday)
        assertEquals(1, active.notificationShownToday)
        assertEquals(3, active.notificationTargetToday)
        assertEquals("/cache/img.jpg", active.creativeImagePath)
        assertTrue(active.checkedInToday)
    }

    @Test
    fun `composer zeroes disabled channel counts`() {
        val json =
            """{"wallpaperEnabled":false,"notificationEnabled":true,"wallpaperTargetsPerDay":4,"notificationTargetsPerDay":3}"""
        val snapshot =
            WidgetStateComposer.compose(
                goal(json),
                mapOf(Channel.WALLPAPER to 2, Channel.NOTIFICATION to 1),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        val active = snapshot as WidgetSnapshot.Active
        assertEquals("Disabled wallpaper count must be zeroed", 0, active.wallpaperShownToday)
        assertEquals(1, active.notificationShownToday)
        assertFalse(active.checkedInToday)
        assertNull("Null creative path must pass through", active.creativeImagePath)
    }

    @Test
    fun `composer tolerates malformed settings without throwing`() {
        val snapshot =
            WidgetStateComposer.compose(
                goal("{not json"),
                emptyMap(),
                checkInsToday = 0,
                latestCreativePath = null,
            )
        assertTrue("Malformed settings must degrade to Error, not throw", snapshot is WidgetSnapshot.Error)
    }
}
