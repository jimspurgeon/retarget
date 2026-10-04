/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

/**
 * Wallpaper channel pacing — PURE KOTLIN, no Android dependencies.
 *
 * The wallpaper is an ambient channel (no hard daily cap; DEVELOPMENT.md
 * "Pre-registered decisions" #6), but quiet hours are sacred (#3): rotation
 * never fires between 22:00 and 07:00 local time. The Android-side worker
 * consults this policy before touching WallpaperManager.
 */
object WallpaperRotationPolicy {
    /** Rotation cadence: ~3 impressions/day keeps a pack fresh without churn. */
    const val INTERVAL_HOURS = 8L

    /** True if wallpaper rotation is allowed at [hourOfDay] (0–23, local time). */
    fun shouldRotateNow(hourOfDay: Int): Boolean = !BudgetPolicy.isQuietHour(hourOfDay)
}
