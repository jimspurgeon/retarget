/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics.export

import com.retarget.analytics.CheckInDao
import com.retarget.analytics.CheckInEntity
import com.retarget.creative.ExposureDao
import com.retarget.creative.ExposureEntity
import com.retarget.goal.GoalDao
import com.retarget.goal.GoalEntity
import com.retarget.learning.LearningStateDao

/**
 * Room-backed [ExportUseCase]: fetches every user-authored row (goals,
 * exposure events, check-ins) and renders the export document.
 *
 * Fetches ALL goals — not just active ones — because an export is a backup:
 * the user must not lose inactive/archived goals in their data copy
 * (AGENTS.md §2: users can inspect and export their full data).
 *
 * suspend (unlike most of this app's blocking DAOs) because it is called once
 * from [com.retarget.app.ui.ExportViewModel] inside withContext(IO); suspend
 * keeps the call site free of manual executor juggling.
 */
class RoomExportUseCase(
    private val goalDao: GoalDao,
    private val exposureDao: ExposureDao,
    private val checkInDao: CheckInDao,
    private val learningDao: LearningStateDao? = null,
) : ExportUseCase {
    override suspend fun buildExport(): String {
        val goals: List<GoalEntity> = goalDao.getAllGoalsForExport()
        val exposures: List<ExposureEntity> = exposureDao.getAllExposuresForExport()
        val checkIns: List<CheckInEntity> = checkInDao.getAllCheckInsForExport()
        val learningCells = learningDao?.getAllForExport() ?: emptyList()
        return ExportSerializer.serialize(ExportPayload.fromRows(goals, exposures, checkIns, learningCells))
    }
}
