/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retarget.app.R
import com.retarget.channels.notification.TickerChannel
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalRepository
import com.retarget.scheduler.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Settings screen: displays quiet hours and per-channel toggles for the active goal.
 * Extensible structure for future settings (quiet-hours editing, pacing adjustments).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val exportViewModel: ExportViewModel = hiltViewModel()
    val isExporting by exportViewModel.isExporting.collectAsState()

    // System file picker (ACTION_CREATE_DOCUMENT). A null URI means the user
    // cancelled: no export, no toast, no crash (requirement: cancellation is a no-op).
    val createExportFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            exportViewModel.exportTo(
                openSink = { context.contentResolver.openOutputStream(uri) },
                onSuccess = {
                    Toast
                        .makeText(context, context.getString(R.string.settings_export_success), Toast.LENGTH_SHORT)
                        .show()
                },
                onError = {
                    Toast
                        .makeText(context, context.getString(R.string.settings_export_failed), Toast.LENGTH_LONG)
                        .show()
                },
            )
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.screen_settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                QuietHoursCard(uiState.quietHoursStart, uiState.quietHoursEnd)
            }

            item {
                ExportDataCard(
                    enabled = !isExporting,
                    onClick = { createExportFileLauncher.launch(buildSuggestedExportFileName()) },
                )
            }

            item {
                ResetLearningCard(onClick = { viewModel.resetLearning(context) })
            }

            items(uiState.channelSettings, key = { it.channelName }) { channel ->
                ChannelToggleCard(
                    channelName = channel.channelName,
                    enabled = channel.enabled,
                    targetsPerDay = channel.targetsPerDay,
                    onToggle = { viewModel.toggleChannel(channel.channelName, context) },
                )
            }
        }
    }
}

@Composable
private fun QuietHoursCard(startHour: Int, endHour: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_quiet_hours_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$startHour:00 – $endHour:00",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_quiet_hours_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ChannelToggleCard(
    channelName: String,
    enabled: Boolean,
    targetsPerDay: Int,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (channelName) {
                        "Wallpaper" -> stringResource(R.string.settings_channel_wallpaper)
                        "Notification" -> stringResource(R.string.settings_channel_notification)
                        "Ticker" -> stringResource(R.string.settings_channel_ticker)
                        else -> channelName
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_channel_target, targetsPerDay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (channelName == "Ticker") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_ticker_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}

/** UI state driven by SettingsViewModel. */
data class SettingsUiState(
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 7,
    val channelSettings: List<ChannelSetting> = emptyList(),
)

/**
 * "Export my data" settings row. Mirrors the quiet-hours card styling.
 * Disabled while an export is in flight to prevent double-taps.
 */
@Composable
private fun ExportDataCard(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_export_action),
                style = MaterialTheme.typography.titleMedium,
                // Visually de-emphasize while a write is in flight
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_export_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ResetLearningCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_reset_learning_action),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_reset_learning_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

data class ChannelSetting(
    val channelName: String,
    val enabled: Boolean,
    val targetsPerDay: Int,
)

/**
 * ViewModel for SettingsScreen.
 * Binds to the active goal's CampaignSettings and exposes channel-level toggles.
 *
 * Handles:
 * - Per-channel toggle with immediate WorkManager cancellation/rescheduling
 * - Toast feedback for user actions (notifications paused/resumed)
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repo: GoalRepository,
        private val learningExporter: com.retarget.learning.LearningExporter,
    ) : ViewModel() {

        val uiState =
            repo
                .observeActive()
                .map { goals ->
                    goals.firstOrNull()?.let { goal ->
                        buildSettingsUiState(goal.settings)
                    } ?: buildSettingsUiState(CampaignSettings(wallpaperTargetsPerDay = 1, notificationTargetsPerDay = 0))
                }
                .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = SettingsUiState())

        /**
         * Toggles a channel's enabled state.
         *
         * For "Notification" channel:
         * - On disable: cancels WorkManager job, shows Toast
         * - On enable: reschedules WorkManager job
         *
         * For "Wallpaper" channel:
         * - Updates settings (scheduler manager handles WorkManager changes)
         */
        fun toggleChannel(
            channelName: String,
            context: Context,
        ) {
            viewModelScope.launch {
                val activeGoals = repo.observeActive().first()
                activeGoals.firstOrNull()?.let { goal ->
                    val currentSettings = goal.settings
                    val newEnabled =
                        when (channelName) {
                            "Wallpaper" -> !currentSettings.wallpaperEnabled
                            "Notification" -> !currentSettings.notificationEnabled
                            "Ticker" -> !currentSettings.tickerEnabled
                            else -> false
                        }

                    val updatedSettings =
                        when (channelName) {
                            "Wallpaper" ->
                                currentSettings.copy(wallpaperEnabled = newEnabled)

                            "Notification" ->
                                currentSettings.copy(notificationEnabled = newEnabled)

                            "Ticker" ->
                                currentSettings.copy(tickerEnabled = newEnabled)

                            else -> currentSettings
                        }

                    repo.updateSettings(goal.id, updatedSettings)

                    // Handle WorkManager changes and show Toast
                    handleChannelToggleEffects(
                        context = context,
                        channelName = channelName,
                        newEnabled = newEnabled,
                        goalId = goal.id,
                    )
                }
            }
        }

        /**
         * M3.4: wipes ALL learning state (one-tap; per-goal reset arrives with
         * goal-scoped UI later). Toast confirms on completion.
         */
        fun resetLearning(context: Context) {
            viewModelScope.launch {
                val cleared = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    learningExporter.wipeAllLearning()
                }
                android.os.Handler(context.mainLooper).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_reset_learning_done) +
                            " ($cleared)",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        private fun handleChannelToggleEffects(
            context: Context,
            channelName: String,
            newEnabled: Boolean,
            goalId: Long,
        ) {
            // This runs in a CoroutineScope, need to dispatch to main for Toast
            android.os.Handler(context.mainLooper).post {
                when (channelName) {
                    "Notification" -> {
                        if (!newEnabled) {
                            // Cancel any legacy per-goal notification work (cleanup;
                            // the global delivery worker filters by per-goal settings)
                            NotificationScheduler.cancelAllNotificationsForGoal(context, goalId)
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_notifications_paused),
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            // Nothing to schedule: NotificationSchedulerManager observes
                            // the settings change and (re)starts the global delivery
                            // worker — the single scheduling path after the
                            // v0.3.x consolidation (no per-goal periodic work).
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_notifications_resumed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    "Wallpaper" -> {
                        // WallpaperSchedulerManager will handle the change
                        // No Toast needed for wallpaper toggle
                    }
                    "Ticker" -> {
                        if (!newEnabled) {
                            // Remove any showing ticker immediately (reversibility,
                            // AGENTS.md §2); the delivery worker also filters disabled
                            // goals on its next run.
                            TickerChannel(context).cancelForGoal(goalId)
                        }
                        // On enable there is nothing to schedule here: the delivery
                        // worker picks up ticker-enabled goals on its next cycle.
                        Toast.makeText(
                            context,
                            context.getString(
                                if (newEnabled) R.string.toast_ticker_resumed else R.string.toast_ticker_paused,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }

        private fun buildSettingsUiState(settings: CampaignSettings): SettingsUiState =
            SettingsUiState(
                quietHoursStart = 22,
                quietHoursEnd = 7,
                channelSettings =
                    listOf(
                        ChannelSetting(
                            channelName = "Wallpaper",
                            enabled = settings.wallpaperEnabled,
                            targetsPerDay = settings.wallpaperTargetsPerDay,
                        ),
                        ChannelSetting(
                            channelName = "Notification",
                            enabled = settings.notificationEnabled,
                            targetsPerDay = settings.notificationTargetsPerDay,
                        ),
                        ChannelSetting(
                            channelName = "Ticker",
                            enabled = settings.tickerEnabled,
                            targetsPerDay = settings.tickerTargetsPerDay,
                        ),
                    ),
            )
    }
