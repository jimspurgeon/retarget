/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retarget.analytics.export.ExportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import javax.inject.Inject

/**
 * Prefix for suggested export file names (machine-facing constant, not a
 * user-visible string — those live in strings.xml).
 */
internal const val EXPORT_FILENAME_PREFIX = "retarget-export-"

/**
 * Builds the suggested file name handed to the system document picker,
 * e.g. `retarget-export-2026-02-14.json`. Uses java.time (minSdk 26).
 */
internal fun buildSuggestedExportFileName(today: LocalDate = LocalDate.now()): String = "$EXPORT_FILENAME_PREFIX$today.json"

/**
 * ViewModel backing the Settings "Export my data" action.
 *
 * Owns the write-side of export: once the system picker (ACTION_CREATE_DOCUMENT)
 * returns a URI, the caller passes a sink factory and this view model builds
 * the payload via [ExportUseCase] (Room reads stay off the main thread inside
 * the use case / on [ioDispatcher]) and streams it out.
 *
 * Failure handling is user-safe by construction: neither callback carries
 * exception text, so nothing can leak internals into a toast.
 */
@HiltViewModel
class ExportViewModel
    @Inject
    constructor(
        private val exportUseCase: ExportUseCase,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _isExporting = MutableStateFlow(false)

        /** True while an export is being built/written; the UI disables the row to prevent double-taps. */
        val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

        /**
         * Builds the export payload via [ExportUseCase] and writes it to the
         * stream returned by [openSink].
         *
         * [openSink] is a factory (rather than a Uri) so this view model stays
         * JVM-unit-testable; the composable supplies
         * `context.contentResolver::openOutputStream`.
         *
         * @param onSuccess invoked on the main thread after the file is fully written
         * @param onError invoked on the main thread for any failure (serializer
         *   threw, sink could not be opened, write failed). Generic by design.
         */
        fun exportTo(
            openSink: () -> OutputStream?,
            onSuccess: () -> Unit = {},
            onError: () -> Unit = {},
        ) {
            if (_isExporting.value) return // double-tap guard
            _isExporting.value = true
            viewModelScope.launch {
                val outcome =
                    runCatching {
                        withContext(ioDispatcher) {
                            val json = exportUseCase.buildExport()
                            val sink =
                                openSink()
                                    ?: throw IOException("Export destination could not be opened")
                            sink.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        }
                    }
                _isExporting.value = false
                if (outcome.isSuccess) {
                    onSuccess()
                } else {
                    // Exception details are deliberately dropped here: user-visible
                    // messaging stays generic (AGENTS.md §2 transparency; never leak internals).
                    onError()
                }
            }
        }
    }
