/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A creative pack: curated set of creatives for a specific goal theme.
 * Loaded from bundled JSON manifests on first launch (one-time ingest).
 */
@Entity(tableName = "creative_packs")
data class CreativePackEntity(
    @PrimaryKey val id: String, // packId from manifest
    val goalTheme: String, // GoalTheme enum name
    val ingestTimestamp: Long, // epoch millis when ingested
    val imageCount: Int, // number of creatives in pack
)

/**
 * A single creative with all required license/attribution metadata.
 * Foreign key ensures orphan cleanup when pack is deleted.
 */
@Entity(
    tableName = "creatives",
    foreignKeys = [
        ForeignKey(
            entity = CreativePackEntity::class,
            parentColumns = ["id"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packId"), Index("subTheme")],
)
data class CreativeEntity(
    @PrimaryKey val id: String, // unique creative ID
    val packId: String, // FK to CreativePackEntity
    val subTheme: String, // sub-theme tag for diversity
    val imagePath: String, // asset path relative to pack
    val attribution: String?, // photographer credit (nullable)
    val licenseUrl: String, // required license URL
    val sha256: String, // file integrity hash
    val baseAppeal: Float = 1.0f, // seed quality score
    val copyPoolJson: String, // serialized list of one-liners
    val unsplashId: String?, // original source ID (nullable for non-Unsplash)
    val photographer: String?, // photographer name (nullable)
    val photographerUrl: String?, // photographer profile URL
    val sourceUrl: String?, // original source URL
    val width: Int = 0, // image width in pixels
    val height: Int = 0, // image height in pixels
)

/** Serializes the copy pool list to/from the JSON string persisted in [CreativeEntity]. */
object CopyPoolCodec {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun encode(pool: List<String>): String = json.encodeToString(pool)

    fun decode(jsonStr: String): List<String> = json.decodeFromString(jsonStr)
}
