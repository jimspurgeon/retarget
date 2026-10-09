/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Delivers the lock-screen ticker nudge (M3.3, PHASE3-AGENCY.md §4).
 *
 * Implements the "goal ticker" design from docs/research/android-platform.md §6:
 * a persistent low-priority ongoing notification acting as an always-available
 * lock-screen glance surface. Channel characteristics:
 *
 *   - Separate Android notification channel "retarget_ticker", IMPORTANCE_LOW
 *   - No heads-up, no sound, no vibration (importance low handles this; also
 *     explicitly disabled on the notification builder)
 *   - Ongoing (setOngoing(true)) — one ticker replaces the previous via a
 *     stable per-goal notification id
 *   - Lock-screen visible: VISIBILITY_PUBLIC so it appears on the lock screen
 *
 * This class is a "dumb pipe" like [NotificationChannel]: the scheduler decides
 * what and when, this class only executes delivery.
 *
 * Copy note (logged decision per the M3.3 dispatch spec): ticker copy is
 * hardcoded here mirroring the NotificationChannel.kt precedent ("Check in",
 * "Snooze 2h"); routing through com.retarget strings is owned by the W2/UI
 * worker. Framing stays neutral and factual — no fear/shame language
 * (pre-registered decision #5).
 */
class TickerChannel(
    private val context: Context,
) {
    private val notificationManager: NotificationManager =
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: throw IllegalStateException("NotificationManager not available")

    init {
        createNotificationChannel()
    }

    /**
     * Creates the ticker's own Android notification channel, separate from the
     * heads-up nudge channel: users can silence/disable lock-screen presence
     * independently of nudge notifications (AGENTS.md §2 reversibility).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Retarget Lock-Screen Ticker",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Low-key goal ticker shown on the lock screen"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
        Log.d(TAG, "Ticker notification channel created: $CHANNEL_ID")
    }

    /**
     * Checks if POST_NOTIFICATIONS permission is granted (Android 13+).
     * Same guard as [NotificationChannel.hasPostNotificationsPermission].
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
     * Delivers (or replaces) the ticker notification for the given [spec].
     *
     * The [notificationId] is stable per goal (see NotificationDeliveryWorker),
     * so one ticker replaces the previous one instead of stacking.
     *
     * @param spec            the ticker content (goal title + pacing line)
     * @param notificationId  stable per-goal notification id
     * @return true if the notification was posted successfully
     */
    fun deliver(
        spec: TickerSpec,
        notificationId: Int,
    ): Boolean {
        if (!hasPostNotificationsPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission denied; cannot deliver ticker for goal ${spec.goalId}")
            return false
        }

        val notification = buildNotification(spec)
        return try {
            notificationManager.notify(notificationId, notification)
            Log.i(TAG, "Ticker delivered: goalId=${spec.goalId}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver ticker for goal ${spec.goalId}", e)
            false
        }
    }

    /**
     * Removes the ticker notification for the given goal (reversibility:
     * the worker cancels when the ticker's budget is exhausted or on opt-out).
     */
    fun cancel(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * Builds the ongoing lock-screen ticker notification.
     *
     * Minimal and neutral by design: goal title + pacing line, no imagery, no
     * actions beyond a single tap-through check-in (the ticker is a glance
     * surface, not an interruption).
     */
    private fun buildNotification(spec: TickerSpec): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(spec.title)
            .setContentText(spec.pacingLine)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // Silent and non-interrupting by construction (PHASE3-AGENCY.md §4).
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0)
            // Ongoing: the ticker persists on the lock screen until replaced
            // by the next scheduled update or canceled on budget exhaustion.
            .setOngoing(true)
            // Public visibility so the ticker appears on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setContentIntent(spec.tapIntent)
            .build()

    companion object {
        private const val TAG = "TickerChannel"
        const val CHANNEL_ID = "retarget_ticker"
    }
}

/**
 * Content specification for a lock-screen ticker nudge.
 *
 * Carrier from the delivery worker to [TickerChannel]. Deliberately leaner
 * than [NotificationSpec]: the ticker carries text only (no creative image),
 * mirroring the glance-surface design in PHASE3-AGENCY.md §4.
 *
 * @param goalId      the goal this ticker targets
 * @param title       goal display name / preset name
 * @param pacingLine  gentle pacing line, e.g. "Nature time · 2 nudges today"
 * @param tapIntent   optional tap-through PendingIntent (check-in); no other
 *                    actions — the ticker is a glance surface, not a prompt
 */
data class TickerSpec(
    val goalId: Long,
    val title: String,
    val pacingLine: String,
    val tapIntent: PendingIntent?,
)
