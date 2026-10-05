/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.creative

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Shared helper for loading creative images from APK assets into cache.
 *
 * Reused by both [WallpaperRotationWorker] and [NotificationChannel] to ensure
 * consistent caching behavior: asset streams are copied to cacheDir on first use,
 * then subsequent reads come from the cached File (no network involved).
 *
 * See PHASE2-CAMPAIGN.md Milestone 2.1: "Image loading: decode from the same
 * cache pattern as BundledPackSource.imageFileFor (File under cacheDir)".
 */
object CreativeImageCache {

    private const val TAG = "CreativeImageCache"
    private const val CACHE_SUBDIR = "creative-cache"

    /**
     * Resolves the on-disk cache file behind [creative], copying it from APK
     * assets on first use. Returns the cached [File] if successful, or null
     * if the asset cannot be accessed.
     *
     * This method implements the shared caching pattern used across channels.
     * Callers should check `file.isFile` before attempting to decode.
     */
    fun cachedFileFor(creative: Creative, context: Context): File? {
        val cacheDir = File(context.cacheDir, CACHE_SUBDIR)
        val cacheFile = File(cacheDir, creative.imagePath)

        if (cacheFile.isFile) {
            return cacheFile
        }

        return try {
            // Try opening as asset path
            val assetStream = context.assets.open(creative.imagePath)
            cacheFile.parentFile?.mkdirs()
            assetStream.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Cached creative image: ${creative.imagePath} -> ${cacheFile.absolutePath}")
            cacheFile
        } catch (e: Exception) {
            // Asset might not exist or path might already be a filesystem path
            Log.w(TAG, "Failed to cache creative asset ${creative.imagePath}: ${e.message}")
            // If it's already a file path, return it directly
            if (cacheFile.exists()) {
                cacheFile
            } else {
                null
            }
        }
    }

    /**
     * Clears the creative cache directory. Useful for testing or cache management.
     */
    fun clearCache(context: Context): Boolean {
        val cacheDir = File(context.cacheDir, CACHE_SUBDIR)
        return if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            true
        } else {
            true // Already clear
        }
    }
}