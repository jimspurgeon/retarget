/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

/**
 * Pure display state for the M3.2 home-screen widget (DEVELOPMENT.md Phase 3).
 *
 * This sealed hierarchy is the frozen seam between the state layer (this
 * package) and Worker B's Glance renderer: the renderer maps each variant to
 * composables and never reads repositories directly. Consequently the model
 * carries NO Android types (no Context, no resource ids) so it can be composed
 * and unit-tested on the plain JVM.
 *
 * User-visible strings live with the renderer, not here: `Empty` deliberately
 * carries no onboarding-hint text and `Error` carries a typed [ErrorReason]
 * instead of a message, keeping the state layer localizable-resource-agnostic.
 */
sealed interface WidgetSnapshot {

    /**
     * No active goal exists. The widget shows an onboarding hint (rendered by
     * Worker B from string resources, e.g. "Add a goal to see it here") with a
     * tap action that opens the app.
     */
    data object Empty : WidgetSnapshot

    /**
     * An active goal with today's pacing snapshot.
     *
     * @property goalDisplayName Display name of the active goal.
     * @property wallpaperShownToday Wallpaper exposures recorded today
     *   (0 if the wallpaper channel is disabled).
     * @property wallpaperTargetToday Wallpaper daily target from the goal's
     *   campaign settings (0 if the wallpaper channel is disabled).
     * @property notificationShownToday Notification exposures recorded today
     *   (0 if the notification channel is disabled).
     * @property notificationTargetToday Notification daily target from the
     *   goal's campaign settings (0 if the notification channel is disabled).
     * @property creativeImagePath Absolute path of the creative image to show,
     *   or null when no cached creative exists — the renderer falls back to a
     *   solid color.
     * @property checkedInToday True when at least one check-in was recorded
     *   today for this goal.
     */
    data class Active(
        val goalDisplayName: String,
        val wallpaperShownToday: Int,
        val wallpaperTargetToday: Int,
        val notificationShownToday: Int,
        val notificationTargetToday: Int,
        val creativeImagePath: String?,
        val checkedInToday: Boolean,
    ) : WidgetSnapshot

    /**
     * Generic degradation state: the goal exists but its campaign settings
     * could not be read (corrupt or out-of-range settingsJson). The renderer
     * shows a neutral, non-alarming fallback and a tap action into the app,
     * where the goal list remains fully usable.
     *
     * @param reason Machine-readable cause the renderer maps to a localized
     *   string resource (never shown raw to the user).
     */
    data class Error(
        val reason: ErrorReason,
    ) : WidgetSnapshot
}

/**
 * Closed set of composition-degradation causes for [WidgetSnapshot.Error].
 *
 * Sealed (not a plain enum) so new reasons are exhaustively handled by the
 * Glance renderer at compile time. Coordinator decision (Option A, worker
 * M3.2): the state layer stays resource-agnostic; Worker B maps each reason
 * to a localized string resource (e.g. SettingsUnparseable →
 * R.string.widget_error_generic) in rendering code.
 */
sealed interface ErrorReason {
    /** settingsJson failed to parse or violated CampaignSettings invariants. */
    data object SettingsUnparseable : ErrorReason

    /**
     * Reading persisted state failed (e.g. a Room/storage error while counting
     * today's exposures). Distinct from [SettingsUnparseable] so logs and any
     * future diagnostics never misattribute storage problems to user data
     * (gatekeeper M2m). Rendered identically: neutral message, tap reopens app.
     */
    data object StorageFailure : ErrorReason
}
