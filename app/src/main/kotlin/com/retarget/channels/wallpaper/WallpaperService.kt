/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Thin wrapper around [WallpaperManager] — a dumb pipe per DEVELOPMENT.md
 * architecture ("channels are dumb pipes"). No selection or pacing logic
 * lives here; the scheduler decides *what* and *when*, this class only
 * executes the bitmap swap and keeps revert support.
 *
 * Threading: all methods perform disk/BitmapFactory work; callers must be
 * off the main thread (the rotation worker runs on Dispatchers.IO).
 */
class WallpaperService(
    private val context: Context,
) {
    private val wallpaperManager = WallpaperManager.getInstance(context)

    /** Snapshot of the pre-retarget wallpaper, kept for one-tap revert. */
    data class SavedWallpaper(
        val file: File,
    )

    /**
     * Sets the system wallpaper to the decoded [imageFile].
     *
     * Before the first takeover, the current wallpaper is stashed via
     * [WallpaperManager.getBitmap] so [restorePrevious] can put it back
     * (DEVELOPMENT.md Phase 1: "revert-wallpaper flow").
     *
     * @return true if the wallpaper was applied.
     */
    fun setFromCreative(imageFile: File): Boolean {
        if (!imageFile.isFile) {
            Log.w(TAG, "Creative image missing: ${imageFile.absolutePath}")
            return false
        }
        val bitmap =
            BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: run {
                    Log.w(TAG, "Failed to decode creative: ${imageFile.absolutePath}")
                    return false
                }
        stashCurrentIfNeeded()
        return try {
            wallpaperManager.setBitmap(bitmap)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set wallpaper", e)
            false
        }
    }

    /**
     * Restores the wallpaper captured before the first retarget takeover.
     *
     * @return true if a snapshot existed and was restored.
     */
    fun restorePrevious(): Boolean {
        val snapshot = snapshotFile()
        if (!snapshot.isFile) return false
        val bitmap = BitmapFactory.decodeFile(snapshot.absolutePath) ?: return false
        return try {
            wallpaperManager.setBitmap(bitmap)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore wallpaper", e)
            false
        } finally {
            snapshot.delete()
        }
    }

    private fun stashCurrentIfNeeded() {
        val snapshot = snapshotFile()
        if (snapshot.isFile) return // keep the earliest snapshot: true "previous" state
        try {
            @Suppress("DEPRECATION")
            val current = (wallpaperManager.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return
            snapshot.outputStream().use { current.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        } catch (e: Exception) {
            // Snapshot is best-effort; the swap itself still proceeds.
            Log.w(TAG, "Failed to snapshot current wallpaper", e)
        }
    }

    private fun snapshotFile(): File = File(context.filesDir, SNAPSHOT_FILENAME)

    companion object {
        private const val TAG = "WallpaperService"
        private const val SNAPSHOT_FILENAME = "pre_retarget_wallpaper.jpg"
    }
}
