/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import com.retarget.goal.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM-only tests for displayed-goal resolution (gatekeeper M2 fix).
 *
 * Pins PHASE3-AGENCY.md §3 semantics: explicit per-goal widget preference
 * wins; absent preference (or a preference pointing at a deleted/inactive
 * goal) falls back to the FIRST-ADDED active goal.
 */
class WidgetGoalResolverTest {
    private fun goal(
        id: Long,
        createdAt: Long,
    ) = GoalEntity(
        id = id,
        presetId = "preset-$id",
        displayName = "Goal $id",
        createdAt = createdAt,
        active = true,
        settingsJson = "{}",
    )

    @Test
    fun `empty goal list resolves to null`() {
        assertNull(WidgetGoalResolver.resolve(emptyList(), preferredGoalId = null))
    }

    @Test
    fun `absent preference falls back to first-added goal`() {
        val goals = listOf(goal(id = 3, createdAt = 300), goal(id = 1, createdAt = 100), goal(id = 2, createdAt = 200))
        assertEquals(1L, WidgetGoalResolver.resolve(goals, preferredGoalId = null)?.id)
    }

    @Test
    fun `explicit preference wins over first-added`() {
        val goals = listOf(goal(id = 1, createdAt = 100), goal(id = 2, createdAt = 200))
        assertEquals(2L, WidgetGoalResolver.resolve(goals, preferredGoalId = 2)?.id)
    }

    @Test
    fun `preference pointing at deleted or inactive goal falls back to first-added active`() {
        // Goal 99 is not in the active list (deleted or deactivated).
        val goals = listOf(goal(id = 5, createdAt = 500), goal(id = 4, createdAt = 400))
        assertEquals(4L, WidgetGoalResolver.resolve(goals, preferredGoalId = 99)?.id)
    }

    @Test
    fun `single goal is returned regardless of preference`() {
        val goals = listOf(goal(id = 7, createdAt = 700))
        assertEquals(7L, WidgetGoalResolver.resolve(goals, preferredGoalId = null)?.id)
        assertEquals(7L, WidgetGoalResolver.resolve(goals, preferredGoalId = 42)?.id)
    }

    @Test
    fun `ties on createdAt break by list position deterministically`() {
        // minByOrNull is stable: first element with the minimal key wins.
        val goals = listOf(goal(id = 2, createdAt = 100), goal(id = 1, createdAt = 100))
        assertEquals(2L, WidgetGoalResolver.resolve(goals, preferredGoalId = null)?.id)
    }
}
