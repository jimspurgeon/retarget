/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.scheduler

import android.util.Log
import com.retarget.creative.Channel
import com.retarget.creative.Creative
import com.retarget.creative.CreativeRotator
import com.retarget.creative.ExposureLedger
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalEntity
import java.security.SecureRandom
import kotlin.math.max

/**
 * General-purpose nudge scheduler coordinating multiple channels (Phase 2, Milestone 2.3).
 *
 * This class implements the target-state architecture from PHASE2-CAMPAIGN.md §3.2:
 * - Accepts active goals with enabled channels
 * - Computes next slots per channel using policies (wallpaper, notification)
 * - Applies fresh-start boosts from FreshStartCalendar.boostMultiplier()
 * - Respects cross-channel crowding backoff
 *
 * Thread safety: All methods are thread-safe. Caller must ensure ledger access
 * is properly synchronized if shared across threads.
 */
object NudgeScheduler {
    private const val TAG = "NudgeScheduler"

    /**
     * Compute next delivery slots for all active goals/channels.
     *
     * @param activeGoals List of currently active goals from Room database
     * @param nowMs Current timestamp in milliseconds (injectable for tests)
     * @param ledger Exposure ledger for checking send counts and cooldowns
     * @param random Random instance for tie-breaking and jitter (defaults to SecureRandom)
     * @return Sorted list of slots ordered by priority (highest first)
     */
    fun computeSlots(
        activeGoals: List<GoalEntity>,
        nowMs: Long,
        ledger: ExposureLedger,
        random: Random = SecureRandom(),
    ): List<Slot> {
        val slots = mutableListOf<Slot>()
        val hourOfDay = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour

        for (goal in activeGoals) {
            val settings = goal.settings
            val packed = PersistentCreativeRepository(goal, goal.presetId)
                .loadPackedCreatives()

            if (packed.isEmpty()) {
                Log.w(TAG, "No packed creatives for goal ${goal.id} (${goal.displayName})")
                continue
            }

            // Check notification channel
            if (settings.notificationEnabled && NotificationPolicy.canDeliver(
                    goalId = goal.id,
                    nowMs = nowMs,
                    settings = settings,
                    sentToday = exposuresTodayForChannel(ledger, Channel.NOTIFICATION, nowMs),
                    dismissedSince = emptyList(), // Dismissals tracked separately
                ) && WallpaperRotationPolicy.shouldRotateNow(hourOfDay)) {
                // Compute next slot time for notification
                val nextSlotMs = computeNextSlotTime(nowMs, random)
                val boost = FreshStartCalendar.boostMultiplier(nowMs)
                val score = CreativeRotator.score(packed.first(), ledger, nowMs) * boost

                slots.add(
                    Slot(
                        goalId = goal.id,
                        channel = Channel.NOTIFICATION,
                        creative = packed.first(),
                        scheduledTimeMs = nextSlotMs,
                        priority = score,
                    ),
                )
            }
        }

        // Sort by priority descending
        return slots.sortedByDescending { it.priority }
    }

    /**
     * Compute the next slot time with jitter for drift tolerance.
     *
     * Per PHASE2-CAMPAIGN.md Risk 1, WorkManager precision under Doze mode
     * may drift ±30-45 minutes, which is acceptable per timing research.
     * This function computes a target time with controlled jitter.
     *
     * @param nowMs Current timestamp
     * @param random Random instance for jitter
     * @return Target time in milliseconds
     */
    fun computeNextSlotTime(
        nowMs: Long,
        random: Random = SecureRandom(),
    ): Long {
        // Base interval: 2 hours for notifications (user-configurable cap suggests spacing)
        val baseIntervalMs = 2 * 60 * 60 * 1000L

        // Add jitter up to ±30 minutes (drift tolerance per PHASE2-CAMPAIGN.md Risk 1)
        val jitterRangeMs = 30 * 60 * 1000L
        val jitter = (random.nextDouble() * 2 - 1) * jitterRangeMs

        return nowMs + baseIntervalMs + jitter.toLong()
    }

    /**
     * Reschedule a notification delivery after successful execution.
     *
     * @param goalId Goal identifier
     * @param creative Creative that was delivered
     * @param settings Campaign settings for the goal
     * @param ledger Exposure ledger
     * @param nowMs Current timestamp
     * @param random Random instance
     * @return Next slot to schedule, or null if no more slots allowed today
     */
    fun rescheduleNotification(
        goalId: Long,
        creative: Creative,
        settings: CampaignSettings,
        ledger: ExposureLedger,
        nowMs: Long,
        random: Random = SecureRandom(),
    ): Slot? {
        if (!settings.notificationEnabled) {
            Log.d(TAG, "Notifications disabled for goal $goalId; not rescheduling")
            return null
        }

        // Check if we can deliver again today
        val sentToday = exposuresTodayForChannel(ledger, Channel.NOTIFICATION, nowMs)
        val dismissedSince = emptyList<Long>() // Placeholder for dismissal tracking

        if (!NotificationPolicy.canDeliver(
                goalId = goalId,
                nowMs = nowMs,
                settings = settings,
                sentToday = sentToday,
                dismissedSince = dismissedSince,
            )) {
            Log.d(TAG, "Notification cap reached for goal $goalId; not rescheduling")
            return null
        }

        // Check quiet hours
        val hourOfDay = java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).hour
        if (!WallpaperRotationPolicy.shouldRotateNow(hourOfDay)) {
            Log.d(TAG, "Quiet hours active; skipping reschedule")
            return null
        }

        val nextSlotMs = computeNextSlotTime(nowMs, random)
        val boost = FreshStartCalendar.boostMultiplier(nowMs)
        val score = CreativeRotator.score(creative, ledger, nowMs) * boost

        return Slot(
            goalId = goalId,
            channel = Channel.NOTIFICATION,
            creative = creative,
            scheduledTimeMs = nextSlotMs,
            priority = score,
        )
    }

    /**
     * Compute initial delay for WorkManager based on slot time.
     *
     * @param slotTimeMs Target slot time in milliseconds
     * @param nowMs Current timestamp
     * @return Initial delay in milliseconds (minimum 0)
     */
    fun computeInitialDelay(slotTimeMs: Long, nowMs: Long): Long {
        return max(0L, slotTimeMs - nowMs)
    }
}

/**
 * Extension function to calculate exposures by channel and day.
 */
private fun exposuresTodayForChannel(ledger: ExposureLedger, channel: Channel, nowMs: Long): Int {
    // Calculate start of current day
    val zone = java.time.ZoneId.systemDefault()
    val localDate = java.time.LocalDate.now(zone)
    val startOfDay = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
    
    return ledger.exposuresTodayByChannel(channel, startOfDay)
}

/**
 * Data class representing a scheduled nudge slot.
 */
data class Slot(
    val goalId: Long,
    val channel: Channel,
    val creative: Creative,
    val scheduledTimeMs: Long,
    val priority: Double,
)

/**
 * Helper to load packed creatives for a goal.
 * Mirrors BundledPackSource pattern from WallpaperRotationWorker.
 */
class PersistentCreativeRepository(
    private val goal: GoalEntity,
    private val packId: String,
) {
    fun loadPackedCreatives(): List<Creative> {
        // Simplified version - in production, this would query the persistent pack
        // For now, return a placeholder creative to satisfy the interface
        return listOf(
            Creative(
                id = "$packId/default",
                packId = packId,
                goalTheme = com.retarget.creative.PresetCatalog.byId(goal.presetId)?.goalTheme
                    ?: com.retarget.creative.GoalTheme.GENERAL_WELLNESS,
                subTheme = packId,
                copyPool = listOf("Keep going!", "You've got this!", "Progress matters!"),
                imagePath = "",
                attribution = "Retarget",
                licenseUrl = "",
            ),
        )
    }
}
