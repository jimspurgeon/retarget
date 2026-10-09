/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import com.retarget.scheduler.BudgetPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CampaignSettings ticker fields (M3.3, PHASE3-AGENCY.md §4).
 *
 * Covers:
 *   - Lockstep mirror of BudgetPolicy.TICKER_HARD_MAX_PER_DAY
 *   - Default-off (pre-registered decision #4, PHASE3-AGENCY.md §8)
 *   - Validation range 0..MAX_TICKER_PER_DAY
 *   - JSON round-trip with the new fields (backward compatibility relies on
 *     kotlinx.serialization using field defaults for missing keys)
 */
class CampaignSettingsTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun makeSettings(
        tickerEnabled: Boolean = false,
        tickerTargetsPerDay: Int = 2,
    ): CampaignSettings =
        CampaignSettings(
            wallpaperEnabled = true,
            notificationEnabled = false,
            tickerEnabled = tickerEnabled,
            wallpaperTargetsPerDay = 2,
            notificationTargetsPerDay = 3,
            tickerTargetsPerDay = tickerTargetsPerDay,
        )

    @Test
    fun `MAX_TICKER_PER_DAY stays in lockstep with BudgetPolicy hard cap`() {
        assertEquals(
            "CampaignSettings.MAX_TICKER_PER_DAY must equal BudgetPolicy.TICKER_HARD_MAX_PER_DAY",
            BudgetPolicy.TICKER_HARD_MAX_PER_DAY,
            CampaignSettings.MAX_TICKER_PER_DAY,
        )
    }

    @Test
    fun `ticker is off by default`() {
        // Default-constructed settings (missing-keys decode) must leave the ticker OFF —
        // pre-registered decision #4: lock-screen presence is opt-in only.
        val decoded = json.decodeFromString<CampaignSettings>(
            """
            {
                "wallpaperEnabled": true,
                "notificationEnabled": false,
                "wallpaperTargetsPerDay": 2,
                "notificationTargetsPerDay": 3
            }
            """.trimIndent(),
        )
        assertFalse("tickerEnabled must default to false", decoded.tickerEnabled)
    }

    @Test
    fun `fromPreset defaults ticker off with sensible per-day target`() {
        val preset = PresetCatalog.byId("hydration")
        assertTrue("test preset must exist", preset != null)
        val settings = CampaignSettings.fromPreset(preset!!)
        assertFalse(
            "fromPreset must leave tickerEnabled=false (decision #4)",
            settings.tickerEnabled,
        )
        assertEquals(
            "fromPreset ticker target should default to the cap while presets lack a ticker knob",
            CampaignSettings.DEFAULT_TICKER_TARGETS_PER_DAY,
            settings.tickerTargetsPerDay,
        )
    }

    @Test
    fun `serialization round-trips ticker fields`() {
        val settings = makeSettings(tickerEnabled = true, tickerTargetsPerDay = 1)
        val encoded = json.encodeToString(CampaignSettings.serializer(), settings)
        val decoded = json.decodeFromString(CampaignSettings.serializer(), encoded)
        assertEquals(settings, decoded)
        assertTrue(decoded.tickerEnabled)
        assertEquals(1, decoded.tickerTargetsPerDay)
    }

    @Test
    fun `rejects tickerTargetsPerDay above hard max`() {
        assertThrows(IllegalArgumentException::class.java) {
            makeSettings(tickerTargetsPerDay = CampaignSettings.MAX_TICKER_PER_DAY + 1)
        }
    }

    @Test
    fun `accepts tickerTargetsPerDay of zero (cap-as-opt-out)`() {
        // Zero is a valid "channel enabled but no pacing" value, mirroring
        // notificationTargetsPerDay's 0..max range.
        val settings = makeSettings(tickerEnabled = true, tickerTargetsPerDay = 0)
        assertEquals(0, settings.tickerTargetsPerDay)
    }
}
