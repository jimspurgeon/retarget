/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.glance.ImageProvider

/**
 * Widget-safe creative decoding (gatekeeper M1 fix, M3.2).
 *
 * Bundled creatives are full-resolution photos (~4000x6000 px); decoding them
 * naively costs ~100 MB ARGB and can throw [OutOfMemoryError] — a
 * `java.lang.Error` that no `catch (Exception)` stops. This helper decodes
 * bounds-first (`inJustDecodeBounds` + `inSampleSize`) so the widget only ever
 * allocates a small thumbnail, and degrades to `null` on ANY failure
 * ([Throwable], not just [Exception]) so the caller falls back to a solid
 * background color instead of crashing the process.
 *
 * Decoding must run OFF the composition/UI thread (e.g. in `provideGlance` on
 * `Dispatchers.IO`); the helper itself is synchronous and thread-safe.
 */
object WidgetCreativeDecoder {
    /** Generous upper bound for the widget footprint (~2x2 cells of a 2x2-resizable widget). */
    private const val MAX_WIDGET_DIMEN_PX = 640

    /**
     * Decodes a subsampled bitmap suitable for the widget background.
     * Returns null when the file is missing, un-decodable, or decoding
     * fails in any way (including [OutOfMemoryError]) — the caller's signal
     * to fall back to a solid color.
     *
     * The decode functions are parameters (defaulting to [BitmapFactory.decodeFile])
     * so tests can exercise the fallback paths deterministically — Robolectric's
     * BitmapFactory shadow fabricates success for missing files, hiding them.
     */
    fun decodeWidgetBitmap(
        path: String?,
        boundsDecode: (String, BitmapFactory.Options) -> Bitmap? = BitmapFactory::decodeFile,
        sampleDecode: (String, BitmapFactory.Options) -> Bitmap? = BitmapFactory::decodeFile,
    ): Bitmap? {
        if (path == null) return null
        return try {
            decodeBoundsFirst(path, boundsDecode, sampleDecode)
        } catch (t: Throwable) {
            // Deliberately Throwable: a pathological image (or low-memory
            // conditions) must degrade to the fallback color, never crash.
            null
        }
    }

    /** Decodes an [ImageProvider] or null (fallback color) for the given path. */
    fun decodeWidgetImageProvider(path: String?): ImageProvider? = decodeWidgetBitmap(path)?.let { ImageProvider(it) }

    private fun decodeBoundsFirst(
        path: String,
        boundsDecode: (String, BitmapFactory.Options) -> Bitmap?,
        sampleDecode: (String, BitmapFactory.Options) -> Bitmap?,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        boundsDecode(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null // not an image / unreadable

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_WIDGET_DIMEN_PX)
            }
        return sampleDecode(path, options)
    }

    /**
     * Largest power-of-two sample size keeping BOTH dimensions >= [targetPx]
     * where possible (classic bounds-first recipe): each halving cuts memory
     * use 4x, and widget scaling tolerates the power-of-two granularity.
     */
    internal fun computeInSampleSize(
        width: Int,
        height: Int,
        targetPx: Int,
    ): Int {
        var inSampleSize = 1
        var largestDim = maxOf(width, height)
        while (largestDim / (inSampleSize * 2) >= targetPx) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
