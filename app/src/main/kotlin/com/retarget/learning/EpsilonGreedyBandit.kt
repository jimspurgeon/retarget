/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import kotlin.random.Random

/**
 * ε-greedy bandit for on-device nudge adaptation (M3.4, PHASE3-AGENCY.md §5).
 *
 * Adapts TWO things, both scoped to a single goal:
 *  1. Slot timing — a multiplicative weight on the scheduler's base priority
 *     for the 4-hour bucket at delivery time.
 *  2. Creative sub-theme mix — an additive bonus in CreativeRotator scoring.
 *
 * What it NEVER adapts (immutable, PHASE3-AGENCY.md §8 decisions #3/#6):
 * daily caps, quiet hours, crowding backoff, ticker hard cap, channel on/off.
 * The bandit only reorders candidates *within* the gate-approved set — its
 * output is clamped to [WEIGHT_MIN, WEIGHT_MAX] so it can starve or flood
 * nothing.
 *
 * Learning model: each (goalId, bucket, subTheme) triple accumulates
 * attempts/scoreSum/scoreCount in Room. The mean score is shrunk toward a
 * conservative PRIOR_MEAN with a Beta-style pseudo-count, and adaptation only
 * kicks in after MIN_OBSERVATIONS combined attempts per (goalId, bucket).
 *
 * PURE KOTLIN — no Android dependencies. Unit-test everything here.
 */
object EpsilonGreedyBandit {
    /**
     * Conservative prior mean for a fresh (goal, bucket, subTheme) cell.
     * 0.5 assumes nothing: adaptation must earn its signal from data.
     */
    const val PRIOR_MEAN = 0.5

    /**
     * Pseudo-observations assigned to the prior. Higher = slower to deviate
     * from neutral on thin data. 5 keeps the first few real signals gentle.
     */
    const val PRIOR_WEIGHT = 5.0

    /**
     * Combined attempts per (goalId, bucket) required before ANY adaptation.
     * Human-approved design (2026-10-09): ~10 observations per (goal, bucket)
     * before slot-timing adapts; the same floor guards the sub-theme mix.
     */
    const val MIN_OBSERVATIONS = 10

    /** ε: probability of exploring a uniformly random candidate. */
    const val EPSILON = 0.1

    /**
     * Clamp range for learned weights. The bandit can only reorder
     * gate-approved candidates — never silence one entirely ([0.5]) or
     * double one ([1.5]).
     */
    const val WEIGHT_MIN = 0.5
    const val WEIGHT_MAX = 1.5

    /**
     * Reward magnitudes (logged decisions, tunable):
     * - CHECK_IN (+1.0): the strongest positive — the user acted.
     * - FEWER_LIKE_THIS (-1.0): strong negative — the user said stop.
     * - SNOOZE (-0.25): mild negative — inconvenient, not unwanted.
     */
    const val REWARD_CHECK_IN = 1.0
    const val REWARD_FEWER_LIKE_THIS = -1.0
    const val REWARD_SNOOZE = -0.25

    /** Rewards only bind to exposures within 2h (approved design). */
    const val CREDIT_WINDOW_MS = 2 * 3_600_000L

    /** Number of 4-hour buckets in a day. */
    const val BUCKETS_PER_DAY = 6

    /**
     * Maps an epoch-ms timestamp to its 4-hour bucket index (0-5).
     * Pure function of wall-clock hour; timezone handling belongs to the caller
     * (the scheduler passes `nowMs` in the same zone it uses everywhere).
     */
    fun bucketOf(
        epochMs: Long,
        zone: java.time.ZoneId,
    ): Int {
        val hour = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).hour
        return (hour / 4).coerceIn(0, BUCKETS_PER_DAY - 1)
    }

    /**
     * Posterior mean score for a (goalId, bucket, subTheme) cell:
     * (PRIOR_MEAN × PRIOR_WEIGHT + scoreSum) / (PRIOR_WEIGHT + scoreCount).
     *
     * With no data, returns PRIOR_MEAN. With abundant data, approaches the raw
     * empirical mean, tempered by the prior during early observations.
     */
    fun posteriorMean(state: LearningStateEntity?): Double {
        if (state == null) return PRIOR_MEAN
        val numerator = PRIOR_MEAN * PRIOR_WEIGHT + state.scoreSum
        val denominator = PRIOR_WEIGHT + state.scoreCount
        return numerator / denominator
    }

    /**
     * Combined attempts across all subThemes for one (goalId, bucket) — the
     * observation floor quantity. Below [MIN_OBSERVATIONS], no adaptation.
     */
    fun bucketAttempts(states: List<LearningStateEntity>): Int = states.sumOf { it.attempts }

    /**
     * Learned timing weight for (goalId, bucket of nowMs).
     *
     * Returns 1.0 (neutral) unless the (goalId, bucket) has at least
     * MIN_OBSERVATIONS combined attempts — cold-start cells never adapt.
     * Otherwise maps the posterior mean reward of the bucket (best subTheme)
     * multiplicatively onto a clamped [WEIGHT_MIN, WEIGHT_MAX] range.
     *
     * @param states learning rows for this (goalId, bucket), any subTheme
     * @return weight in [WEIGHT_MIN, WEIGHT_MAX]; 1.0 before the floor
     */
    fun timingWeight(
        states: List<LearningStateEntity>,
        bucket: Int,
    ): Double {
        if (bucketAttempts(states) < MIN_OBSERVATIONS) return 1.0

        // Best subTheme's posterior represents how receptive this bucket is.
        val bestPosterior =
            states.maxOfOrNull { posteriorMean(it) }
                ?: PRIOR_MEAN

        // Map posterior (bounded ~[-1, 2] by reward magnitudes) linearly:
        // PRIOR_MEAN → 1.0 (neutral), each unit of deviation scales gently.
        val rawWeight = 1.0 + (bestPosterior - PRIOR_MEAN)
        return rawWeight.coerceIn(WEIGHT_MIN, WEIGHT_MAX)
    }

    /**
     * Learned additive bonus for a creative subTheme in this bucket.
     *
     * Zero before the (goalId, bucket) observation floor (cold start is
     * neutral). Otherwise (posterior − PRIOR_MEAN) × BONUS_SCALE, bounded so
     * the bonus can reorder subThemes but not dominate base appeal.
     *
     * @param state learning row for this exact (goalId, bucket, subTheme)
     * @param bucketStates all rows for this (goalId, bucket) — the floor gate
     */
    fun subThemeBonus(
        state: LearningStateEntity?,
        bucketStates: List<LearningStateEntity>,
    ): Double {
        if (bucketAttempts(bucketStates) < MIN_OBSERVATIONS) return 0.0
        if (state == null || state.scoreCount == 0) return 0.0

        return ((posteriorMean(state) - PRIOR_MEAN) * BONUS_SCALE)
            .coerceIn(BONUS_MIN, BONUS_MAX)
    }

    /**
     * ε-greedy action selection: with probability ε pick a uniformly random
     * index; otherwise pick the greedy (highest posterior) index.
     *
     * @param posteriors one score per candidate (same order as the candidates)
     * @param random injectable for deterministic tests
     * @return index of the chosen candidate
     */
    fun select(
        posteriors: List<Double>,
        random: Random,
    ): Int {
        require(posteriors.isNotEmpty()) { "select requires at least one candidate" }
        if (posteriors.size == 1) return 0

        if (random.nextDouble() < EPSILON) {
            return random.nextInt(posteriors.size)
        }
        return posteriors.indices.maxBy { posteriors[it] }
    }

    /**
     * Apply a reward to a learning-state cell, returning the updated entity.
     * Increments scoreCount/scoreSum. Ties-together check-in, fewer-like-this,
     * and snooze signals with one code path (pure function; persistence is the
     * caller's job).
     */
    fun applyReward(
        state: LearningStateEntity?,
        goalId: Long,
        bucket: Int,
        subTheme: String,
        reward: Double,
    ): LearningStateEntity {
        val base = state ?: LearningStateEntity(goalId = goalId, bucket = bucket, subTheme = subTheme)
        return base.copy(
            scoreSum = base.scoreSum + reward,
            scoreCount = base.scoreCount + 1,
        )
    }

    /**
     * Registers one observation (a nudge delivered through a channel) for a
     * learning cell, incrementing [LearningStateEntity.attempts]. Without
     * this, the [MIN_OBSERVATIONS] floor can never be reached and learned
     * weights stay permanently neutral (gatekeeper B1, 2026-10-10).
     *
     * Called from the delivery path at exposure time — "observation" means
     * a nudge delivered, matching the approved design's "~10 observations
     * per (goal, bucket)" semantics.
     */
    fun incrementObservation(state: LearningStateEntity): LearningStateEntity =
        state.copy(attempts = state.attempts + 1)

    /** Scale for sub-theme additive bonuses (logged decision; see [subThemeBonus]). */
    const val BONUS_SCALE = 0.25

    /** Bounds for [subThemeBonus]. */
    const val BONUS_MIN = -0.5
    const val BONUS_MAX = 0.5
}
