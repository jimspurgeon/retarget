/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics.export

import com.retarget.analytics.CheckInEntity
import com.retarget.creative.Channel
import com.retarget.creative.ExposureEntity
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM round-trip tests for the pure-Kotlin export serializer.
 * All data is synthetic (AGENTS.md §1).
 */
class ExportSerializerTest {

    // ------------------------------------------------------------------
    // Synthetic fixtures
    // ------------------------------------------------------------------

    private fun settings(
        wallpaper: Boolean = true,
        notification: Boolean = false,
        wallpaperPerDay: Int = 3,
        notificationPerDay: Int = 0,
    ): CampaignSettings =
        CampaignSettings(
            wallpaperEnabled = wallpaper,
            notificationEnabled = notification,
            wallpaperTargetsPerDay = wallpaperPerDay,
            notificationTargetsPerDay = notificationPerDay,
        )

    /** 3 goals, mixed active/inactive, 2 different presets, varied settings. */
    private fun syntheticGoals(): List<GoalEntity> =
        listOf(
            GoalEntity(
                id = 1,
                presetId = "hydration.basic",
                displayName = "Drink water \uD83D\uDCA7",
                createdAt = 1_700_000_000_000L,
                active = true,
                settingsJson = Json.encodeToString(settings(wallpaperPerDay = 4)),
            ),
            GoalEntity(
                id = 2,
                presetId = "movement.walk",
                displayName = "More walking",
                createdAt = 1_700_100_000_000L,
                active = false,
                settingsJson =
                    Json.encodeToString(
                        settings(
                            wallpaper = false,
                            notification = true,
                            wallpaperPerDay = 1,
                            notificationPerDay = 3,
                        ),
                    ),
            ),
            GoalEntity(
                id = 3,
                presetId = "hydration.basic",
                displayName = "Wasserkopf 行くぞ 🚀",
                createdAt = 1_700_200_000_000L,
                active = true,
                settingsJson =
                    Json.encodeToString(
                        settings(
                            wallpaper = true,
                            notification = true,
                            wallpaperPerDay = 2,
                            notificationPerDay = 2,
                        ),
                    ),
            ),
        )

    /** 24 exposure events spanning all channel categories. */
    private fun syntheticExposures(): List<ExposureEntity> =
        (0 until 24).map { i ->
            ExposureEntity(
                id = i.toLong() + 100,
                creativeId = "creative-$i",
                subTheme = if (i % 2 == 0) "water-glass" else "walking-boot",
                channel = if (i % 3 == 0) Channel.NOTIFICATION else Channel.WALLPAPER,
                atMs = 1_700_500_000_000L + i * 86_400_000L,
            )
        }

    /** 11 check-ins including null and non-null notes with unicode. */
    private fun syntheticCheckIns(): List<CheckInEntity> =
        (0 until 11).map { i ->
            CheckInEntity(
                id = i.toLong() + 500,
                goalId = 1L + (i % 3), // cycles across the 3 goals
                atMs = 1_700_600_000_000L + i * 43_200_000L,
                notes = when (i % 3) {
                    0 -> null
                    1 -> "felt great \uD83D\uDE0A"
                    else -> "walking im Regen ☔ №${i + 1}"
                },
            )
        }

    private fun syntheticPayload(): ExportPayload =
        ExportPayload.fromRows(syntheticGoals(), syntheticExposures(), syntheticCheckIns())

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    fun `synthetic dataset round-trips through serialize-deserialize`() {
        val payload = syntheticPayload()

        val restored = ExportSerializer.deserialize(ExportSerializer.serialize(payload))

        assertEquals(payload, restored)
    }

    @Test
    fun `output json declares the current schema version`() {
        val element = Json.parseToJsonElement(ExportSerializer.serialize(syntheticPayload()))

        assertEquals(ExportPayload.SCHEMA_VERSION, element.jsonObject["schemaVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals(ExportSerializer.CURRENT_SCHEMA_VERSION, ExportPayload.SCHEMA_VERSION)
    }

    @Test
    fun `serialization is deterministic`() {
        val payload = syntheticPayload()

        val first = ExportSerializer.serialize(payload)
        val second = ExportSerializer.serialize(payload)

        assertEquals(first, second)
    }

    @Test
    fun `keys appear in declaration order`() {
        val rendered = ExportSerializer.serialize(syntheticPayload())

        val schemaIdx = rendered.indexOf("\"schemaVersion\"")
        val goalsIdx = rendered.indexOf("\"goals\"")
        val exposuresIdx = rendered.indexOf("\"exposureEvents\"")
        val checkInsIdx = rendered.indexOf("\"checkIns\"")

        assertTrue(schemaIdx >= 0)
        assertTrue(goalsIdx > schemaIdx)
        assertTrue(exposuresIdx > goalsIdx)
        assertTrue(checkInsIdx > exposuresIdx)
    }

    @Test
    fun `goal keys appear in declaration order`() {
        val rendered = ExportSerializer.serialize(syntheticPayload())

        val presetIdx = rendered.indexOf("\"presetId\"")
        val nameIdx = rendered.indexOf("\"displayName\"")
        val createdIdx = rendered.indexOf("\"createdAtMs\"")
        val activeIdx = rendered.indexOf("\"active\"")
        val settingsIdx = rendered.indexOf("\"settings\"")

        assertTrue(presetIdx >= 0)
        assertTrue(nameIdx > presetIdx)
        assertTrue(createdIdx > nameIdx)
        assertTrue(activeIdx > createdIdx)
        assertTrue(settingsIdx > activeIdx)
    }

    @Test
    fun `empty state serializes with schema version intact`() {
        val payload = ExportPayload(schemaVersion = ExportSerializer.CURRENT_SCHEMA_VERSION)

        val element = Json.parseToJsonElement(ExportSerializer.serialize(payload)).jsonObject

        assertEquals(ExportPayload.SCHEMA_VERSION, element["schemaVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, element["goals"]!!.jsonArray.size)
        assertEquals(0, element["exposureEvents"]!!.jsonArray.size)
        assertEquals(0, element["checkIns"]!!.jsonArray.size)
        assertEquals(payload, ExportSerializer.deserialize(ExportSerializer.serialize(payload)))
    }

    @Test
    fun `unicode in names and notes survives round-trip`() {
        val goal =
            GoalEntity(
                id = 42,
                presetId = "misc.emoji",
                displayName = "目標 🎯 café",
                createdAt = 1L,
                active = true,
                settingsJson = Json.encodeToString(settings()),
            )
        val checkIn =
            CheckInEntity(
                id = 7,
                goalId = 42,
                atMs = 2L,
                notes = "קפה ☕ — done!",
            )
        val payload = ExportPayload.fromRows(listOf(goal), emptyList(), listOf(checkIn))

        val restored = ExportSerializer.deserialize(ExportSerializer.serialize(payload))

        assertEquals("目標 🎯 café", restored.goals.single().displayName)
        assertEquals("קפה ☕ — done!", restored.checkIns.single().notes)
    }

    @Test
    fun `channel exports as stable name not ordinal`() {
        val payload = syntheticPayload()

        val element = Json.parseToJsonElement(ExportSerializer.serialize(payload)).jsonObject
        val channels = element["exposureEvents"]!!.jsonArray.map { it.jsonObject["channel"]!!.jsonPrimitive.content }

        assertTrue(channels.contains("WALLPAPER"))
        assertTrue(channels.contains("NOTIFICATION"))
    }

    @Test
    fun `room autogen ids and settingsJson are absent from output`() {
        val rendered = ExportSerializer.serialize(syntheticPayload())

        assertTrue(rendered.contains("settings")) // nested object present...
        assertTrue(!rendered.contains("settingsJson")) // ...not a raw JSON string blob
        assertTrue(!rendered.contains("\"id\"")) // Room autogen ids dropped
        assertTrue(!rendered.contains("\"goalId\""))
    }

    @Test
    fun `check-in with dangling goal maps to unknown marker`() {
        val orphan =
            CheckInEntity(
                id = 999,
                goalId = 12345, // not in the goal list
                atMs = 5L,
                notes = null,
            )
        val payload = ExportPayload.fromRows(syntheticGoals(), emptyList(), listOf(orphan))

        assertEquals("__unknown_goal__", payload.checkIns.single().goalPresetId)
    }
}
