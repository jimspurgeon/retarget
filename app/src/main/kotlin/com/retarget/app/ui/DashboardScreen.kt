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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.retarget.app.R
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dashboard: one card per active goal (display name, emoji, today's check-in
 * count, in-app check-in button) plus aggregate pacing for today. The in-app
 * check-in shares the notification/widget code path, so all three entry
 * points feed the same bandit learning signal (#40).
 *
 * @param viewModel Dashboard view model providing per-goal and pacing state.
 * @param onNavigateToSettings Callback triggered when the settings FAB is clicked.
 * @param onNavigateToTransparency Callback triggered when the transparency FAB is clicked.
 * @param onAddCampaign Opens the campaign catalog (add-campaign route, #40).
 * @param onPinWidget Requests the system pin flow for the home-screen widget
 *   (M3.2); the goal picker (WidgetConfigurationActivity) follows automatically
 *   because the widget declares a configure activity. Returns whether the
 *   launcher accepted the request.
 * @param onPinUnsupported Invoked when the launcher refuses pin requests, so the
 *   host can point the user at the launcher's widget picker.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransparency: () -> Unit = {},
    onAddCampaign: () -> Unit = {},
    onPinWidget: () -> Boolean = { false },
    onPinUnsupported: () -> Unit = {},
) {
    val wallpaperPacing by (viewModel?.wallpaperPacingSummary ?: MutableStateFlow(DailyPacingSummary(0, 0)))
        .collectAsState(initial = DailyPacingSummary(0, 0))
    val notificationPacing by (viewModel?.notificationPacingSummary ?: MutableStateFlow(DailyPacingSummary(0, 0)))
        .collectAsState(initial = DailyPacingSummary(0, 0))
    val goalCards by (viewModel?.perGoalState ?: MutableStateFlow(emptyList()))
        .collectAsState(initial = emptyList())

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                }
            }

            if (viewModel != null) {
                // One card per active goal (#40): display identity + today's
                // progress + the in-app check-in affordance.
                items(goalCards, key = { it.id }) { goal ->
                    GoalCard(
                        goal = goal,
                        onCheckIn = { viewModel.checkIn(goal.id) },
                    )
                }

                item {
                    OutlinedButton(onClick = onAddCampaign, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dashboard_add_goal))
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.dashboard_pacing_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        PacingRow(
                            label = stringResource(R.string.settings_channel_wallpaper),
                            count = wallpaperPacing.countToday,
                            target = wallpaperPacing.targetPerDay,
                        )

                        Spacer(Modifier.height(8.dp))

                        PacingRow(
                            label = stringResource(R.string.settings_channel_notification),
                            count = notificationPacing.countToday,
                            target = notificationPacing.targetPerDay,
                        )
                    }
                }

                item {
                    // Widget pin affordance (M3.2, gatekeeper M2 fix): the system
                    // pin flow launches WidgetConfigurationActivity (declared as
                    // android:configure), where the user picks which goal shows.
                    OutlinedButton(onClick = { if (!onPinWidget()) onPinUnsupported() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dashboard_add_widget))
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_placeholder_body),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToTransparency,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = stringResource(R.string.transparency_title),
            )
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
 * One goal's card: emoji + display name, today's check-in count, and the
 * in-app check-in button. Tapping records a check-in (same transactional
 * path as notification/widget actions) and the count updates reactively.
 */
@Composable
private fun GoalCard(
    goal: GoalCardState,
    onCheckIn: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.emoji,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = goal.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_checkins_today, goal.checkInsToday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_checkin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onCheckIn) {
                Text(stringResource(R.string.dashboard_check_in))
            }
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
                text = stringResource(R.string.dashboard_pacing_target_suffix, target),
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
                text = stringResource(R.string.dashboard_pacing_disabled),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalCardPreview() {
    GoalCard(
        goal = GoalCardState(id = 1, displayName = "Hydration", emoji = "💧", checkInsToday = 2),
        onCheckIn = {},
    )
}
