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

    // ---- Sub-theme diversity (Phase 1) ----

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
    fun `sub-theme diversity spreads selections across a pack`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        val now = 0L
        // Two sub-themes, equal-rest creatives; simulate a rotation run.
        val pool =
            (1..4).map { creative("a$it", subTheme = "alpha") } +
                (1..4).map { creative("b$it", subTheme = "beta") }

        val random = Random(7)
        repeat(20) {
            val pick = rotator.selectNext(pool, ledger, nowMs = now, random = random) ?: return@repeat
            ledger.recordExposure(pick.id, pick.subTheme, Channel.WALLPAPER, now + it)
        }

        val alphaShown = ledger.exposures.count { it.subTheme == "alpha" }
        val betaShown = ledger.exposures.count { it.subTheme == "beta" }
        // Without diversity the run clamps to a single dominant theme with the
        // top-K random tie-break; with it, both themes should see real volume.
        assertTrue(alphaShown > 0 && betaShown > 0)
    }

    @Test
    fun `diversity boost is 1 when recent window is empty`() {
        val rotator = CreativeRotator()
        assertEquals(1.0, rotator.subThemeDiversityBoost("anything", FakeLedger()), 0.0)
    }

    @Test
    fun `diversity boost reaches max when sub-theme absent from recent window`() {
        val rotator = CreativeRotator()
        val ledger = FakeLedger()
        repeat(CreativeRotator.SUB_THEME_WINDOW_N) { ledger.recordExposure("x$it", "other", Channel.WALLPAPER, it * 100L) }

        assertEquals(1.0 + CreativeRotator.SUB_THEME_MAX_BOOST, rotator.subThemeDiversityBoost("unseen", ledger), 1e-9)
    }
}
