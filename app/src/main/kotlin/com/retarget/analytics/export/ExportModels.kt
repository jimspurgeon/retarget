/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.analytics.export

import com.retarget.analytics.CheckInEntity
import com.retarget.creative.ExposureEntity
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalEntity
import kotlinx.serialization.Serializable

/**
 * Top-level export document — everything the user owns in a portable,
 * human-readable JSON file (see [ExportSerializer]).
 *
 * Privacy stance (AGENTS.md §1): this payload carries behavioral data
 * (exposure events, check-ins) only because the *user* asked for it, and it
 * is written to a location the *user* chooses. That is the local-first
 * promise fulfilled, not a violation of it: no network, no telemetry, no
 * copy retained by the app beyond the user's own file.
 *
 * Deliberately excluded:
 * - Room auto-generated row ids (`GoalEntity.id`, `ExposureEntity.id`,
 *   `CheckInEntity.id`) — device-local and meaningless outside this device.
 *   Check-ins reference their goal by stable [ExportedCheckIn.goalPresetId].
 * - Creative binaries, image paths, and license data — creatives are
 *   re-downloadable repo assets (PHASE3-AGENCY.md §2).
 */
@Serializable
data class ExportPayload(
    val schemaVersion: Int,
    val goals: List<ExportedGoal> = emptyList(),
    val exposureEvents: List<ExportedExposure> = emptyList(),
    val checkIns: List<ExportedCheckIn> = emptyList(),
    /** M3.4: learned scheduling/creative-preference cells, newest feature block. */
    val learningState: List<ExportedLearningCell> = emptyList(),
) {
    companion object {
        /** The only schema version emitted by this build; see [ExportSerializer]. */
        const val SCHEMA_VERSION = 2

        /**
         * Assemble a payload from raw Room rows (pure mapping, no I/O —
         * callers fetch the rows themselves so this stays testable).
         */
        fun fromRows(
            goals: List<GoalEntity>,
            exposureEvents: List<ExposureEntity>,
            checkIns: List<CheckInEntity>,
            learningCells: List<com.retarget.learning.LearningStateEntity> = emptyList(),
        ): ExportPayload =
            ExportPayload(
                schemaVersion = SCHEMA_VERSION,
                goals = goals.map { ExportedGoal.from(it) },
                exposureEvents = exposureEvents.map { ExportedExposure.from(it) },
                checkIns = checkIns.map { ExportedCheckIn.from(it, goals) },
                learningState = learningCells.map { ExportedLearningCell.from(it, goals) },
            )
    }
}

/**
 * One M3.4 learning cell: what the app has learned about (time bucket,
 * sub-theme) response for a goal. Exported so the user can inspect exactly
 * what adaptation is based on (AGENTS.md §2 transparency) — nothing more,
 * nothing less.
 */
@Serializable
data class ExportedLearningCell(
    val goalPresetId: String,
    /** 0-5: which 4-hour slice of the day this cell describes. */
    val bucket: Int,
    val subTheme: String,
    val attempts: Int,
    val scoreSum: Double,
    val scoreCount: Int,
) {
    companion object {
        fun from(
            cell: com.retarget.learning.LearningStateEntity,
            goals: List<GoalEntity>,
        ): ExportedLearningCell =
            ExportedLearningCell(
                goalPresetId = goals.firstOrNull { it.id == cell.goalId }?.presetId ?: ExportedCheckIn.UNKNOWN_GOAL,
                bucket = cell.bucket,
                subTheme = cell.subTheme,
                attempts = cell.attempts,
                scoreSum = cell.scoreSum,
                scoreCount = cell.scoreCount,
            )
    }
}

/**
 * One user goal ("brand") with its campaign settings embedded as a nested
 * object (not raw JSON strings — the export is meant to be read by humans).
 *
 * The Room row id is intentionally dropped; identity is carried by
 * [presetId], which is stable across devices.
 */
@Serializable
data class ExportedGoal(
    val presetId: String,
    val displayName: String,
    /** Epoch milliseconds. */
    val createdAtMs: Long,
    val active: Boolean,
    val settings: CampaignSettings,
) {
    companion object {
        fun from(goal: GoalEntity): ExportedGoal =
            ExportedGoal(
                presetId = goal.presetId,
                displayName = goal.displayName,
                createdAtMs = goal.createdAt,
                active = goal.active,
                settings = goal.settings,
            )
    }
}

/**
 * One exposure-ledger event. The channel is exported as its stable name
 * string (e.g. "WALLPAPER", "NOTIFICATION") rather than the Room ordinal,
 * so the file stays readable and survives channel reordering on-device.
 */
@Serializable
data class ExportedExposure(
    val creativeId: String,
    val subTheme: String,
    val channel: String,
    /** Epoch milliseconds. */
    val atMs: Long,
) {
    companion object {
        fun from(event: ExposureEntity): ExportedExposure =
            ExportedExposure(
                creativeId = event.creativeId,
                subTheme = event.subTheme,
                channel = event.channel.name,
                atMs = event.atMs,
            )
    }
}

/**
 * One check-in event. References its goal by [goalPresetId] because Room
 * row ids are device-local; if two installed goals share a preset, both are
 * candidates for restore — import (v0.4.1) will need a documented
 * disambiguation rule.
 */
@Serializable
data class ExportedCheckIn(
    val goalPresetId: String,
    /** Epoch milliseconds. */
    val atMs: Long,
    val notes: String? = null,
) {
    companion object {
        fun from(
            checkIn: CheckInEntity,
            goals: List<GoalEntity>,
        ): ExportedCheckIn =
            ExportedCheckIn(
                goalPresetId = goals.firstOrNull { it.id == checkIn.goalId }?.presetId ?: UNKNOWN_GOAL,
                atMs = checkIn.atMs,
                notes = checkIn.notes,
            )

        /** Placeholder when a check-in's goal row is missing (dangling foreign data). */
        const val UNKNOWN_GOAL = "__unknown_goal__"
    }
}
