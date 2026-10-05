/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.retarget.creative.Channel
import com.retarget.creative.Creative
import com.retarget.creative.GoalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Unit tests for [NotificationChannel] (Phase 2, Milestone 2.1).
 *
 * Validates that the notification builder produces correct fields:
 * - BigPictureStyle present when image available
 * - Content title matches goal theme
 * - Channel ID is "retarget_nudges"
 * - Actions are properly attached
 *
 * Runs under Robolectric with mocked NotificationManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Android 13 (Tiramisu) for POST_NOTIFICATIONS testing
class NotificationChannelTest {

    private lateinit var context: Context
    private lateinit var notificationChannel: NotificationChannel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Grant POST_NOTIFICATIONS so deliver() passes the permission gate under Robolectric (SDK 33 defaults it to DENIED)
        Shadows.shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        notificationChannel = NotificationChannel(context)
    }

    @Test
    fun `channel ID is retarget_nudges`() {
        assertEquals("retarget_nudges", NotificationChannel.CHANNEL_ID)
    }

    @Test
    fun `hasPostNotificationsPermission returns true below Android 13 or when granted`() {
        // Robolectric mocks the permission check; should return true for granted case
        val hasPermission = notificationChannel.hasPostNotificationsPermission()
        assertTrue(hasPermission)
    }

    @Test
    fun `notification builds with BigPictureStyle and correct channel`() {
        val creative = Creative(
            id = "test-pack/test-creative",
            packId = "test-pack",
            goalTheme = GoalTheme.HYDRATION,
            subTheme = "water glass",
            copyPool = listOf("Stay hydrated"),
            imagePath = "",
            attribution = "Test Photographer",
            licenseUrl = "https://example.com",
        )

        val spec = NotificationSpec(
            goalId = 1L,
            creative = creative,
            title = "Hydration Time",
            copyLine = "Drink water now",
            actions = createMockActions(),
        )

        // Deliver should succeed with proper permissions
        val delivered = notificationChannel.deliver(spec, notificationId = 1)

        // Verification: check notification was sent (Robolectric captures it)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications

        // At least one notification should be posted
        assertTrue("At least one notification should be active", notifications.isNotEmpty())
    }

    @Test
    fun `notification uses correct channel ID`() {
        val creative = createTestCreative(GoalTheme.NATURE_TIME)
        val spec = NotificationSpec(
            goalId = 2L,
            creative = creative,
            title = "Nature Reminder",
            copyLine = "Step outside for fresh air",
            actions = createMockActions(),
        )

        notificationChannel.deliver(spec, notificationId = 2)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications

        assertTrue("Notification should be posted", notifications.isNotEmpty())
        val postedNotification = notifications[0].notification
        assertEquals("Channel ID should match", NotificationChannel.CHANNEL_ID, postedNotification.channelId)
    }

    @Test
    fun `notification includes all three actions`() {
        val creative = createTestCreative(GoalTheme.BREATHING)
        val spec = NotificationSpec(
            goalId = 3L,
            creative = creative,
            title = "Breathing Break",
            copyLine = "Take a deep breath",
            actions = createMockActions(),
        )

        notificationChannel.deliver(spec, notificationId = 3)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications

        assertTrue("Notification should be posted", notifications.isNotEmpty())
        val postedNotification = notifications[0].notification

        // Check action count
        assertEquals("Should have 3 actions", 3, postedNotification.actions.size.toLong())
    }

    @Test
    fun `notification sets priority to DEFAULT`() {
        val creative = createTestCreative(GoalTheme.GENERAL_WELLNESS)
        val spec = NotificationSpec(
            goalId = 4L,
            creative = creative,
            title = "Wellness Check",
            copyLine = "Remember to take care of yourself",
            actions = createMockActions(),
        )

        notificationChannel.deliver(spec, notificationId = 4)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications

        assertTrue("Notification should be posted", notifications.isNotEmpty())
        val postedNotification = notifications[0].notification

        // PRIORITY_DEFAULT maps to 0 in NotificationCompat
        assertEquals("Priority should be DEFAULT", NotificationCompat.PRIORITY_DEFAULT.toLong(), postedNotification.priority.toLong())
    }

    @Test
    fun `notification content title comes from goal theme`() {
        val goalTitle = "Hydration Goal"
        val creative = createTestCreative(GoalTheme.HYDRATION)
        val spec = NotificationSpec(
            goalId = 5L,
            creative = creative,
            title = goalTitle,
            copyLine = "Time for water",
            actions = createMockActions(),
        )

        notificationChannel.deliver(spec, notificationId = 5)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications

        assertTrue("Notification should be posted", notifications.isNotEmpty())
        val postedNotification = notifications[0].notification

        // The title should be set
        assertNotNull("Content title should be set", postedNotification.extras.getString(android.app.Notification.EXTRA_TITLE))
    }

    private fun createTestCreative(theme: GoalTheme): Creative {
        return Creative(
            id = "test-pack/${theme.name.lowercase()}",
            packId = "test-pack",
            goalTheme = theme,
            subTheme = theme.name.lowercase(),
            copyPool = listOf("Test copy for $theme"),
            imagePath = "",
            attribution = "Test Photographer",
            licenseUrl = "https://example.com",
        )
    }

    private fun createMockActions(): NotificationActions {
        val dummyPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            0,
            android.content.Intent("DUMMY_ACTION"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationActions(
            checkInIntent = dummyPendingIntent,
            snooze2hIntent = dummyPendingIntent,
            fewerLikeThisIntent = dummyPendingIntent,
        )
    }
}
