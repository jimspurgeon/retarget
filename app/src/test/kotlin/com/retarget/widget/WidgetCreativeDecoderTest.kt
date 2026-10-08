/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for [WidgetCreativeDecoder] decode safety (gatekeeper M1 fix).
 *
 * Verifies that:
 * - Null path / missing file / un-decodable input return null (graceful
 *   degradation to the fallback color, never a crash)
 * - inSampleSize calculation subsamples large images to widget bounds
 * - Even [OutOfMemoryError] from the decoder is swallowed into null
 *
 * Negative paths are exercised via injected decode lambdas because
 * Robolectric's BitmapFactory shadow fabricates success for missing files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetCreativeDecoderTest {
    @Test
    fun `null path returns null`() {
        assertNull(WidgetCreativeDecoder.decodeWidgetBitmap(null))
    }

    @Test
    fun `missing file returns null`() {
        // Simulate a decoder that reports no dimensions for a missing file
        // (real BitmapFactory does this; Robolectric's shadow fakes success).
        assertNull(
            WidgetCreativeDecoder.decodeWidgetBitmap(
                "/nonexistent/path.jpg",
                boundsDecode = { _, _ -> null },
            ),
        )
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        // Bounds pass succeeds with dimensions, but the sample decode throws
        // (e.g. corrupt data or OutOfMemoryError): must degrade to null, never throw.
        assertNull(
            WidgetCreativeDecoder.decodeWidgetBitmap(
                "/cache/garbage.bin",
                boundsDecode = { _, opts ->
                    opts.outWidth = 4000
                    opts.outHeight = 6000
                    null
                },
                sampleDecode = { _, _ -> throw OutOfMemoryError("simulated decode OOM") },
            ),
        )
    }

    @Test
    fun `decoder reporting zero dimensions returns null`() {
        // Bounds decode "succeeds" but yields no usable dimensions.
        assertNull(
            WidgetCreativeDecoder.decodeWidgetBitmap(
                "/cache/zero-dims.jpg",
                boundsDecode = { _, opts ->
                    opts.outWidth = 0
                    opts.outHeight = 0
                    null
                },
                sampleDecode = { _, _ -> throw AssertionError("must not be reached") },
            ),
        )
    }

    @Test
    fun `sample decode result passes through with computed inSampleSize`() {
        // 4000x6000 bounds → inSampleSize 8; the sample decoder must see it.
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        var seenSampleSize = -1
        val decoded =
            WidgetCreativeDecoder.decodeWidgetBitmap(
                "/cache/big.jpg",
                boundsDecode = { _, opts ->
                    opts.outWidth = 4000
                    opts.outHeight = 6000
                    null
                },
                sampleDecode = { _, opts ->
                    seenSampleSize = opts.inSampleSize
                    bitmap
                },
            )
        assertEquals(bitmap, decoded)
        assertEquals(8, seenSampleSize)
    }

    @Test
    fun `decode succeeds for a real image and stays within widget bounds`() {
        // End-to-end against the real (Robolectric-shadowed) BitmapFactory:
        // compress a 2048x2048 bitmap to a temp file and decode through the
        // widget decoder — it must come back subsampled, not full-size.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("creative", ".jpg", context.cacheDir)
        try {
            val src = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
            file.outputStream().use { src.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            src.recycle()

            val decoded = WidgetCreativeDecoder.decodeWidgetBitmap(file.absolutePath)
            assertNotNull("Real image must decode", decoded)
            checkNotNull(decoded)
            // 2048/2=1024 >= 640, 2048/4=512 < 640 → inSampleSize 2.
            assertEquals(1024, decoded.width)
            assertEquals(1024, decoded.height)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `computeInSampleSize scales down very large images`() {
        // 4000x6000 target 640 → 8 (6000/8=750 >= 640, 6000/16=375 < 640)
        assertEquals(8, WidgetCreativeDecoder.computeInSampleSize(4000, 6000, 640))
    }

    @Test
    fun `computeInSampleSize stays 1 for already-small images`() {
        assertEquals(1, WidgetCreativeDecoder.computeInSampleSize(320, 320, 640))
    }

    @Test
    fun `computeInSampleSize power-of-two scaling`() {
        assertEquals(4, WidgetCreativeDecoder.computeInSampleSize(2560, 2560, 640))
    }
}
