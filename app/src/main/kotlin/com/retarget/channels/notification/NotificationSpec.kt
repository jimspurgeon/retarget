/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.notification

import com.retarget.creative.Creative

/**
 * Specification for a notification nudge (Phase 2, Milestone 2.1).
 *
 * Data carrier from the scheduler to the [NotificationChannel]. Contains all
 * fields needed to render a BigPictureStyle notification with action buttons.
 * See docs/plans/PHASE2-CAMPAIGN.md §2.2 for the notification channel design.
 */
data class NotificationSpec(
    val goalId: Long,
    val creative: Creative,
    val title: String,
    val copyLine: String,
    val actions: NotificationActions,
)

/**
 * Action buttons for a notification nudge.
 *
 * Each action maps to a PendingIntent stub that the notification channel
 * wires to concrete handlers (check-in, snooze, opt-down). Per AGENTS.md §2,
 * all nudges must be reversible and controllable.
 */
data class NotificationActions(
    val checkInIntent: android.app.PendingIntent,
    val snooze2hIntent: android.app.PendingIntent,
    val fewerLikeThisIntent: android.app.PendingIntent,
)
