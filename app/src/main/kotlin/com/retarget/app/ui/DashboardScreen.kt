/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retarget.app.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Phase 0 placeholder dashboard. Phase 1 replaces the body with real campaign
 * state (goals, exposures, pacing) from a DashboardViewModel.
 *
 * @param viewModel Dashboard view model providing pacing state.
 * @param onNavigateToSettings Callback triggered when the settings FAB is clicked.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel? = null,
    onNavigateToSettings: () -> Unit = {},
) {
    val wallpaperPacing by (viewModel?.wallpaperPacingSummary ?: MutableStateFlow(DailyPacingSummary(0, 0)))
        .collectAsState(initial = DailyPacingSummary(0, 0))
    val notificationPacing by (viewModel?.notificationPacingSummary ?: MutableStateFlow(DailyPacingSummary(0, 0)))
        .collectAsState(initial = DailyPacingSummary(0, 0))

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null, // decorative; text conveys the same meaning
                modifier = Modifier.height(96.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.screen_dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_tagline),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))

            // Pacing summary section
            if (viewModel != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "Campaign Pacing",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))

                    // Wallpaper pacing
                    PacingRow(
                        label = stringResource(R.string.settings_channel_wallpaper),
                        count = wallpaperPacing.countToday,
                        target = wallpaperPacing.targetPerDay,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Notification pacing
                    PacingRow(
                        label = stringResource(R.string.settings_channel_notification),
                        count = notificationPacing.countToday,
                        target = notificationPacing.targetPerDay,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.dashboard_placeholder_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FloatingActionButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_icon_content_description),
            )
        }
    }
}

/**
 * Pacing row showing channel usage vs target.
 *
 * @param label Channel label (e.g., "Wallpaper", "Notifications")
 * @param count Number of exposures today
 * @param target Daily target for this channel
 */
@Composable
private fun PacingRow(
    label: String,
    count: Int,
    target: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (target > 0) {
            Text(
                text = "/$target today",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            LinearProgressIndicator(
                progress = minOf(count.toFloat() / target, 1.0f),
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(4.dp),
            )
        } else {
            Text(
                text = "disabled",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
