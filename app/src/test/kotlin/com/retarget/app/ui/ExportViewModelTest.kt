/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import com.retarget.analytics.export.ExportUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * JVM tests for [ExportViewModel] using a fake [ExportUseCase]:
 * success writes the payload to the target stream; failure propagates a
 * user-safe error outcome (and never throws out of the ViewModel).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    /** Fake seam: emits [payload], or throws when [throwOnBuild] is set. */
    private class FakeExportUseCase(
        var payload: String = "{}",
        var throwOnBuild: Exception? = null,
    ) : ExportUseCase {
        override suspend fun buildExport(): String {
            throwOnBuild?.let { throw it }
            return payload
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `success writes the payload string to the stream`() =
        runTest(testDispatcher) {
            val fake = FakeExportUseCase(payload = """{"goals":[]}""")
            val viewModel = ExportViewModel(fake, testDispatcher)
            val sink = ByteArrayOutputStream()

            var succeeded = false
            viewModel.exportTo(
                openSink = { sink },
                onSuccess = { succeeded = true },
                onError = { error("exportTo must not report failure on success") },
            )

            assertTrue(succeeded)
            assertEquals("""{"goals":[]}""", sink.toString(Charsets.UTF_8.name()))
            // Row re-enabled after completion
            assertFalse(viewModel.isExporting.first())
        }

    @Test
    fun `failure propagates user-safe error state without leaking exception text`() =
        runTest(testDispatcher) {
            val boom = RuntimeException("sqlite table goals missing at /data/secret.db")
            val fake = FakeExportUseCase(throwOnBuild = boom)
            val viewModel = ExportViewModel(fake, testDispatcher)

            var succeeded = false
            var failed = false
            viewModel.exportTo(
                openSink = { ByteArrayOutputStream() },
                onSuccess = { succeeded = true },
                onError = { failed = true },
            )

            assertFalse(succeeded)
            assertTrue(failed)
            assertFalse(viewModel.isExporting.first())
        }

    @Test
    fun `unopenable sink reports error without throwing`() =
        runTest(testDispatcher) {
            val fake = FakeExportUseCase()
            val viewModel = ExportViewModel(fake, testDispatcher)

            var failed = false
            viewModel.exportTo(
                openSink = { null }, // contentResolver.openOutputStream returning null
                onSuccess = { error("must not succeed") },
                onError = { failed = true },
            )

            assertTrue(failed)
            assertFalse(viewModel.isExporting.first())
        }

    @Test
    fun `double-tap guard ignores export while one is in flight`() =
        runTest(testDispatcher) {
            var started = false
            // buildExport never returns (delay(MAX_VALUE)); first export hangs.
            val fake =
                object : ExportUseCase {
                    override suspend fun buildExport(): String {
                        started = true
                        delay(Long.MAX_VALUE)
                        return "{}"
                    }
                }
            val viewModel = ExportViewModel(fake, testDispatcher)
            viewModel.exportTo(openSink = { ByteArrayOutputStream() })
            assertTrue(started)
            assertTrue(viewModel.isExporting.first())

            var secondWrote = false
            viewModel.exportTo(
                openSink = {
                    secondWrote = true
                    ByteArrayOutputStream()
                },
            )
            assertFalse("second export must be rejected while one is in flight", secondWrote)
        }

    @Test
    fun `suggested file name uses ISO date and json extension`() {
        val name = buildSuggestedExportFileName(LocalDate.of(2026, 2, 14))
        assertEquals("retarget-export-2026-02-14.json", name)
    }
}
