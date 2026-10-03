/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retarget.app.ui.DashboardScreen
import com.retarget.app.ui.onboarding.OnboardingScreen
import com.retarget.goal.GoalRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        repo: GoalRepository,
    ) : ViewModel() {
        val hasGoals =
            repo
                .observeActive()
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)
    }

/**
 * Single-activity Compose shell. First launch routes to onboarding; once any
 * goal is active the dashboard is home.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root(rootViewModel: RootViewModel = hiltViewModel()) {
    val hasGoals by rootViewModel.hasGoals.collectAsState()
    if (hasGoals) {
        DashboardScreen()
    } else {
        OnboardingScreen(onFinished = { /* hasGoals flips via Flow; nothing to do */ })
    }
}
