/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads pre-bundled creative packs from APK assets into Room (one-time ingest)
 * and serves pack/creative queries to the rest of the app.
 *
 * Ingestion model: `ensureLoaded()` is idempotent and safe to call from any
 * entry point (Application onCreate, ViewModel init). It scans
 * `creative-packs/<dir>/manifest.json` in assets, skips packs already ingested
 * (by packId), validates each manifest via [CreativeManifestLoader], and
 * inserts pack + creatives in a transaction.
 */
class PersistentCreativeRepository(
    private val context: Context,
    private val dao: CreativePackDao,
    private val manifestLoader: CreativeManifestLoader =
        CreativeManifestLoader(
            // Real asset-backed existence check: an image listed in a manifest
            // must actually be bundled, mirroring checkCreativeLicenses.
            fileExists = { path ->
                val dir = path.substringBeforeLast('/')
                val file = path.substringAfterLast('/', path)
                runCatching { context.assets.list(dir)?.contains(file) ?: false }.getOrDefault(false)
            },
        ),
    private val packsRoot: String = ASSETS_PACKS_ROOT,
) {
    private val ingestMutex = Mutex()

    /**
     * One-time ingest of all bundled packs. Skips packs already present.
     * Safe to call repeatedly; concurrent callers serialize on [ingestMutex].
     *
     * @param nowMs epoch millis recorded as the ingest timestamp
     */
    suspend fun ensureLoaded(nowMs: Long): Unit =
        ingestMutex.withLock {
            withContext(Dispatchers.IO) {
                // `list()` returns immediate child directories on device, but
                // Robolectric flattens the listing into full file paths
                // ("fresh_air/img.jpg"). Normalizing to the first path segment
                // and deduplicating handles both shapes.
                val dirNames =
                    context.assets
                        .list(packsRoot)
                        ?.map { it.substringBefore('/') }
                        ?.distinct()
                        ?.sorted()
                        ?: emptyList()
                for (dir in dirNames) {
                    val manifestPath = "$packsRoot/$dir/manifest.json"
                    val manifestJson =
                        runCatching {
                            context.assets
                                .open(manifestPath)
                                .bufferedReader()
                                .use { it.readText() }
                        }.getOrNull()
                    if (manifestJson == null) continue // not a pack dir; skip
                    val (packId, creatives) =
                        try {
                            manifestLoader.loadManifest(manifestJson, "$packsRoot/$dir")
                        } catch (e: CreativeManifestLoader.ManifestValidationException) {
                            // Invalid bundled packs are a build-time bug; refuse to
                            // crash the app but leave the pack un-ingested and
                            // loud in logs for diagnosis.
                            android.util.Log.w(TAG, "Skipping invalid pack in '$dir': ${e.message}")
                            continue
                        }
                    if (dao.packExists(packId)) continue
                    val goalTheme = goalThemeForPack(packId)
                    dao.insertPack(
                        CreativePackEntity(
                            id = packId,
                            goalTheme = goalTheme,
                            ingestTimestamp = nowMs,
                            imageCount = creatives.size,
                        ),
                    )
                    // Resolve image paths to full asset paths for consumers
                    // (wallpaper decoder opens them directly from assets).
                    dao.insertCreatives(
                        creatives.map { it.copy(imagePath = "$packsRoot/$dir/${it.imagePath}") },
                    )
                }
            }
        }

    /** Reactive list of all ingested packs. */
    fun loadAllPacks(): Flow<List<CreativePackEntity>> = dao.loadAllPacks()

    /** Reactive list of packs matching a goal theme. */
    fun getPacksByGoalTheme(theme: GoalTheme): Flow<List<CreativePackEntity>> = dao.getPacksByGoalTheme(theme.name)

    /**
     * Candidate creatives for the themes of the currently active goals.
     * Falls back to all creatives when no theme matches (first-launch safety).
     */
    suspend fun getCandidatesForActiveGoals(activeGoalThemes: Collection<GoalTheme>): List<Creative> =
        dao.getCandidatesForActiveGoals(activeGoalThemes.map { it.name }).map { it.toDomain() }

    /** All creatives in a pack, mapped to domain objects. */
    suspend fun getCreativesForPack(packId: String): List<Creative> = dao.getCreativesForPack(packId).map { it.toDomain() }

    companion object {
        const val ASSETS_PACKS_ROOT = "creative-packs"
        private const val TAG = "PersistentCreativeRepo"

        /**
         * Maps a packId to its goal theme. Bundled packs are curated per
         * PresetCatalog themes; unknown packs default to GENERAL_WELLNESS
         * rather than being rejected (forward compatibility with Phase 3
         * remote packs).
         */
        fun goalThemeForPack(packId: String): String =
            when (packId) {
                "fresh-air" -> GoalTheme.NATURE_TIME.name
                "fruit", "vegetables", "fruit-and-veg" -> GoalTheme.PLANT_BASED_WHOLE_FOODS.name
                "hydration" -> GoalTheme.HYDRATION.name
                else -> GoalTheme.GENERAL_WELLNESS.name
            }
    }
}

/** Map a persisted row to the domain [Creative]. */
fun CreativeEntity.toDomain(): Creative =
    Creative(
        id = id,
        packId = packId,
        goalTheme = GoalTheme.valueOf(goalThemeForEntity(packId)),
        subTheme = subTheme,
        copyPool = CopyPoolCodec.decode(copyPoolJson),
        imagePath = imagePath,
        attribution = attribution,
        licenseUrl = licenseUrl,
        baseAppeal = baseAppeal,
    )

private fun goalThemeForEntity(packId: String): String = PersistentCreativeRepository.goalThemeForPack(packId)
