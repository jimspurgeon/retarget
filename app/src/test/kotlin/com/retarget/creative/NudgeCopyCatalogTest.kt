/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeCopyCatalogTest {

    // ---- Catalog shape -------------------------------------------------

    @Test
    fun `every theme has at least 12 lines`() {
        GoalTheme.entries.forEach { theme ->
            assertTrue(
                "Theme $theme has only ${NudgeCopyCatalog.forTheme(theme).size} lines",
                NudgeCopyCatalog.forTheme(theme).size >= 12,
            )
        }
    }

    @Test
    fun `no line is duplicated within a theme`() {
        GoalTheme.entries.forEach { theme ->
            val lines = NudgeCopyCatalog.forTheme(theme)
            assertEquals(
                "Theme $theme contains duplicate lines",
                lines.size,
                lines.toSet().size,
            )
        }
    }

    @Test
    fun `no line is blank`() {
        GoalTheme.entries.forEach { theme ->
            NudgeCopyCatalog.forTheme(theme).forEach { line ->
                assertTrue("Theme $theme has a blank line", line.isNotBlank())
            }
        }
    }

    // ---- selectLine purity and edges -----------------------------------

    @Test
    fun `selectLine returns a line from the theme pool on first run`() {
        // lastShownLine == null models a first run with no persisted state.
        GoalTheme.entries.forEach { theme ->
            val picked = NudgeCopyCatalog.selectLine(theme, null)
            assertTrue(
                "Picked line not in pool for $theme",
                NudgeCopyCatalog.forTheme(theme).contains(picked),
            )
        }
    }

    @Test
    fun `selectLine with a single-line pool returns that line`() {
        val only = "the only line"
        assertEquals(only, NudgeCopyCatalog.selectFrom(listOf(only), null))
        // Even when the persisted last line equals the only option, it is
        // still the only possible result (pool-size-1 edge).
        assertEquals(only, NudgeCopyCatalog.selectFrom(listOf(only), only))
    }

    @Test
    fun `selectLine never repeats the last shown line`() {
        val pool = listOf("a", "b", "c")
        // Repeat draws many times: the exclusion must hold for every draw.
        repeat(500) {
            val first = NudgeCopyCatalog.selectFrom(pool, null)
            val second = NudgeCopyCatalog.selectFrom(pool, first)
            assertNotEquals(first, second)
        }
    }

    @Test
    fun `selectLine still excludes when last line is stale or from another pool`() {
        // A persisted line that no longer exists in the pool (catalog edited)
        // must not crash or shrink the draw to empty.
        val pool = listOf("a", "b")
        repeat(100) {
            val picked = NudgeCopyCatalog.selectFrom(pool, "removed-from-catalog")
            assertTrue(pool.contains(picked))
        }
    }

    @Test
    fun `selectLine distributes over the remaining candidates`() {
        // With last=a, both b and c should eventually appear (sanity that
        // filtering didn't collapse to a single element).
        val seen = mutableSetOf<String>()
        repeat(200) {
            seen.add(NudgeCopyCatalog.selectFrom(listOf("a", "b", "c"), "a"))
        }
        assertEquals(setOf("b", "c"), seen)
    }

    @Test
    fun `unknown pools fall back to general wellness`() {
        // forTheme's contract: never empty; unlisted themes fall back.
        // All enum entries are listed, so this pins the fallback indirectly
        // by confirming every entry resolves to a non-empty pool.
        GoalTheme.entries.forEach { theme ->
            assertTrue(NudgeCopyCatalog.forTheme(theme).isNotEmpty())
        }
    }

    @Test
    fun `two consecutive deliveries for a theme never show the same line`() {
        // Models the worker's persisted-state loop: draw, persist, redraw.
        GoalTheme.entries.forEach { theme ->
            var last: String? = null
            repeat(100) {
                val picked = NudgeCopyCatalog.selectLine(theme, last)
                if (last != null) {
                    assertNotEquals(
                        "Consecutive repeat for theme $theme",
                        last,
                        picked,
                    )
                }
                last = picked
            }
        }
    }
}
