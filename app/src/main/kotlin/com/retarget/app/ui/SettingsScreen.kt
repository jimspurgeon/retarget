/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retarget.app.R
import com.retarget.goal.CampaignSettings
import com.retarget.goal.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val uiState by viewModel.uiState.collectAsState()

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

            items(uiState.channelSettings, key = { it.channelName }) { channel ->
                ChannelToggleCard(
                    channelName = channel.channelName,
                    enabled = channel.enabled,
                    targetsPerDay = channel.targetsPerDay,
                    onToggle = { viewModel.toggleChannel(channel.channelName) },
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
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_channel_target, targetsPerDay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

data class ChannelSetting(
    val channelName: String,
    val enabled: Boolean,
    val targetsPerDay: Int,
)

/**
 * ViewModel for SettingsScreen.
 * Binds to the active goal's CampaignSettings and exposes channel-level toggles.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repo: GoalRepository,
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

        fun toggleChannel(channelName: String) {
            viewModelScope.launch {
                val activeGoals = repo.observeActive().first()
                activeGoals.firstOrNull()?.let { goal ->
                    val updatedSettings =
                        when (channelName) {
                            "Wallpaper" ->
                                goal.settings.copy(wallpaperEnabled = !goal.settings.wallpaperEnabled)

                            "Notification" ->
                                goal.settings.copy(notificationEnabled = !goal.settings.notificationEnabled)

                            else -> goal.settings
                        }
                    repo.updateSettings(goal.id, updatedSettings)
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
                    ),
            )
    }
