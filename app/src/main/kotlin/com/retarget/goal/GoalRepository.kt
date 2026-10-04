/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import kotlinx.coroutines.flow.Flow

/**
 * Install/uninstall goals, backed by Room. The only writer of Goal rows.
 */
class GoalRepository(
    private val dao: GoalDao,
) {
    fun observeActive(): Flow<List<GoalEntity>> = dao.observeActive()

    /**
     * Instantiate a campaign from a catalog preset. Idempotent: an existing
     * active goal for the same preset is reactivated rather than duplicated.
     */
    suspend fun installPreset(
        presetId: String,
        nowMs: Long,
    ): Long {
        val preset =
            PresetCatalog.byId(presetId)
                ?: throw IllegalArgumentException("Unknown preset '$presetId'")
        dao.byPresetId(presetId)?.let { existing ->
            if (!existing.active) dao.setActive(existing.id, true)
            return existing.id
        }
        return dao.insert(
            GoalEntity(
                presetId = preset.id,
                displayName = preset.displayName,
                createdAt = nowMs,
                settingsJson = GoalConverters().settingsToJson(CampaignSettings.fromPreset(preset)),
            ),
        )
    }

    suspend fun setActive(
        id: Long,
        active: Boolean,
    ) = dao.setActive(id, active)

    suspend fun hasAnyActiveGoal(): Boolean = dao.activeCount() > 0
}
