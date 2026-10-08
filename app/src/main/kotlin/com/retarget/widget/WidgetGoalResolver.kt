/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import com.retarget.goal.GoalEntity

/**
 * Pure displayed-goal resolution for the widget (gatekeeper M2 fix).
 *
 * PHASE3-AGENCY.md §3 — precedence:
 * 1. the user's explicitly chosen goal ID, when it resolves to an active goal;
 * 2. otherwise the FIRST-ADDED active goal (`minByOrNull { createdAt }`).
 *
 * Extracted from [DefaultWidgetStateSource] so the selection semantics are
 * pinned by JVM tests without Robolectric/Room.
 */
object WidgetGoalResolver {
    /**
     * @param activeGoals all currently active goals (order irrelevant).
     * @param preferredGoalId the user's widget preference, or null when unset.
     * @return the goal the widget should display, or null when no goals are active.
     */
    fun resolve(
        activeGoals: List<GoalEntity>,
        preferredGoalId: Long?,
    ): GoalEntity? {
        if (activeGoals.isEmpty()) return null
        return activeGoals.firstOrNull { it.id == preferredGoalId }
            ?: activeGoals.minByOrNull { it.createdAt }
    }
}
