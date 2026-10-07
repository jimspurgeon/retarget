/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import com.retarget.analytics.CheckInDao
import com.retarget.creative.Channel
import com.retarget.creative.CreativeImageCache
import com.retarget.creative.ExposureDao
import com.retarget.creative.PersistentCreativeRepository
import com.retarget.goal.GoalDao
import com.retarget.goal.GoalEntity
import com.retarget.goal.PresetCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of the current [WidgetSnapshot] for widget rendering (M3.2).
 *
 * Seam between the pure composer ([WidgetStateComposer], owned by Worker A)
 * and the render layer ([RetargetWidget]): `provideGlance` depends on this
 * interface, not on DAOs, so Glance rendering stays dumb.
 */
interface WidgetStateSource {
    /** Current snapshot for widget rendering; safe to call from any dispatcher. */
    suspend fun current(): WidgetSnapshot

    /**
     * Channel enablement for the displayed goal — drives pacing aggregation
     * (only ENABLED channels count toward "N of M nudges").
     */
    suspend fun channelEnablement(): ChannelEnablement

    /**
     * Records a check-in for the displayed goal and returns the goal ID used,
     * or null when no goal is active (nothing to check in to).
     */
    suspend fun checkIn(): Long?
}

/** Enablement of the delivery channels for the goal shown on the widget. */
data class ChannelEnablement(
    val wallpaperEnabled: Boolean,
    val notificationEnabled: Boolean,
)

/**
 * Default [WidgetStateSource]: reads the primary goal's counts and composes a snapshot.
 *
 * Widget add flow sets a per-goal widget preference (first added goal wins for
 * MVP — PHASE3-AGENCY.md §3): the most recently created active goal is displayed.
 */
@Singleton
class DefaultWidgetStateSource
    @Inject
    constructor(
        private val context: android.content.Context,
        private val goalDao: GoalDao,
        private val exposureDao: ExposureDao,
        private val checkInDao: CheckInDao,
        private val creativeRepository: PersistentCreativeRepository,
        private val zoneId: ZoneId,
    ) : WidgetStateSource {
        override suspend fun current(): WidgetSnapshot =
            withContext(Dispatchers.IO) {
                val goal = primaryGoal() ?: return@withContext WidgetSnapshot.Empty
                composeFor(goal)
            }

        override suspend fun channelEnablement(): ChannelEnablement =
            withContext(Dispatchers.IO) {
                val goal = primaryGoal() ?: return@withContext ChannelEnablement(false, false)
                val settings =
                    try {
                        goal.settings
                    } catch (e: Exception) {
                        return@withContext ChannelEnablement(false, false)
                    }
                ChannelEnablement(settings.wallpaperEnabled, settings.notificationEnabled)
            }

        override suspend fun checkIn(): Long? =
            withContext(Dispatchers.IO) {
                val goal = primaryGoal() ?: return@withContext null
                checkInDao.insert(
                    com.retarget.analytics.CheckInEntity(goalId = goal.id, atMs = System.currentTimeMillis(), notes = null),
                )
                goal.id
            }

        /**
         * The displayed goal: most recently created ACTIVE goal (M3.2 MVP:
         * "first added goal wins" approximated by latest-created; widget add-flow
         * per-goal preference may refine this later without changing the seam).
         */
        private suspend fun primaryGoal(): GoalEntity? = goalDao.getAllActiveGoals().maxByOrNull { it.createdAt }

        private suspend fun composeFor(goal: GoalEntity): WidgetSnapshot {
            try {
                // Start of today in the injected zone — mirrors the schedulers'
                // ZoneId-injection pattern (NudgeScheduler uses the same calculation).
                val startOfDayMs =
                    Instant
                        .now()
                        .atZone(zoneId)
                        .toLocalDate()
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli()

                val exposuresToday =
                    mapOf(
                        Channel.WALLPAPER to exposureDao.exposuresTodayByChannel(Channel.WALLPAPER, startOfDayMs),
                        Channel.NOTIFICATION to exposureDao.exposuresTodayByChannel(Channel.NOTIFICATION, startOfDayMs),
                    )
                val checkInsToday = checkInDao.countByGoal(goal.id, startOfDayMs)
                val latestCreativePath = latestCreativePathFor(goal)
                return WidgetStateComposer.compose(goal, exposuresToday, checkInsToday, latestCreativePath)
            } catch (e: Exception) {
                android.util.Log.w("DefaultWidgetStateSource", "Failed to compose widget state", e)
                return WidgetSnapshot.Error(reason = ErrorReason.SettingsUnparseable)
            }
        }

        /**
         * Resolves a creative path for the widget background using the SAME
         * resolution pattern as the other channels ([CreativeImageCache]): the
         * highest-base-appeal candidate for the goal's theme, cached to disk.
         * Stable choice, no fatigue engine involvement — the widget is a
         * mirror, not a delivery channel.
         */
        private suspend fun latestCreativePathFor(goal: GoalEntity): String? {
            try {
                val theme =
                    PresetCatalog.byId(goal.presetId)?.goalTheme
                        ?: return null
                val candidates = creativeRepository.getCandidatesForActiveGoals(listOf(theme))
                val creative = candidates.maxByOrNull { it.baseAppeal } ?: return null
                val file = CreativeImageCache.cachedFileFor(creative, context)
                return file?.absolutePath
            } catch (e: Exception) {
                return null
            }
        }
    }
