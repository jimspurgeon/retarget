/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import com.retarget.creative.Channel
import com.retarget.creative.Creative
import com.retarget.creative.ExposureLedger
import com.retarget.goal.GoalEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Multi-channel nudge scheduling engine — coordinates wallpaper, notification,
 * overlay, and widget delivery according to per-channel policies and cross-channel
 * constraints.
 *
 * Per PHASE2-CAMPAIGN.md §3: computes next delivery slots for all active goals/channels,
 * applies fresh-start boosts, and enforces cross-channel crowding backoff.
 *
 * PURE KOTLIN — no Android dependencies. Fully testable without Robolectric.
 */
object NudgeScheduler {

    /** Minimum gap between different channels for the same goal (crowding backoff). */
    const val MIN_GAP_MS = 3_600_000L // 60 minutes

    /**
     * Computes the next delivery slots for all active goals and their enabled channels.
     *
     * This is the core scheduling algorithm that:
     *   1. Iterates over each active goal and its enabled channels
     *   2. Calls per-channel policy canDeliver() to check eligibility
     *   3. Applies FreshStartCalendar boost multiplier to slot priorities
     *   4. Enforces cross-channel crowding backoff (MIN_GAP_MS between channels)
     *   5. Returns slots sorted by priority (highest first)
     *
     * @param activeGoals  list of active goals with their channel enablement settings
     * @param nowMs        current timestamp in milliseconds (injectable for tests)
     * @param ledger       exposure ledger for cross-channel timing queries
     * @param random       random generator for tie-breaking (injectable for tests)
     * @return List of Slot objects sorted by descending priority
     */
    fun computeSlots(
        activeGoals: List<GoalEntity>,
        nowMs: Long,
        ledger: ExposureLedger,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<Slot> {
        val slots = mutableListOf<Slot>()

        for (goal in activeGoals) {
            val settings = goal.settings
            val goalId = goal.id

            // Process notification channel
            if (settings.notificationEnabled) {
                // Daily-cap accounting: ledger currently tracks exposures channel-wide
                // (no goalId column). For a single-goal campaign this is exact; with
                // multiple active goals it is conservative (under- rather than over-delivers).
                // TODO(goal-scoped-ledger): add goalId to ExposureEntity for per-goal caps.
                val startOfDayMs = Instant.ofEpochMilli(nowMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val sentToday = ledger.exposuresTodayByChannel(Channel.NOTIFICATION, startOfDayMs)
                if (NotificationPolicy.canDeliver(
                    goalId = goalId,
                    nowMs = nowMs,
                    settings = settings,
                    sentToday = sentToday,
                    dismissedSince = emptyList(),
                )) {
                    // Check crowding backoff before adding slot
                    if (passesCrowdingBackoff(goalId, Channel.NOTIFICATION, nowMs, ledger)) {
                        val basePriority = computeBasePriority(goal, nowMs, random)
                        val boostedPriority = applyFreshStartBoost(basePriority, nowMs)
                        slots.add(
                            Slot(
                                goalId = goalId,
                                channel = Channel.NOTIFICATION,
                                creative = null, // Creative selection deferred to delivery
                                scheduledTimeMs = nowMs,
                                priority = boostedPriority,
                            )
                        )
                    }
                }
            }

            // Process wallpaper channel
            if (settings.wallpaperEnabled) {
                val hourOfDay = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
                if (WallpaperRotationPolicy.shouldRotateNow(hourOfDay)) {
                    if (passesCrowdingBackoff(goalId, Channel.WALLPAPER, nowMs, ledger)) {
                        val basePriority = computeBasePriority(goal, nowMs, random)
                        val boostedPriority = applyFreshStartBoost(basePriority, nowMs)
                        slots.add(
                            Slot(
                                goalId = goalId,
                                channel = Channel.WALLPAPER,
                                creative = null, // Creative selection deferred to delivery
                                scheduledTimeMs = nowMs,
                                priority = boostedPriority,
                            )
                        )
                    }
                }
            }

            // TODO: Overlay and Widget channels to be implemented in future milestones
        }

        // Sort by priority descending (highest priority first)
        return slots.sortedByDescending { it.priority }
    }

    /**
     * Checks if a slot passes the cross-channel crowding backoff constraint.
     *
     * Skips the slot if it's within MIN_GAP_MS of any exposure on a different
     * channel for the same goal.
     *
     * @param goalId    the goal to check
     * @param channel   the channel proposing a slot
     * @param nowMs     the proposed slot time
     * @param ledger    exposure ledger for query
     * @return true if the slot is allowed (not too close to other channels)
     */
    private fun passesCrowdingBackoff(
        goalId: Long,
        channel: Channel,
        nowMs: Long,
        ledger: ExposureLedger,
    ): Boolean {
        // Get recent exposures across all channels
        val recentExposures = ledger.recentExposures(limit = 20)

        for (exposure in recentExposures) {
            // Only check exposures from DIFFERENT channels
            if (exposure.channel != channel) {
                val timeDiff = kotlin.math.abs(nowMs - exposure.atMs)
                if (timeDiff < MIN_GAP_MS) {
                    // Within the crowding gap — skip this slot
                    return false
                }
            }
        }

        return true
    }

    /**
     * Computes a base priority score for a goal-slot.
     *
     * Uses goal age and randomness for variety. Newer goals get slight preference,
     * but randomization prevents starvation.
     *
     * @param goal    the goal to score
     * @param nowMs   current timestamp for age calculation
     * @param random  random generator for tie-breaking
     * @return base priority score
     */
    private fun computeBasePriority(
        goal: GoalEntity,
        nowMs: Long,
        random: kotlin.random.Random,
    ): Double {
        // Base priority from goal age and randomness for variety
        val goalAgeHours = (nowMs - goal.createdAt) / 3_600_000.0
        val ageFactor = 1.0 / (1.0 + goalAgeHours / 24.0) // decays over ~24 hours
        val noise = 0.1 * random.nextDouble() // small random component

        return ageFactor + noise
    }

    /**
     * Applies fresh-start boost multiplier to the slot priority.
     *
     * Per FreshStartCalendar, fresh-start days (Mondays, 1st of month, New Year)
     * get a gentle boost to encourage engagement during motivational windows.
     *
     * @param basePriority  the unboosted priority
     * @param nowMs         current timestamp
     * @return boosted priority (bounded, gentle multiplier)
     */
    private fun applyFreshStartBoost(
        basePriority: Double,
        nowMs: Long,
    ): Double {
        val date = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val multiplier = FreshStartCalendar.boostMultiplier(date)
        return basePriority * multiplier
    }
}

/**
 * A computed delivery slot representing a scheduled nudge opportunity.
 *
 * @param goalId           the goal this slot targets
 * @param channel          the delivery channel (WALLPAPER, NOTIFICATION, etc.)
 * @param creative         the creative to deliver (null until selected at delivery)
 * @param scheduledTimeMs  when this slot should execute
 * @param priority         the computed priority score (higher = sooner)
 */
data class Slot(
    val goalId: Long,
    val channel: Channel,
    val creative: Creative?,
    val scheduledTimeMs: Long,
    val priority: Double,
)
