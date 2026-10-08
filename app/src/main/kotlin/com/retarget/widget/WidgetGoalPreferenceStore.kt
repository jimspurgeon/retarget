/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence for the per-goal widget preference (M3.2, gatekeeper M2 fix).
 *
 * PHASE3-AGENCY.md §3: "Widget add flow sets per-goal widget preference".
 * The user picks which goal the home-screen widget displays when pinning it
 * (or later, from the widget's configure screen). When no preference is
 * set — or the preferred goal is no longer active — the widget falls back
 * to the FIRST-ADDED active goal, matching the plan's letter.
 *
 * SharedPreferences is a tiny, local-only key/value store: behavioral data
 * stays on-device (AGENTS.md §1), no new permissions involved.
 */
@Singleton
class WidgetGoalPreferenceStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        /** The user's chosen widget goal ID, or null when unset. */
        fun get(): Long? =
            prefs.getLong(KEY_WIDGET_GOAL_ID, DEFAULT_GOAL_ID).takeIf { it != DEFAULT_GOAL_ID }

        /** Persists the goal the widget should display. */
        fun set(goalId: Long) {
            prefs.edit().putLong(KEY_WIDGET_GOAL_ID, goalId).apply()
        }

        /** Clears the preference (fall back to first-added active goal). */
        fun clear() {
            prefs.edit().remove(KEY_WIDGET_GOAL_ID).apply()
        }

        private companion object {
            const val PREFS_FILE = "retarget_widget_prefs"
            const val KEY_WIDGET_GOAL_ID = "widget_goal_id"

            /** Sentinel for "unset" (SharedPreferences has no nullable Long getter). */
            const val DEFAULT_GOAL_ID = -1L
        }
    }
