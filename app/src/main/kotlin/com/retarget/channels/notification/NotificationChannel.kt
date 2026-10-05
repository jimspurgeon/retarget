/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.retarget.creative.CreativeImageCache

/**
 * Delivers nudge notifications via BigPictureStyle (Phase 2, Milestone 2.1).
 *
 * Implements the notification channel design from PHASE2-CAMPAIGN.md §2.2:
 * - Single channel "retarget_nudges" with goal-themed notification titles
 * - IMPORTANCE_DEFAULT (no heads-up interruptions)
 * - BigPictureStyle for visual-first, copy-light UX (DEVELOPMENT.md §UX Principles)
 * - Three actions: check-in, snooze 2h, fewer like this
 *
 * This class is a "dumb pipe" per the architecture principle in DEVELOPMENT.md:
 * the scheduler decides what and when, this class only executes delivery.
 *
 * Thread safety: all methods expect caller to be off the main thread (disk I/O
 * for image decoding). Callers on IO are responsible for posting results back
 * to main if UI updates are needed.
 */
class NotificationChannel(
    private val context: Context,
) {
    private val notificationManager: NotificationManager =
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: throw IllegalStateException("NotificationManager not available")

    init {
        createNotificationChannel()
    }

    /**
     * Creates the single notification channel for all retarget nudges.
     *
     * Design decision (logged per AGENTS.md §5.1.1): single channel over
     * per-goal channels to reduce user confusion ("why so many Retarget channels?").
     * See PHASE2-CAMPAIGN.md Risk 3 Option B.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Retarget Nudges",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Goal-focused reminders with visual cues"
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Notification channel created: $CHANNEL_ID")
    }

    /**
     * Checks if POST_NOTIFICATIONS permission is granted (Android 13+).
     *
     * @return true if permission granted or below Android 13 (where not required).
     */
    fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Below Android 13, permission not required
        }
    }

    /**
     * Delivers a notification nudge for the given [spec].
     *
     * @param spec The notification specification containing creative, copy, and actions.
     * @param notificationId Unique ID for this notification (use goalId + timestamp).
     *
     * @return true if the notification was posted successfully.
     */
    fun deliver(spec: NotificationSpec, notificationId: Int): Boolean {
        if (!hasPostNotificationsPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied; cannot deliver nudge for goal ${spec.goalId}")
            return false
        }

        val imageFile = CreativeImageCache.cachedFileFor(spec.creative, context)
        val largeBitmap = imageFile?.let { file ->
            if (file.isFile) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } ?: run {
            Log.w(TAG, "Creative image unavailable: ${spec.creative.imagePath}")
            null
        }

        val notification = buildNotification(spec, largeBitmap)
        try {
            notificationManager.notify(notificationId, notification)
            Log.i(TAG, "Notification delivered: goalId=${spec.goalId}, creative=${spec.creative.id}")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver notification for goal ${spec.goalId}", e)
            return false
        }
    }

    /**
     * Builds the notification with BigPictureStyle.
     *
     * Follows UX principle "visual-first, copy-light" from DEVELOPMENT.md:
     * - Large bitmap from creative image (or fallback if unavailable)
     * - Content title = goal theme (from Creative.goalTheme)
     * - Content text = copy line from spec
     * - BigPictureStyle for immersive visual impact
     */
    private fun buildNotification(
        spec: NotificationSpec,
        largeBitmap: android.graphics.Bitmap?,
    ): Notification {
        val bigPictureStyle = android.app.Notification.BigPictureStyle()
            .setLargeIcon(largeBitmap ?: defaultLargeIcon())
            .bigPicture(largeBitmap)
            .setSummaryText(spec.copyLine)
            .setContentTitle(spec.title)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(spec.title)
            .setContentText(spec.copyLine)
            .setStyle(bigPictureStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(buildCheckInAction(spec.actions.checkInIntent))
            .addAction(buildSnoozeAction(spec.actions.snooze2hIntent))
            .addAction(buildFewerAction(spec.actions.fewerLikeThisIntent))
            .build()
    }

    private fun defaultLargeIcon(): android.graphics.drawable.Icon {
        return android.graphics.drawable.Icon.createWithResource(
            context,
            android.R.drawable.ic_menu_gallery,
        )
    }

    private fun buildCheckInAction(pendingIntent: PendingIntent): NotificationCompat.Action {
        return NotificationCompat.Action(
            android.R.drawable.ic_media_play,
            "Check in",
            pendingIntent,
        )
    }

    private fun buildSnoozeAction(pendingIntent: PendingIntent): NotificationCompat.Action {
        return NotificationCompat.Action(
            android.R.drawable.ic_menu_recent_history,
            "Snooze 2h",
            pendingIntent,
        )
    }

    private fun buildFewerAction(pendingIntent: PendingIntent): NotificationCompat.Action {
        return NotificationCompat.Action(
            android.R.drawable.ic_delete,
            "Fewer like this",
            pendingIntent,
        )
    }

    companion object {
        private const val TAG = "NotificationChannel"
        const val CHANNEL_ID = "retarget_nudges"
    }
}
