/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retarget.app.R
import com.retarget.goal.PresetCampaign

/**
 * Theme accent per preset — placeholder visuals until creative packs land
 * (issue #4). Derived from the active color scheme's tonal containers so the
 * chips follow Material You dynamic color instead of fixed brand hex values.
 */
@Composable
private fun presetAccent(id: String): Color =
    when (id) {
        "hydration" -> MaterialTheme.colorScheme.primaryContainer
        "fresh-air" -> MaterialTheme.colorScheme.tertiaryContainer
        "fruit" -> MaterialTheme.colorScheme.errorContainer // tonal coral, not an error signal
        "vegetables" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

/**
 * Preset picker: the "browse our campaign catalog" moment. One tap installs.
 *
 * @param catalogMode True when opened from the dashboard's "Add another
 *   campaign" (add-campaign route, #40): hides the first-run extras (ticker
 *   opt-in, skip) and swaps to the catalog title. Activation itself is the
 *   same install path as first run.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    catalogMode: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onFinished()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(
                if (catalogMode) R.string.add_campaign_title else R.string.screen_onboarding_title,
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(state.presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    installing = state.installing == preset.id,
                    enabled = state.installing == null,
                    onClick = { viewModel.activate(preset.id) },
                )
            }
            if (!catalogMode) {
                item {
                    TickerOptInCard(
                        checked = state.tickerOptIn,
                        onCheckedChange = viewModel::setTickerOptIn,
                    )
                }
            }
        }
        if (!catalogMode) {
            TextButton(onClick = viewModel::skip, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

/**
 * Lock-screen ticker opt-in (PHASE3-AGENCY.md §8 decision #4).
 *
 * Discovery prompt, placed with preset selection. Strictly opt-in: the
 * switch starts unchecked and nothing is enabled until the user acts
 * (AGENTS.md §2 — no pre-checked boxes). Plain language explains what it
 * does and that it is changeable anytime.
 */
@Composable
private fun TickerOptInCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_ticker_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.onboarding_ticker_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PresetCard(
    preset: PresetCampaign,
    installing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val displayNameRes = when (preset.id) {
        "hydration" -> R.string.preset_hydration_name
        "fresh-air" -> R.string.preset_fresh_air_name
        "fruit" -> R.string.preset_fruit_name
        "vegetables" -> R.string.preset_vegetables_name
        else -> R.string.preset_fresh_air_name // fallback
    }
    val blurbRes = when (preset.id) {
        "hydration" -> R.string.preset_hydration_blurb
        "fresh-air" -> R.string.preset_fresh_air_blurb
        "fruit" -> R.string.preset_fruit_blurb
        "vegetables" -> R.string.preset_vegetables_blurb
        else -> R.string.preset_fresh_air_blurb // fallback
    }

    Card(
        modifier =
            Modifier.fillMaxWidth().clickable(enabled = enabled && !installing, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(48.dp).background(presetAccent(preset.id), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = preset.emoji, style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(displayNameRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(blurbRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
