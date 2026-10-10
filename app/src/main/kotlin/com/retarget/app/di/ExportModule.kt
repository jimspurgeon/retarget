/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.di

import com.retarget.analytics.CheckInDao
import com.retarget.analytics.export.ExportUseCase
import com.retarget.analytics.export.RoomExportUseCase
import com.retarget.creative.ExposureDao
import com.retarget.goal.GoalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Export wiring for the M3.1 "Export my data" Settings action.
 *
 * Integrates the export-core serializer with the export-ui action:
 * [RoomExportUseCase] fetches all goals (active AND archived), exposure
 * events, and check-ins from Room, maps them via
 * [com.retarget.analytics.export.ExportPayload.fromRows], and renders the
 * pretty-printed JSON the user picks a destination for.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExportModule {
    @Provides
    @Singleton
    fun provideExportUseCase(
        goalDao: GoalDao,
        exposureDao: ExposureDao,
        checkInDao: CheckInDao,
        learningDao: com.retarget.learning.LearningStateDao,
    ): ExportUseCase =
        RoomExportUseCase(goalDao, exposureDao, checkInDao, learningDao)
}
