/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Per-goal notification snooze state (hotfix for v0.3.x notification spam).
 *
 * When the user taps "Snooze 2h" on a notification, the goal is suppressed
 * from notification delivery until the snooze window elapses. Previously the
 * snooze action merely re-scheduled a legacy per-goal periodic worker that the
 * consolidated global delivery worker ignored, so snoozing had no effect.
 *
 * State lives in a dedicated SharedPreferences file ([FILE_NAME]) keyed by goal
 * ID: `snooze_goal_<id>` → epoch millis when the snooze ends. Entries expire
 * lazily on read (cleared once the window has elapsed). Local-only by design;
 * purgeable in one call for full reset (AGENTS.md §2: reversible, deletable).
 */
object SnoozeSuppression {
    private const val TAG = "SnoozeSuppression"

    /** Dedicated prefs file, isolated from other app state. */
    const val FILE_NAME = "nudge_suppression"

    private const val KEY_PREFIX = "snooze_goal_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun keyFor(goalId: Long): String = "${KEY_PREFIX}${goalId}"

    /**
     * Marks the goal as snoozed until [untilMs] (epoch millis). Overwrites any
     * in-flight snooze for the same goal.
     */
    fun snooze(
        context: Context,
        goalId: Long,
        untilMs: Long,
    ) {
        prefs(context).edit().putLong(keyFor(goalId), untilMs).apply()
        Log.i(TAG, "Goal $goalId snoozed until $untilMs")
    }

    /**
     * True while the goal's snooze window is active at [nowMs]. Expired entries
     * are cleared lazily on first read past expiry so the file does not grow
     * unboundedly with stale keys.
     */
    fun isSnoozed(
        context: Context,
        goalId: Long,
        nowMs: Long,
    ): Boolean {
        val prefs = prefs(context)
        val untilMs = prefs.getLong(keyFor(goalId), Long.MIN_VALUE)
        if (untilMs == Long.MIN_VALUE) return false
        if (nowMs < untilMs) return true

        // Window elapsed: clear the stale entry while we hold the instance.
        prefs.edit().remove(keyFor(goalId)).apply()
        return false
    }

    /** Clears snooze state for one goal (e.g., user re-enables the channel). */
    fun clear(
        context: Context,
        goalId: Long,
    ) {
        prefs(context).edit().remove(keyFor(goalId)).apply()
    }

    /** Clears all snooze state (full reset / data wipe). */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
