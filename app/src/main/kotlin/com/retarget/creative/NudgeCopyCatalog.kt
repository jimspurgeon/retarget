/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

/**
 * Static copy catalog for bundled creative packs.
 *
 * Bundled pack manifests describe images only (photographer, license, file);
 * they carry no copy lines. Historically `BundledPackSource` built Creatives
 * with `copyPool = emptyList()`, and `NotificationDeliveryWorker` crashed with
 * `NoSuchElementException` calling `copyPool.random()` (v0.3.1 regression:
 * notifications permanently FAILED after first attempt).
 *
 * This catalog supplies theme-appropriate one-liners so bundled creatives
 * always have non-empty copy pools. Local-first: no network, no tracking.
 */
object NudgeCopyCatalog {

    private val byTheme: Map<GoalTheme, List<String>> =
        mapOf(
            GoalTheme.HYDRATION to
                listOf(
                    "Your body runs better hydrated. Grab a glass.",
                    "Halfway to your water goal — one sip closer.",
                    "Clear mind, steady energy: it starts with water.",
                    "Feeling sluggish? Water first.",
                    "Small drink, big difference. Top up now.",
                    "Hydration is the cheapest upgrade to your day.",
                ),
            GoalTheme.PLANT_BASED_WHOLE_FOODS to
                listOf(
                    "Crunch counts. An apple beats another scroll.",
                    "Colors on your plate, colors in your day.",
                    "Fuel the body you live in — grab something fresh.",
                    "Real food, real energy. No refill needed.",
                    "Your gut votes on your mood. Feed it well.",
                    "One serving now keeps the afternoon slump away.",
                ),
            GoalTheme.NATURE_TIME to
                listOf(
                    "The outside is free and always open.",
                    "Ten minutes of daylight resets your head.",
                    "Your brain evolved outdoors. Take it home.",
                    "Step out. The sky has no notifications.",
                    "Fresh air is a shortcut to calm.",
                    "Move your legs; the trail is waiting.",
                ),
            GoalTheme.BREATHING to
                listOf(
                    "Three slow breaths. Right where you are.",
                    "Exhale longer than you inhale — instant calm.",
                    "A minute of stillness beats an hour of noise.",
                    "Shoulders down. Jaw loose. Breathe.",
                    "Reset your nervous system in sixty seconds.",
                    "Pause. The task will wait. Your breath won't.",
                ),
            GoalTheme.GENERAL_WELLNESS to
                listOf(
                    "Tiny habits, compounding returns.",
                    "Future-you is watching. Make one good move.",
                    "Progress over perfection — one rep counts.",
                    "Your goals, quietly winning the day.",
                    "Small step now. Momentum later.",
                    "Showing up is the whole trick.",
                ),
        )

    /** Copy pool for [theme]; never empty. */
    fun forTheme(theme: GoalTheme): List<String> =
        byTheme[theme] ?: byTheme.getValue(GoalTheme.GENERAL_WELLNESS)
}
