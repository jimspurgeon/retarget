/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.channels.notification

import android.content.Context
import android.app.PendingIntent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.retarget.creative.Channel
import com.retarget.creative.Creative
import com.retarget.creative.CreativeImageCache
import com.retarget.creative.CreativeRotator
import com.retarget.creative.NudgeCopyCatalog
import com.retarget.creative.RoomExposureLedger
import com.retarget.goal.GoalDatabase
import com.retarget.goal.PresetCatalog
import com.retarget.scheduler.BudgetPolicy
import com.retarget.scheduler.NudgeScheduler
import com.retarget.scheduler.SnoozeSuppression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One notification delivery cycle (Phase 2, Milestone 2.4).
 *
 * This class mirrors the WallpaperRotationWorker pattern:
 * - Pulls active goals with notification enabled
 * - Uses NudgeScheduler to compute the highest-priority slot
 * - Delivers via NotificationChannel
 * - Records exposure
 * - Schedules next slot
 *
 * Construction by WorkManager; dependencies sourced from GoalDatabase and assets.
 */
class NotificationDeliveryWorker(
    appCtx: Context,
    params: WorkerParameters,
) : CoroutineWorker(appCtx, params) {
    private val db = GoalDatabase.get(applicationContext)
    private val ledger = RoomExposureLedger(db.exposureDao())
    private val rotator = CreativeRotator()
    private val notificationChannel = NotificationChannel(applicationContext)
    private val tickerChannel = TickerChannel(applicationContext)
    private val packSource = BundledPackSource(applicationContext)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting notification delivery worker")

            // Check quiet hours first
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (!com.retarget.scheduler.WallpaperRotationPolicy.shouldRotateNow(hour)) {
                Log.i(TAG, "Quiet hours active (hour=$hour); skipping notification")
                return@withContext Result.success()
            }

            // Get active goals with any delivery channel enabled, minus any the user has
            // snoozed (suppression is user-controllable, AGENTS.md §2). NOTE: ticker-enabled
            // goals are included here so NudgeScheduler can rank ticker slots against
            // notification slots; each branch below filters to its own channel.
            val now = System.currentTimeMillis()
            val activeGoals = db.goalDao().observeActive().first()
                .filter { it.settings.notificationEnabled || it.settings.tickerEnabled }
                .filterNot { SnoozeSuppression.isSnoozed(applicationContext, it.id, now) }

            if (activeGoals.isEmpty()) {
                Log.i(TAG, "No eligible goals (none active+enabled, or all snoozed); nothing to do")
                return@withContext Result.success()
            }

            // Compute slots using NudgeScheduler
            val slots = NudgeScheduler.computeSlots(activeGoals, now, ledger)

            if (slots.isEmpty()) {
                Log.i(TAG, "No slots computed for active goals")
                return@withContext Result.success()
            }

            // Pick highest-priority slot
            val slot = slots.first()
            val goal = activeGoals.find { it.id == slot.goalId }

            if (goal == null) {
                Log.w(TAG, "Goal ${slot.goalId} not found; retrying later")
                return@withContext Result.retry()
            }

            // Dispatch by channel: the lock-screen ticker is a text-only glance
            // surface with its own delivery path (M3.3, PHASE3-AGENCY.md §4).
            if (slot.channel == Channel.LOCK_SCREEN_TICKER) {
                return@withContext deliverTicker(goal, now)
            }

            // Intra-day spacing: the ledger is channel-wide today (see the
            // goal-scoped-ledger TODO in NudgeScheduler), so this gaps the most
            // recent NOTIFICATION exposure of ANY goal — conservative and
            // exactly what stops back-to-back spam when several goals compete.
            // Skip (success, NOT retry — retrying would hammer the same window).
            val lastNotificationAtMs = ledger
                .recentExposures(limit = 100)
                .firstOrNull { it.channel == Channel.NOTIFICATION }
                ?.atMs
            if (!BudgetPolicy.isWithinNotificationGap(lastNotificationAtMs, now)) {
                Log.i(
                    TAG,
                    "Last notification " +
                        (lastNotificationAtMs?.let { "${(now - it) / 60000} min ago" } ?: "n/a") +
                        "; within " +
                        "${BudgetPolicy.MIN_GAP_BETWEEN_NOTIFICATIONS_MS / 60000} min spacing; skipping",
                )
                return@withContext Result.success()
            }

            // Get creative candidates for this goal
            val candidates = packSource.creativesFor(listOf(goal))
            if (candidates.isEmpty()) {
                Log.w(TAG, "No creative candidates for goal ${goal.id}; retrying later")
                return@withContext Result.retry()
            }

            // Score and select creative (may differ from slot's creative due to fatigue)
            val selected = rotator.selectNext(candidates, ledger, now)
                ?: run {
                    Log.i(TAG, "Rotator returned no selection")
                    return@withContext Result.success()
                }

            // Prepare notification action intents — explicit component + action matched to
            // manifest receivers (com.retarget.action.*) and their "goal_id" extra keys.
            // Explicit intents sidestep the O+ implicit-broadcast restriction.
            val actions = NotificationActions(
                checkInIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    selected.id.hashCode(),
                    android.content.Intent(applicationContext, com.retarget.broadcast.CheckInReceiver::class.java).apply {
                        action = com.retarget.broadcast.CheckInReceiver.ACTION_CHECK_IN
                        putExtra(com.retarget.broadcast.CheckInReceiver.EXTRA_GOAL_ID, goal.id)
                        putExtra("CREATIVE_ID", selected.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
                snooze2hIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    selected.id.hashCode() + 1,
                    android.content.Intent(applicationContext, com.retarget.broadcast.SnoozeReceiver::class.java).apply {
                        action = com.retarget.broadcast.SnoozeReceiver.ACTION_SNOOZE
                        putExtra(com.retarget.broadcast.SnoozeReceiver.EXTRA_GOAL_ID, goal.id)
                        putExtra("CREATIVE_ID", selected.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
                fewerLikeThisIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    selected.id.hashCode() + 2,
                    android.content.Intent(applicationContext, com.retarget.broadcast.FewerNotificationsReceiver::class.java).apply {
                        action = com.retarget.broadcast.FewerNotificationsReceiver.ACTION_FEWER_NOTIFICATIONS
                        putExtra(com.retarget.broadcast.FewerNotificationsReceiver.EXTRA_GOAL_ID, goal.id)
                        putExtra("CREATIVE_ID", selected.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

            // Build and deliver notification. Copy is chosen from the theme
            // catalog with no-immediate-repeat: the last line shown for this
            // theme is excluded from the draw so consecutive nudges never
            // repeat. Falls back to the goal's display name if the creative
            // carries no copy (defensive: an empty copyPool previously crashed
            // this worker with NoSuchElementException).
            val fallbackCopy = "A gentle nudge toward your goal."
            val theme = selected.goalTheme
            val copyPrefs = applicationContext.getSharedPreferences(COPY_STATE_PREFS, Context.MODE_PRIVATE)
            val lastLine = copyPrefs.getString(lastLineKey(theme), null)
            val selectedLine = NudgeCopyCatalog.selectLine(theme, lastLine)
            val spec = NotificationSpec(
                goalId = goal.id,
                creative = selected,
                title = PresetCatalog.byId(goal.presetId)?.displayName ?: goal.displayName,
                copyLine = selectedLine.ifEmpty { fallbackCopy },
                actions = actions,
            )

            val notificationId = (goal.id * 1000 + (now / 1000)).toInt()
            val delivered = notificationChannel.deliver(spec, notificationId)

            if (!delivered) {
                Log.w(TAG, "Notification delivery failed for goal ${goal.id}; retrying later")
                return@withContext Result.retry()
            }

            // Record exposure, then persist the delivered copy line so the next
            // nudge for this theme avoids repeating it.
            ledger.recordExposure(selected.id, selected.subTheme, Channel.NOTIFICATION, now)
            copyPrefs.edit().putString(lastLineKey(theme), selectedLine).apply()
            Log.i(TAG, "Notification delivered: goal=${goal.id}, creative=${selected.id}")

            // Schedule next slot: recompute with the freshly-updated ledger and take
            // the next notification slot for this goal (if any remains within budget)
            val nextSlot = NudgeScheduler.computeSlots(
                listOf(goal),
                now,
                ledger,
            ).firstOrNull { it.goalId == goal.id && it.channel == Channel.NOTIFICATION }

            if (nextSlot != null) {
                val delay = (nextSlot.scheduledTimeMs - now).coerceAtLeast(0)
                Log.d(TAG, "Next slot scheduled in ${delay / 1000 / 60} minutes")
            } else {
                Log.d(TAG, "No more slots today for goal ${goal.id}")
            }

            Result.success()
        }

    /**
     * Delivers the lock-screen ticker slot (M3.3, PHASE3-AGENCY.md §4).
     *
     * The ticker is a text-only ongoing notification with a stable per-goal id,
     * so each delivery replaces the previous one instead of stacking. Exposure
     * is recorded to the ledger with Channel.LOCK_SCREEN_TICKER so the daily
     * cap and quiet-hour gates see it.
     */
    private suspend fun deliverTicker(
        goal: com.retarget.goal.GoalEntity,
        now: Long,
    ): Result {
        val startOfDayMs = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Gentle pacing line: total nudges across channels today. Neutral,
        // factual framing only — no fear/shame language (decision #5).
        val nudgesToday = Channel.entries.sumOf {
            ledger.exposuresTodayByChannel(it, startOfDayMs)
        }
        val title = PresetCatalog.byId(goal.presetId)?.displayName ?: goal.displayName
        val pacingLine = "$title · ${nudgesToday + 1} nudges today"

        // Tap-through check-in mirrors the notification channel's primary action;
        // the ticker deliberately has no other actions (glance surface, not prompt).
        val tapIntent = PendingIntent.getBroadcast(
            applicationContext,
            goal.id.hashCode(),
            android.content.Intent(applicationContext, com.retarget.broadcast.CheckInReceiver::class.java).apply {
                action = com.retarget.broadcast.CheckInReceiver.ACTION_CHECK_IN
                putExtra(com.retarget.broadcast.CheckInReceiver.EXTRA_GOAL_ID, goal.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val spec = TickerSpec(
            goalId = goal.id,
            title = title,
            pacingLine = pacingLine,
            tapIntent = tapIntent,
        )

        // Stable id per goal: one ticker replaces the previous.
        val notificationId = (TICKER_NOTIFICATION_ID_BASE + goal.id).toInt()
        val delivered = tickerChannel.deliver(spec, notificationId)

        return if (delivered) {
            ledger.recordExposure("${goal.id}:ticker", "ticker", Channel.LOCK_SCREEN_TICKER, now)
            Log.i(TAG, "Ticker delivered: goal=${goal.id}")
            Result.success()
        } else {
            Log.w(TAG, "Ticker delivery failed for goal ${goal.id}; retrying later")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotificationDeliveryWorker"
        private const val COPY_STATE_PREFS = "nudge_copy_state"

        /** Stable notification-id base for the per-goal lock-screen ticker. */
        internal const val TICKER_NOTIFICATION_ID_BASE = 1000L

        /** SharedPreferences key holding the last delivered line for [theme]. */
        internal fun lastLineKey(theme: com.retarget.creative.GoalTheme): String =
            "last_line_${theme.name}"
    }
}

/**
 * Bundled creative pack source for notification channel.
 * Mirrors BundledPackSource from WallpaperRotationWorker with minor adaptations.
 */
class BundledPackSource(
    private val context: Context,
) {
    /** Packs bundled in assets, mapped to the goal themes they serve. */
    private val packThemes: Map<String, com.retarget.creative.GoalTheme> =
        mapOf(
            "fresh_air" to com.retarget.creative.GoalTheme.NATURE_TIME,
            "fruit" to com.retarget.creative.GoalTheme.PLANT_BASED_WHOLE_FOODS,
            "vegetables" to com.retarget.creative.GoalTheme.PLANT_BASED_WHOLE_FOODS,
        )

    fun creativesFor(goals: List<com.retarget.goal.GoalEntity>): List<Creative> {
        val wantedThemes = goals.mapNotNull { PresetCatalog.byId(it.presetId)?.goalTheme }.toSet()
        if (wantedThemes.isEmpty()) return emptyList()
        return packThemes
            .filterValues { it in wantedThemes }
            .keys
            .flatMap { packId -> loadPack(packId) }
    }

    fun imageFileFor(creative: Creative): File? {
        return CreativeImageCache.cachedFileFor(creative, context)
    }

    private fun loadPack(packId: String): List<Creative> {
        val manifestJson = try {
            context.assets.open("$PACKS_DIR/$packId/manifest.json")
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "No manifest for pack '$packId'", e)
            return emptyList()
        }

        val manifest = json.decodeFromString(PackManifest.serializer(), manifestJson)
        return manifest.images.map { entry ->
            Creative(
                id = "${packId}/${entry.unsplashId}",
                packId = packId,
                goalTheme = packThemes[packId] ?: com.retarget.creative.GoalTheme.GENERAL_WELLNESS,
                subTheme = entry.subTheme ?: packId,
                copyPool = NudgeCopyCatalog.forTheme(
                    packThemes[packId] ?: com.retarget.creative.GoalTheme.GENERAL_WELLNESS,
                ),
                imagePath = "$PACKS_DIR/$packId/${entry.file}",
                attribution = entry.photographer,
                licenseUrl = entry.licenseUrl,
            )
        }
    }

    @kotlinx.serialization.Serializable
    private data class PackManifest(
        val packId: String,
        val images: List<PackImage>,
    )

    @kotlinx.serialization.Serializable
    private data class PackImage(
        val unsplashId: String,
        val file: String,
        val photographer: String? = null,
        val licenseUrl: String,
        val subTheme: String? = null,
    )

    companion object {
        private const val PACKS_DIR = "creative-packs"
        private const val TAG = "NotificationPackSource"
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
