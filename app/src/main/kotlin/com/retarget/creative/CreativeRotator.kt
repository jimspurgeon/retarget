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
 * Scores creatives by:
 *   score = baseAppeal × restRecovery × wearPenalty × diversityFactor
 *
 * and picks randomly among the top-K scorers for variety. Rest recovery
 * penalizes creatives shown recently; cumulative wear slightly tires a
 * creative forever (wearout avoidance — advertising psychology §4,
 * docs/research/advertising-psychology.md). The diversity factor spreads
 * selections across sub-themes to prevent clustering on highest-appeal themes.
 *
 * PURE KOTLIN — no Android dependencies. Unit-test everything here.
 */
class CreativeRotator {
    /**
     * Selects the next creative to display, applying fatigue-aware scoring with
     * sub-theme diversity.
     *
     * @param candidates  creatives eligible for this slot (prefiltered by goal/theme)
     * @param exposureLedger  on-device append-only record of impressions
     * @param nowMs       current time (injectable for tests)
     * @param random      random generator for top-K selection (injectable for tests)
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

    /**
     * Computes the sub-theme diversity multiplier.
     *
     * Formula: 1.0 - alpha * (subThemeExposures / totalExposures)
     * When totalExposures = 0, returns 1.0 (no penalty on first draw).
     * Single sub-theme packs are handled correctly (multiplier still applies).
     *
     * @param creative  the creative being scored
     * @param ledger    exposure ledger for computing sub-theme ratios
     * @return diversity factor in range (1-alpha, 1.0]
     */
    fun computeDiversityFactor(
        creative: Creative,
        ledger: ExposureLedger,
    ): Double {
        val totalExposures = ledger.totalExposures()
        if (totalExposures == 0) return 1.0

        val subThemeExposures = ledger.exposuresBySubTheme(creative.subTheme)
        val subThemeRatio = subThemeExposures.toDouble() / totalExposures

        return 1.0 - DIVERSITY_ALPHA * subThemeRatio
    }

    /**
     * Scores a creative for selection, incorporating fatigue and sub-theme diversity.
     *
     * Score formula: baseAppeal × restRecovery × wearPenalty × diversityFactor
     * The diversity factor penalizes overrepresented sub-themes, spreading
     * selections across thematic variety (Option A from design gate).
     */
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
        // Sub-theme diversity: penalizes overrepresented sub-themes (Option A).
        val diversityFactor = computeDiversityFactor(creative, ledger)

        return creative.baseAppeal * restRecovery * wearPenalty * diversityFactor
    }

    companion object {
        const val TOP_K = 3
        const val HALF_LIFE_HOURS = 72.0 // 3 days per creative freshness half-life
        const val WEAR_RATE = 0.03 // gentle cumulative wear
        const val MS_PER_HOUR = 3_600_000.0
        const val DIVERSITY_ALPHA = 0.4 // sub-theme depletion strength (range [0.3, 0.5])
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

    /** Total number of exposures recorded across all creatives. */
    fun totalExposures(): Int

    /** Number of exposures for a specific sub-theme. */
    fun exposuresBySubTheme(subTheme: String): Int

    /** Count of exposures for a specific channel. */
    fun exposuresByChannel(channel: Channel): Int

    /**
     * Count of exposures for a specific channel since a given timestamp.
     * Used for daily pacing (e.g., "2/3 today").
     */
    fun exposuresTodayByChannel(channel: Channel, startOfDayMs: Long): Int
}

/** One ledger entry, newest-first view for diversity/analysis windows. */
data class RecentExposure(
    val creativeId: String,
    val subTheme: String,
    val channel: Channel,
    val atMs: Long,
)

enum class Channel { WALLPAPER, NOTIFICATION, OVERLAY, WIDGET, LOCK_SCREEN_TICKER }
