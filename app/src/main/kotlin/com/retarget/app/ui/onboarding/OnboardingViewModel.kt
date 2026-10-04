/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retarget.goal.GoalRepository
import com.retarget.goal.PresetCampaign
import com.retarget.goal.PresetCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val presets: List<PresetCampaign> = PresetCatalog.ALL,
    val installing: String? = null, // presetId currently being activated
    val done: Boolean = false,
)

/**
 * Drives preset selection and one-tap campaign activation ("instant campaign").
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val repo: GoalRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OnboardingUiState())
        val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

        fun activate(presetId: String) {
            if (_state.value.installing != null) return // guard double-taps
            _state.value = _state.value.copy(installing = presetId)
            viewModelScope.launch {
                repo.installPreset(presetId, nowMs = System.currentTimeMillis())
                _state.value = _state.value.copy(installing = null, done = true)
            }
        }

        /** Skip onboarding entirely (no goal installed yet; reachable later). */
        fun skip() {
            _state.value = _state.value.copy(done = true)
        }
    }
