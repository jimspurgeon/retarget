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
import com.retarget.goal.GoalDao
import com.retarget.goal.GoalEntity
import com.retarget.goal.PresetCatalog
import com.retarget.learning.LearningStateDao
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
 * Displayed-goal selection (gatekeeper M2 fix, PHASE3-AGENCY.md §3 "per-goal
 * widget preference"):
 * 1. the goal the user picked for the widget ([WidgetGoalPreferenceStore]);
 * 2. when unset — or the preferred goal is inactive/missing — the FIRST-ADDED
 *    active goal (`minByOrNull { createdAt }`), per the plan's letter.
 */
@Singleton
class DefaultWidgetStateSource
    @Inject
    constructor(
        private val context: android.content.Context,
        private val goalDao: GoalDao,
        private val checkInDao: CheckInDao,
        private val exposureDao: ExposureDao,
        private val learningDao: LearningStateDao,
        private val creativeRepository: com.retarget.creative.PersistentCreativeRepository,
        private val zoneId: ZoneId,
        private val widgetGoalPreference: WidgetGoalPreferenceStore,
    ) : WidgetStateSource {
        private val checkInWithReward by lazy {
            // Assembled from DAOs rather than injected: RewardRecorder is new in
            // M3.4 and the source owns the DB domain it reads (widget check-ins
            // were already a self-contained write path). Lazy to avoid paying
            // setup cost when only current()/channelEnablement() are used.
            val db = com.retarget.goal.GoalDatabase.get(context)
            com.retarget.learning.CheckInWithReward(
                db,
                com.retarget.learning.RewardRecorder(db.learningStateDao(), db.exposureDao()),
            )
        }
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
                // Parity with CheckInService: a storage failure (disk full, DB
                // corruption) degrades to "no check-in" + a log line instead of
                // propagating out of the Glance ActionCallback (gatekeeper M4a).
                try {
                    checkInWithReward.checkIn(goalId = goal.id)
                    goal.id
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Widget check-in insert failed for goalId=${goal.id}", e)
                    null
                }
            }

        /**
         * The displayed goal (gatekeeper M2 fix): the user's widget preference
         * when it resolves to an active goal, else the FIRST-ADDED active goal.
         */
        private suspend fun primaryGoal(): GoalEntity? {
            val activeGoals = goalDao.getAllActiveGoals()
            return WidgetGoalResolver.resolve(activeGoals, widgetGoalPreference.get())
        }

        private suspend fun composeFor(goal: GoalEntity): WidgetSnapshot {
            // Parse failures (goal.settings) are expected user-data degradation
            // and map to Error(SettingsUnparseable); anything else (e.g. a Room/
            // storage failure while counting) is a distinct, loudly-logged
            // ErrorReason.StorageFailure (gatekeeper M2m: never misattribute
            // storage problems to unparseable settings).
            try {
                goal.settings
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Campaign settings unparseable for goalId=${goal.id}", e)
                return WidgetSnapshot.Error(reason = ErrorReason.SettingsUnparseable)
            }
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
                android.util.Log.e(TAG, "Storage failure composing widget state for goalId=${goal.id}", e)
                return WidgetSnapshot.Error(reason = ErrorReason.StorageFailure)
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

        private companion object {
            private const val TAG = "DefaultWidgetStateSource"
        }
    }
