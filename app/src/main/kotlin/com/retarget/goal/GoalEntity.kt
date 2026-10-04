/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A user-installed goal ("brand") running a campaign instantiated from a preset.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: String,
    val displayName: String,
    val createdAt: Long, // epoch millis
    @ColumnInfo(defaultValue = "1") val active: Boolean = true,
    val settingsJson: String,
) {
    val settings: CampaignSettings
        get() = GoalConverters.json.decodeFromString(settingsJson)
}

/** Room converters for the embedded settings JSON. */
class GoalConverters {
    @TypeConverter
    fun settingsToJson(settings: CampaignSettings): String = Json.encodeToString(settings)

    @TypeConverter
    fun jsonToSettings(json: String): CampaignSettings = Json.decodeFromString(json)

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
