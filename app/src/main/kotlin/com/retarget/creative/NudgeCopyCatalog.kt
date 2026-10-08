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
 *
 * Lines are Kotlin constants by design (research-backed microcopy), not
 * resources/strings.xml entries — see the change report for the localization
 * tradeoff. Voice: warm, plain-language, specific, self-directed. No guilt,
 * fake urgency, or streak-shaming.
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
                    "A glass of water now softens the afternoon drag.",
                    "Even mild dehydration shows up as fatigue. Refill.",
                    "Your brain is mostly water. Give it what it runs on.",
                    "Thirst often masquerades as hunger. Water first.",
                    "Keep a glass within reach — pouring is the hard part.",
                    "One glass down beats none. Pour another.",
                ),
            GoalTheme.PLANT_BASED_WHOLE_FOODS to
                listOf(
                    "Crunch counts. An apple beats another scroll.",
                    "Colors on your plate, colors in your day.",
                    "Fuel the body you live in — grab something fresh.",
                    "Real food, real energy. No refill needed.",
                    "Your gut votes on your mood. Feed it well.",
                    "One serving now keeps the afternoon slump away.",
                    "Wash the fruit now; snack-you will be grateful.",
                    "Fiber today, steadier energy tomorrow.",
                    "Grab the apple you already bought.",
                    "Plants first at your next meal — the rest follows.",
                    "Your microbiome thrives on variety. Add a color.",
                    "Whole foods keep you fuller than the vending machine.",
                ),
            GoalTheme.NATURE_TIME to
                listOf(
                    "The outside is free and always open.",
                    "Ten minutes of daylight resets your head.",
                    "Your brain evolved outdoors. Take it home.",
                    "Step out. The sky has no notifications.",
                    "Fresh air is a shortcut to calm.",
                    "Move your legs; the trail is waiting.",
                    "Cloud-watching counts as a break. Look up.",
                    "Morning daylight today helps you sleep tonight.",
                    "A short walk outside beats another cup of coffee.",
                    "Grass underfoot, sky overhead. Five minutes.",
                    "A window view is a teaser. Open the door.",
                    "Take your lunch somewhere with a breeze.",
                ),
            GoalTheme.BREATHING to
                listOf(
                    "Three slow breaths. Right where you are.",
                    "Exhale longer than you inhale — instant calm.",
                    "A minute of stillness beats an hour of noise.",
                    "Shoulders down. Jaw loose. Breathe.",
                    "Reset your nervous system in sixty seconds.",
                    "Pause. The task will wait. Your breath won't.",
                    "Box breathing: in four, hold four, out four, hold four.",
                    "Unclench. One slow exhale, starting now.",
                    "Stress shrinks when your breath lengthens.",
                    "Sixty seconds of slow breathing, wherever you are.",
                    "Let the belly rise. Let the shoulders fall.",
                    "Breathe out twice as long as in. Notice the shift.",
                ),
            GoalTheme.GENERAL_WELLNESS to
                listOf(
                    "Tiny habits, compounding returns.",
                    "Future-you is watching. Make one good move.",
                    "Progress over perfection — one rep counts.",
                    "Your goals, quietly winning the day.",
                    "Small step now. Momentum later.",
                    "Showing up is the whole trick.",
                    "Stack the new habit onto one you already have.",
                    "Done beats perfect. Take the small win.",
                    "Consistency is quieter than motivation — and stronger.",
                    "Two minutes of it still counts. Do the short version.",
                    "You don't need the whole plan. Just the first rep.",
                    "Momentum is built, not found. Build a little.",
                ),
        )

    /** Copy pool for [theme]; never empty. */
    fun forTheme(theme: GoalTheme): List<String> =
        byTheme[theme] ?: byTheme.getValue(GoalTheme.GENERAL_WELLNESS)

    /**
     * Select a copy line for [theme], avoiding an immediate repeat of
     * [lastShownLine] (the line last delivered for this theme) whenever the
     * pool has at least two distinct lines.
     *
     * Pure function — no Android dependencies — so it is unit-testable on
     * the JVM. Callers persist the returned line and pass it back as
     * [lastShownLine] on the next delivery.
     */
    fun selectLine(theme: GoalTheme, lastShownLine: String?): String =
        selectFrom(forTheme(theme), lastShownLine)

    /**
     * Shared selection logic: exclude [lastShownLine] when the pool offers an
     * alternative; otherwise draw uniformly from what remains.
     */
    internal fun selectFrom(pool: List<String>, lastShownLine: String?): String {
        if (pool.isEmpty()) {
            throw IllegalStateException("Copy pool must not be empty")
        }
        if (pool.size == 1) return pool.first()
        val candidates = if (lastShownLine == null) pool else pool.filterNot { it == lastShownLine }
        return candidates.random()
    }
}
