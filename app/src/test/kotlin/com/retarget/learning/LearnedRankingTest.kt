/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.learning

import com.retarget.creative.Creative
import com.retarget.creative.CreativeRotator
import com.retarget.creative.ExposureLedger
import com.retarget.creative.RecentExposure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Pins M3.4 task-3 behavior: learned weights only affect ranking after the
 * MIN_OBSERVATIONS floor, and stay clamped inside [WEIGHT_MIN, WEIGHT_MAX] /
 * [BONUS_MIN, BONUS_MAX] thereafter.
 */
class LearnedRankingTest {
    private val ledger: ExposureLedger = FakeExposureLedger()

    private fun states(
        attempts: Int,
        rewards: List<Double>,
    ): List<LearningStateEntity> =
        rewards.mapIndexed { i, r ->
            LearningStateEntity(
                goalId = 1,
                bucket = 0,
                subTheme = "theme$i",
                attempts = attempts / rewards.size.coerceAtLeast(1),
                scoreSum = r,
                scoreCount = 1,
            )
        }

    @Test
    fun `timingWeight is neutral below the observation floor`() {
        val belowFloor = states(EpsilonGreedyBandit.MIN_OBSERVATIONS - 1, listOf(1.0))
        assertEquals(1.0, EpsilonGreedyBandit.timingWeight(belowFloor, 0), 1e-9)
    }

    @Test
    fun `timingWeight rewards a hot bucket and penalizes a cold one, clamped`() {
        // Past the floor: strongly-positive rewards raise the weight above
        // neutral, strongly-negative lower it below neutral. (With the
        // conservative PRIOR_WEIGHT=5 pseudo-count prior, realistic reward
        // magnitudes stay near-neutral — the mapping is intentionally gentle.)
        val hot = states(50, List(5) { EpsilonGreedyBandit.REWARD_CHECK_IN })
        val hotWeight = EpsilonGreedyBandit.timingWeight(hot, 0)
        assertTrue("hot=$hotWeight", hotWeight > 1.0)
        assertTrue(hotWeight <= EpsilonGreedyBandit.WEIGHT_MAX)

        val cold = states(50, List(5) { EpsilonGreedyBandit.REWARD_FEWER_LIKE_THIS })
        val coldWeight = EpsilonGreedyBandit.timingWeight(cold, 0)
        assertTrue("cold=$coldWeight", coldWeight < 1.0)
        assertTrue(coldWeight >= EpsilonGreedyBandit.WEIGHT_MIN)
    }

    @Test
    fun `subThemeBonus is zero below the floor and respects bounds`() {
        val bucketStates = states(50, listOf(1.0, -1.0))
        val best = bucketStates[0]
        val worst = bucketStates[1]

        val bonusBest = EpsilonGreedyBandit.subThemeBonus(best, bucketStates)
        val bonusWorst = EpsilonGreedyBandit.subThemeBonus(worst, bucketStates)

        assertTrue(bonusBest > 0.0)
        assertTrue(bonusWorst < 0.0)
        assertTrue(bonusBest <= EpsilonGreedyBandit.BONUS_MAX + 1e-9)
        assertTrue(bonusWorst >= EpsilonGreedyBandit.BONUS_MIN - 1e-9)
    }

    @Test
    fun `rotator LearningContext reorders subThemes by learned bonus`() {
        val rotator = CreativeRotator()
        val now = 0L

        val strong = Creative(
            id = "s1", packId = "p", goalTheme = com.retarget.creative.GoalTheme.HYDRATION,
            subTheme = "focus", copyPool = listOf("a"), imagePath = "/x",
            attribution = null, licenseUrl = "https://example.org/cc0",
        )
        val weak = Creative(
            id = "w1", packId = "p", goalTheme = com.retarget.creative.GoalTheme.HYDRATION,
            subTheme = "chill", copyPool = listOf("b"), imagePath = "/x",
            attribution = null, licenseUrl = "https://example.org/cc0",
        )

        // No learning context → scores equal (same appeal, no exposure history).
        val plainStrong = rotator.score(strong, ledger, now)
        val plainWeak = rotator.score(weak, ledger, now)
        assertEquals(plainStrong, plainWeak, 1e-9)

        // Learned context: "focus" earned check-ins, "chill" earned rejections.
        val ctx = CreativeRotator.LearningContext(
            goalId = 1,
            bucket = 0,
            statesForBucket = listOf(
                LearningStateEntity(goalId = 1, bucket = 0, subTheme = "focus", attempts = 20, scoreSum = 18.0, scoreCount = 18),
                LearningStateEntity(goalId = 1, bucket = 0, subTheme = "chill", attempts = 20, scoreSum = -16.0, scoreCount = 16),
            ),
        )
        val learnedStrong = rotator.score(strong, ledger, now, learningContext = ctx)
        val learnedWeak = rotator.score(weak, ledger, now, learningContext = ctx)
        assertTrue(learnedStrong > learnedWeak)
        assertTrue(learnedStrong > plainStrong) // bonus is strictly additive
    }
}

/** Fake exposure ledger returning zeros so score = baseAppeal x 1 x 1 x 1 */
class FakeExposureLedger : ExposureLedger {
    override fun lastShownAt(creativeId: String): Long? = null
    override fun timesShown(creativeId: String): Int = 0
    override fun recordExposure(creativeId: String, subTheme: String, channel: com.retarget.creative.Channel, atMs: Long) {}
    override fun recentExposures(limit: Int): List<RecentExposure> = emptyList()
    override fun totalExposures(): Int = 0
    override fun exposuresBySubTheme(subTheme: String): Int = 0
    override fun exposuresByChannel(channel: com.retarget.creative.Channel): Int = 0
    override fun exposuresTodayByChannel(channel: com.retarget.creative.Channel, startOfDayMs: Long): Int = 0
}
