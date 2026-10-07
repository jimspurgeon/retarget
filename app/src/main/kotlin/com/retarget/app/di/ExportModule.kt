/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.di

import com.retarget.analytics.export.ExportUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Export wiring for the M3.1 "Export my data" Settings action (Worker B,
 * branch jimspurgeon/m31-export-ui).
 *
 * INTEGRATION NOTE (coordinator): the export-core branch provides the real
 * serializer, which satisfies [ExportUseCase] directly. Until that merge,
 * [provideExportUseCase] returns a placeholder so this branch compiles with a
 * complete Hilt graph on its own. On integration, delete this provider and
 * the placeholder object and bind the concrete serializer instead, e.g.:
 *
 * ```
 * @Binds
 * @Singleton
 * abstract fun bindExportUseCase(impl: ExportSerializer): ExportUseCase
 * ```
 *
 * If triggered pre-integration the placeholder fails safely: the ViewModel
 * catches the throw and shows the generic "export failed" toast.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExportModule {
    /**
     * Placeholder binding keeping the DI graph complete without the concrete
     * serializer from the export-core branch. Replace at integration (see
     * class-level note).
     */
    @Provides
    @Singleton
    fun provideExportUseCase(): ExportUseCase = PlaceholderExportUseCase
}

private object PlaceholderExportUseCase : ExportUseCase {
    override suspend fun buildExport(): String =
        throw IllegalStateException(
            "Export serializer not wired yet: integrate the m31-export-core branch",
        )
}
