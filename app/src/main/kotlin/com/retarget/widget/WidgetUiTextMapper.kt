/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

/**
 * Widget display text bundle — every user-visible string the widget renders,
 * already resolved (values, not resource IDs) so Glance composables consume
 * them directly and the mapping stays pure JVM (M3.2 acceptance criterion:
 * "Robolectric test for glance state mapping").
 */
data class WidgetUiText(
    /** Headline line (goal name, or empty/error title). Null when this state has no headline. */
    val headline: String?,
    /** Secondary line (pacing "2 of 4 nudges", or the onboarding/error hint). Null when absent. */
    val body: String?,
    /** Check-in button label; null when the state offers no check-in action. */
    val checkInLabel: String?,
)

/**
 * Pure snapshot → UI text mapping (M3.2).
 *
 * `snapshotToUiText` is deliberately free of Android types: callers pass the
 * resolved string-resource values in, and receive resolved strings out. That
 * keeps this object JVM-testable without Robolectric, while [RetargetWidget]
 * owns the actual `Context.getString` resolution.
 */
object WidgetUiTextMapper {
    /** Resource-string lookup surface, so tests supply literals and Glance supplies resources. */
    interface Strings {
        val emptyTitle: String
        val emptyHint: String
        val errorMessage: String
        val checkInButton: String
        val checkInDoneButton: String
        val pacingFormat: String // e.g. "%1$d of %2$d nudges"
    }

    /**
     * Computes the aggregate pacing line for ENABLED channels only, e.g. "2 of 4 nudges".
     */
    fun aggregatePacing(
        active: WidgetSnapshot.Active,
        wallpaperEnabled: Boolean,
        notificationEnabled: Boolean,
        pacingFormat: String,
    ): String {
        var shown = 0
        var target = 0
        if (wallpaperEnabled) {
            shown += active.wallpaperShownToday
            target += active.wallpaperTargetToday
        }
        if (notificationEnabled) {
            shown += active.notificationShownToday
            target += active.notificationTargetToday
        }
        return formatPacing(pacingFormat, shown, target)
    }

    /** Formats "N of M nudges" given a format string with %1$d / %2$d placeholders. */
    fun formatPacing(
        format: String,
        shown: Int,
        target: Int,
    ): String = format.replace("%1\$d", shown.toString()).replace("%2\$d", target.toString())

    /**
     * snapshotToUiText: maps a [WidgetSnapshot] to renderable [WidgetUiText].
     *
     * @param snapshot display state from [WidgetStateComposer]
     * @param strings resolved string values (resource-backed in prod, literals in tests)
     * @param wallpaperEnabled whether the wallpaper channel is enabled (gates pacing aggregation)
     * @param notificationEnabled whether the notification channel is enabled
     */
    fun snapshotToUiText(
        snapshot: WidgetSnapshot,
        strings: Strings,
        wallpaperEnabled: Boolean = true,
        notificationEnabled: Boolean = false,
    ): WidgetUiText =
        when (snapshot) {
            is WidgetSnapshot.Empty ->
                WidgetUiText(
                    headline = strings.emptyTitle,
                    body = strings.emptyHint,
                    checkInLabel = null,
                )

            is WidgetSnapshot.Error ->
                WidgetUiText(
                    headline = strings.errorMessage,
                    body = null,
                    checkInLabel = null,
                )

            is WidgetSnapshot.Active ->
                WidgetUiText(
                    headline = snapshot.goalDisplayName.ifBlank { strings.emptyTitle },
                    body = aggregatePacing(snapshot, wallpaperEnabled, notificationEnabled, strings.pacingFormat),
                    checkInLabel =
                        when {
                            snapshot.checkedInToday -> strings.checkInDoneButton
                            else -> strings.checkInButton
                        },
                )
        }
}
