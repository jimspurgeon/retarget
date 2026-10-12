/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility as TopLevelAnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.retarget.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dashboard: one card per active goal (display name, emoji, today's check-in
 * count, in-app check-in button) plus aggregate pacing for today. The in-app
 * check-in shares the notification/widget code path, so all three entry
 * points feed the same bandit learning signal (#40).
 *
 * @param viewModel Dashboard view model providing per-goal and pacing state.
 * @param onNavigateToSettings Callback triggered when the settings icon is tapped.
 * @param onNavigateToTransparency Callback triggered when the transparency icon is tapped.
 * @param onAddCampaign Opens the campaign catalog (add-campaign route, #40).
 * @param onPinWidget Requests the system pin flow for the home-screen widget
 *   (M3.2); the goal picker (WidgetConfigurationActivity) follows automatically
 *   because the widget declares a configure activity. Returns whether the
 *   launcher accepted the request.
 * @param onPinUnsupported Invoked when the launcher refuses pin requests, so the
 *   host can point the user at the launcher's widget picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.screen_dashboard_title)) },
                actions = {
                    IconButton(onClick = onNavigateToTransparency) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.transparency_title),
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_icon_content_description),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (viewModel != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddCampaign,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.dashboard_add_goal)) },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    OutlinedButton(
                        onClick = { if (!onPinWidget()) onPinUnsupported() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.dashboard_add_widget))
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_placeholder_body),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * One goal's card: emoji + display name, today's check-in count, and the
 * in-app check-in button. Tapping records a check-in (same transactional
 * path as notification/widget actions) and the count updates reactively.
 * A brief "checked in ✓" confirmation scales in over the button, then fades
 * back so the affordance stays available for the next check-in.
 */
@Composable
private fun GoalCard(
    goal: GoalCardState,
    onCheckIn: () -> Unit,
) {
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation) {
        // Auto-dismiss the confirmation so the button returns to its
        // steady "Check in" state without any user action.
        LaunchedEffect(goal.checkInsToday) {
            delay(1200)
            showConfirmation = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
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
            CheckInButton(
                showConfirmation = showConfirmation,
                onCheckIn = {
                    showConfirmation = true
                    onCheckIn()
                },
            )
        }
    }
}

/**
 * Check-in button with a transient confirmation: a check icon springs in
 * centered over the button, holds briefly, then fades so the affordance
 * stays available for the next check-in.
 */
@Composable
private fun CheckInButton(
    showConfirmation: Boolean,
    onCheckIn: () -> Unit,
) {
    Box {
        FilledTonalButton(onClick = onCheckIn) {
            Text(stringResource(R.string.dashboard_check_in))
        }
        TopLevelAnimatedVisibility(
            visible = showConfirmation,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = scaleOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null, // transient confirmation; count text above changes too
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Pacing row showing channel usage vs target. The progress fraction is
 * spring-animated so mid-day count updates settle naturally instead of
 * snapping to their new width.
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
            val fraction = minOf(count.toFloat() / target, 1.0f)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "pacingProgress",
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
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
