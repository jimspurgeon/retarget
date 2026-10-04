/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CreativeRotatorTest {
    private class FakeLedger : ExposureLedger {
        val exposures = mutableListOf<RecentExposure>()

        override fun recordExposure(
            creativeId: String,
            subTheme: String,
            channel: Channel,
            atMs: Long,
        ) {
            exposures.add(RecentExposure(creativeId, subTheme, channel, atMs))
        }

        override fun lastShownAt(creativeId: String): Long? = exposuresOf(creativeId).maxOfOrNull { it.atMs }

        override fun timesShown(creativeId: String): Int = exposuresOf(creativeId).size

        override fun recentExposures(limit: Int): List<RecentExposure> =
            exposures.sortedByDescending { it.atMs }.take(limit)

        override fun totalExposures(): Int = exposures.size

        override fun exposuresBySubTheme(subTheme: String): Int =
            exposures.count { it.subTheme == subTheme }

        private fun exposuresOf(creativeId: String) = exposures.filter { it.creativeId == creativeId }
    }

    private fun creative(
        id: String,
        subTheme: String = "default",
        appeal: Float = 1.0f,
    ) = Creative(
        id = id,
        packId = "pack",
        goalTheme = GoalTheme.HYDRATION,
        subTheme = subTheme,
        copyPool = listOf("Refreshing."),
        imagePath = "/x/$id.jpg",
        attribution = null,
        licenseUrl = "https://example.org/cc0",
        baseAppeal = appeal,
    )

    @Test
    fun `empty candidates returns null`() {
        val rotator = CreativeRotator()
        assertNull(rotator.selectNext(emptyList(), FakeLedger(), nowMs = 0))
    }

    @Test
    fun `single candidate is returned`() {
        val rotator = CreativeRotator()
        val only = creative("a")
        assertEquals(only, rotator.selectNext(listOf(only), FakeLedger(), nowMs = 0))
    }

    @Test
    fun `never-shown creative scores higher than recently-shown`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val now = 100 * CreativeRotator.MS_PER_HOUR.toLong()
        val fresh = creative("fresh")
        val stale = creative("stale")
        ledger.recordExposure("stale", "default", Channel.WALLPAPER, now - 60 * 3_600_000L) // 60h ago

        val freshScore = rotator.score(fresh, ledger, now)
        val staleScore = rotator.score(stale, ledger, now)
        assertTrue(freshScore > staleScore)
    }

    @Test
    fun `score recovers as time passes after exposure`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val shownAt = 0L
        ledger.recordExposure("c", "default", Channel.WALLPAPER, shownAt)
        val c = creative("c")

        val soonAfter = rotator.score(c, ledger, nowMs = 2 * 3_600_000L)
        val muchLater = rotator.score(c, ledger, nowMs = 100 * 3_600_000L)
        assertTrue(muchLater > soonAfter)
    }

    @Test
    fun `cumulative wear reduces score even after long rest`() {
        val rotator = CreativeRotator()
        val ledgerA = FakeLedger()
        val ledgerB = FakeLedger()
        repeat(20) { ledgerB.recordExposure("c", "default", Channel.WALLPAPER, it * 10_000L) }

        val c = creative("c")
        val restedOnce = rotator.score(c, ledgerA, nowMs = 10_000_000_000L)
        val wornTwenty = rotator.score(c, ledgerB, nowMs = 10_000_000_000L)
        assertTrue(restedOnce > wornTwenty)
    }

    @Test
    fun `selection avoids repeating same creative consecutively when alternatives exist`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val pool = (1..6).map { creative("c$it") }
        // Show c1 most recently.
        ledger.recordExposure("c1", "default", Channel.WALLPAPER, 1000L)

        val random = Random(42)
        val picks = (1..20).mapNotNull { rotator.selectNext(pool, ledger, nowMs = 2000L, random = random)?.id }
        // With 6 creatives, a fatigue-aware rotator should not pick c1 in all 20 draws.
        assertTrue(picks.none { it == "c1" } || picks.count { it == "c1" } < 20)
    }

    // ---- Sub-theme diversity (Phase 1, Option A: depletion multiplier) ----

    @Test
    fun `under-represented sub-theme scores higher than over-represented with equal appeal`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val now = 1_000_000_000L // far future of all exposures → equal rest recovery

        // Recent window (N=5) dominated by "water-glass".
        repeat(5) { ledger.recordExposure("w$it", "water-glass", Channel.WALLPAPER, now - it * 10_000L) }

        val overRep = creative("over", subTheme = "water-glass")
        val underRep = creative("under", subTheme = "river-stream")

        assertTrue(rotator.score(underRep, ledger, now) > rotator.score(overRep, ledger, now))
    }

    @Test
    fun `diversity factor returns one point zero when no exposures exist`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val c = creative("c", subTheme = "theme_a")

        val factor = rotator.computeDiversityFactor(c, ledger)
        assertEquals(1.0, factor, 0.0001)
    }

    @Test
    fun `diversity factor penalizes overrepresented sub-themes`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()

        val themeACreative = creative("a", subTheme = "theme_a")
        val themeBCreative = creative("b", subTheme = "theme_b")

        // Record 4 exposures for theme_a, 1 for theme_b
        repeat(4) { ledger.recordExposure("a", "theme_a", Channel.WALLPAPER, it * 10_000L) }
        ledger.recordExposure("b", "theme_b", Channel.WALLPAPER, 50_000L)

        // theme_a should be penalized: 1.0 - 0.4 * (4/5) = 1.0 - 0.32 = 0.68
        val factorA = rotator.computeDiversityFactor(themeACreative, ledger)
        assertEquals(0.68, factorA, 0.0001)

        // theme_b should be less penalized: 1.0 - 0.4 * (1/5) = 1.0 - 0.08 = 0.92
        val factorB = rotator.computeDiversityFactor(themeBCreative, ledger)
        assertEquals(0.92, factorB, 0.0001)
    }

    @Test
    fun `single sub-theme pack still applies diversity factor correctly`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val pool = (1..4).map { creative("c$it", subTheme = "single_theme") }

        repeat(4) { ledger.recordExposure("c${it + 1}", "single_theme", Channel.WALLPAPER, it * 10_000L) }

        // All 4 exposures are for the same sub-theme: factor = 1.0 - 0.4 * (4/4) = 0.6
        val factor = rotator.computeDiversityFactor(pool[0], ledger)
        assertEquals(0.6, factor, 0.0001)
    }

    @Test
    fun `diversity factor promotes spread across sub-themes statistically`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        
        // Create a pool with 3 creatives per sub-theme (2 sub-themes = 6 total)
        val pool = mutableListOf<Creative>()
        repeat(3) { i ->
            pool.add(creative("a$i", subTheme = "theme_a", appeal = 1.0f))
        }
        repeat(3) { i ->
            pool.add(creative("b$i", subTheme = "theme_b", appeal = 1.1f)) // Slightly higher appeal
        }

        // Simulate 30 draws with seeded randomness
        val random = Random(seed = 123)
        val selectionCounts = mutableMapOf<String, Int>().withDefault { 0 }
        
        (1..30).forEach { drawNum ->
            val selected = rotator.selectNext(pool, ledger, nowMs = 100_000L + drawNum * 1000L, random = random)
            selected?.let {
                selectionCounts[it.subTheme] = (selectionCounts[it.subTheme] ?: 0) + 1
                // Record the exposure so diversity factor kicks in
                ledger.recordExposure(it.id, it.subTheme, Channel.WALLPAPER, 100_000L + drawNum * 1000L)
            }
        }

        // Without diversity factor, theme_b (higher appeal 1.1 vs 1.0) would dominate.
        // With diversity, we expect a more balanced distribution.
        val themeACount = selectionCounts["theme_a"] ?: 0
        val themeBCount = selectionCounts["theme_b"] ?: 0
        
        // Assert that neither theme monopolizes (> 85% of selections would indicate clustering)
        assertTrue(
            "Theme A should have at least 15% of selections with diversity factor applied. Got $themeACount/30",
            themeACount >= 4
        )
        assertTrue(
            "Theme B should not exceed 85% of selections. Got $themeBCount/30",
            themeBCount <= 26
        )
    }

    @Test
    fun `diversity computation is deterministic with same seed`() {
        val rotator = CreativeRotator()
        val ledger1 = FakeLedger()
        val ledger2 = FakeLedger()
        
        val pool = listOf(
            creative("a", subTheme = "theme_a", appeal = 1.0f),
            creative("b", subTheme = "theme_b", appeal = 1.0f),
        )

        val random1 = Random(42)
        val random2 = Random(42)
        
        val selections1 = (1..20).mapNotNull { 
            rotator.selectNext(pool, ledger1, nowMs = 1000L + it * 100L, random = random1)?.subTheme 
        }
        val selections2 = (1..20).mapNotNull { 
            rotator.selectNext(pool, ledger2, nowMs = 1000L + it * 100L, random = random2)?.subTheme 
        }

        // Same seed should produce identical sequence
        assertEquals(selections1, selections2)
    }
}
