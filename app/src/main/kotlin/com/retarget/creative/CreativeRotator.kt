/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import kotlin.math.exp
import kotlin.math.ln

/**
 * Fatigue-aware creative rotation engine — the app's algorithmic heart.
 *
 * Scores candidate creatives by:
 *   score = baseAppeal × restRecovery × wearPenalty
 *
 * and picks randomly among the top-K scorers for variety. Rest recovery
 * penalizes creatives shown recently; cumulative wear slightly tires a
 * creative forever (wearout avoidance — advertising psychology §4,
 * docs/research/advertising-psychology.md).
 *
 * Sub-theme diversity (Phase 1): score also multiplies in a boost for
 * under-represented sub-themes based on the most recent N exposures, so
 * selections spread across a pack instead of clustering on one look.
 *
 * PURE KOTLIN — no Android dependencies. Unit-test everything here.
 */
class CreativeRotator {
    /**
     * @param candidates  creatives eligible for this slot (prefiltered by goal/theme)
     * @param exposureLedger  on-device append-only record of impressions
     * @param nowMs       current time (injectable for tests)
     */
    fun selectNext(
        candidates: List<Creative>,
        exposureLedger: ExposureLedger,
        nowMs: Long,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): Creative? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()

        val scored = candidates.map { it to score(it, exposureLedger, nowMs) }
        // Weighted random among top-K to keep variety without picking stale creatives.
        val topK = scored.sortedByDescending { it.second }.take(TOP_K)
        return topK[random.nextInt(topK.size)].first
    }

    fun score(
        creative: Creative,
        ledger: ExposureLedger,
        nowMs: Long,
    ): Double {
        val lastShown = ledger.lastShownAt(creative.id)
        val timesShown = ledger.timesShown(creative.id)
        val recencyHours =
            if (lastShown == null) {
                Double.POSITIVE_INFINITY
            } else {
                (nowMs - lastShown) / MS_PER_HOUR
            }

        // Rest-recovery curve: a creative just shown is fully suppressed (0) and
        // recovers half the remaining distance to full freshness (1) every
        // HALF_LIFE_HOURS of rest. Never-shown creatives are fully rested (1).
        // (Wearout avoidance — advertising psychology §4, docs/research/
        // advertising-psychology.md.)
        val restRecovery = 1.0 - exp(-ln(2.0) * recencyHours / HALF_LIFE_HOURS)
        // Cumulative wear: each showing slightly tires the creative forever (small effect).
        val wearPenalty = 1.0 / (1.0 + WEAR_RATE * timesShown)
        // Sub-theme diversity: boost under-represented sub-themes among the
        // most recent N exposures so a pack's looks rotate broadly.
        val subThemeBoost = subThemeDiversityBoost(creative.subTheme, ledger)

        return creative.baseAppeal * restRecovery * wearPenalty * subThemeBoost
    }

    /**
     * Diversity multiplier for [subTheme]: 1.0 when the theme is (jointly)
     * most represented in the recent-exposure window, rising linearly to
     * [SUB_THEME_MAX_BOOST] when absent from it.
     */
    internal fun subThemeDiversityBoost(
        subTheme: String,
        ledger: ExposureLedger,
    ): Double {
        val recent = ledger.recentExposures(SUB_THEME_WINDOW_N)
        if (recent.isEmpty()) return 1.0
        val counts = HashMap<String, Int>()
        for (exposure in recent) {
            counts[exposure.subTheme] = (counts[exposure.subTheme] ?: 0) + 1
        }
        val maxCount = counts.values.max()
        val themeCount = counts[subTheme] ?: 0
        if (maxCount == 0) return 1.0
        val deficit = (maxCount - themeCount).toDouble() / maxCount
        return 1.0 + SUB_THEME_MAX_BOOST * deficit
    }

    companion object {
        const val TOP_K = 3
        const val HALF_LIFE_HOURS = 72.0 // 3 days per creative freshness half-life
        const val WEAR_RATE = 0.03 // gentle cumulative wear
        const val MS_PER_HOUR = 3_600_000.0

        /** Lookback window (most recent N exposures) for sub-theme diversity. */
        const val SUB_THEME_WINDOW_N = 5

        /** Score boost for a sub-theme entirely absent from the recent window. */
        const val SUB_THEME_MAX_BOOST = 0.5
    }
}

/** Append-only, on-device impression log. Never leaves the device. */
interface ExposureLedger {
    /** Appends one exposure event. Events are self-describing (sub-theme denormalized). */
    fun recordExposure(
        creativeId: String,
        subTheme: String,
        channel: Channel,
        atMs: Long,
    )

    fun lastShownAt(creativeId: String): Long?

    fun timesShown(creativeId: String): Int

    /** Most recent exposures first (newest first), at most [limit] entries. */
    fun recentExposures(limit: Int): List<RecentExposure>
}

/** One ledger entry, newest-first view for diversity/analysis windows. */
data class RecentExposure(
    val creativeId: String,
    val subTheme: String,
    val channel: Channel,
    val atMs: Long,
)

enum class Channel { WALLPAPER, NOTIFICATION, OVERLAY, WIDGET }
