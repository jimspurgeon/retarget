/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.wallpaper

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.retarget.creative.Channel
import com.retarget.creative.Creative
import com.retarget.creative.CreativeImageCache
import com.retarget.creative.CreativeRotator
import com.retarget.creative.NudgeCopyCatalog
import com.retarget.creative.RoomExposureLedger
import com.retarget.goal.GoalDatabase
import com.retarget.goal.PresetCatalog
import com.retarget.scheduler.WallpaperRotationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

/**
 * One wallpaper rotation cycle (DEVELOPMENT.md Phase 1, "fatigue-aware
 * wallpaper rotator via WorkManager").
 *
 * This class is the Android-side execution shell only: it wires the pure
 * domain pieces (rotation policy, CreativeRotator, exposure ledger) to the
 * wallpaper channel pipe. No selection or pacing logic lives here —
 * DEVELOPMENT.md architecture: "channels are dumb pipes".
 *
 * Constructed by the default WorkManager factory; dependencies are pulled
 * from [GoalDatabase] and bundled assets (see [BundledPackSource]).
 */
class WallpaperRotationWorker(
    appCtx: Context,
    params: WorkerParameters,
) : CoroutineWorker(appCtx, params) {
    private val db = GoalDatabase.get(applicationContext)
    private val ledger = RoomExposureLedger(db.exposureDao())
    private val rotator = CreativeRotator()
    private val wallpaperService = WallpaperService(applicationContext)
    private val packSource = BundledPackSource(applicationContext)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            // Quiet hours are sacred (pre-registered decision #3): never rotate
            // 22:00–07:00. Skip this cycle; the periodic schedule tries again later.
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (!WallpaperRotationPolicy.shouldRotateNow(hour)) {
                Log.i(TAG, "Quiet hours active (hour=$hour); skipping rotation")
                return@withContext Result.success()
            }

            val activeGoals = db.goalDao().observeActive().first().filter { it.settings.wallpaperEnabled }
            if (activeGoals.isEmpty()) {
                Log.i(TAG, "No active goals with wallpaper enabled; nothing to do")
                return@withContext Result.success()
            }

            val candidates = packSource.creativesFor(activeGoals)
            if (candidates.isEmpty()) {
                Log.i(TAG, "No bundled creatives for active goals; skipping")
                return@withContext Result.success()
            }

            val now = System.currentTimeMillis()
            val selected = rotator.selectNext(candidates, ledger, now)
            if (selected == null) {
                Log.i(TAG, "Rotator returned no selection")
                return@withContext Result.success()
            }

            val image = packSource.imageFileFor(selected)
            if (image == null || !image.isFile) {
                Log.w(TAG, "Creative image missing for ${selected.id}; retrying later")
                return@withContext Result.retry()
            }

            if (!wallpaperService.setFromCreative(image)) {
                Log.w(TAG, "Wallpaper apply failed; retrying later")
                return@withContext Result.retry()
            }

            ledger.recordExposure(selected.id, selected.subTheme, Channel.WALLPAPER, now)
            Log.i(TAG, "Wallpaper set to creative=${selected.id} subTheme=${selected.subTheme}")
            Result.success()
        }

    companion object {
        private const val TAG = "WallpaperRotationWorker"
    }
}

/**
 * Loads bundled creative packs from APK assets for the wallpaper channel.
 *
 * SEAM NOTE (Phase 1): the ingestion loader (separate workstream) is expected
 * to land a shared CreativePackRepository; when it does, this minimal
 * asset-reader should be retired in favor of it. It exists so the rotation
 * pipeline works end-to-end with the packs already bundled in assets.
 *
 * Sub-themes: manifests may carry a per-image "subTheme" (ingestion pipeline
 * emits it); images without one fall back to the pack id, so the rotator's
 * diversity factor degrades gracefully instead of breaking.
 */
class BundledPackSource(
    private val context: Context,
) {
    /** Packs bundled in assets, mapped to the goal themes they serve. */
    private val packThemes: Map<String, com.retarget.creative.GoalTheme> =
        mapOf(
            "fresh_air" to com.retarget.creative.GoalTheme.NATURE_TIME,
            "fruit" to com.retarget.creative.GoalTheme.PLANT_BASED_WHOLE_FOODS,
            "vegetables" to com.retarget.creative.GoalTheme.PLANT_BASED_WHOLE_FOODS,
            "hydration" to com.retarget.creative.GoalTheme.HYDRATION,
        )

    fun creativesFor(goals: List<com.retarget.goal.GoalEntity>): List<Creative> {
        val wantedThemes = goals.mapNotNull { PresetCatalog.byId(it.presetId)?.goalTheme }.toSet()
        if (wantedThemes.isEmpty()) return emptyList()
        return packThemes
            .filterValues { it in wantedThemes }
            .keys
            .flatMap { packId -> loadPack(packId) }
    }

    /**
     * Resolves the on-disk cache file behind [creative], using the shared
     * [CreativeImageCache] helper (PHASE2-CAMPAIGN.md Milestone 2.1).
     *
     * DELEGATION: This method delegates to CreativeImageCache.cachedFileFor()
     * to ensure consistent caching behavior across all channels.
     */
    fun imageFileFor(creative: Creative): File? {
        return CreativeImageCache.cachedFileFor(creative, context)
    }

    private fun loadPack(packId: String): List<Creative> {
        val manifestJson =
            try {
                context.assets.open("$PACKS_DIR/$packId/manifest.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.w("BundledPackSource", "No manifest for pack '$packId'", e)
                return emptyList()
            }
        val manifest = json.decodeFromString(PackManifest.serializer(), manifestJson)
        return manifest.images.map { entry ->
            Creative(
                id = "${packId}/${entry.unsplashId}",
                packId = packId,
                goalTheme = packThemes[packId] ?: com.retarget.creative.GoalTheme.GENERAL_WELLNESS,
                subTheme = entry.subTheme ?: packId,
                copyPool = NudgeCopyCatalog.forTheme(
                    packThemes[packId] ?: com.retarget.creative.GoalTheme.GENERAL_WELLNESS,
                ),
                // Bundled asset images are addressable only through AssetManager;
                // imageFileFor copies them to the cache dir before decoding.
                imagePath = "$PACKS_DIR/$packId/${entry.file}",
                attribution = entry.photographer,
                licenseUrl = entry.licenseUrl,
            )
        }
    }

    /** Opens the asset stream behind [creative]'s image, or null. */
    fun openAsset(creative: Creative) =
        try {
            context.assets.open(creative.imagePath)
        } catch (e: Exception) {
            null
        }

    @Serializable
    private data class PackManifest(
        val packId: String,
        val images: List<PackImage>,
    )

    @Serializable
    private data class PackImage(
        val unsplashId: String,
        val file: String,
        val photographer: String? = null,
        val licenseUrl: String,
        val subTheme: String? = null,
    )

    companion object {
        private const val PACKS_DIR = "creative-packs"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
