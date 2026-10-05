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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.retarget.app.R
import com.retarget.app.ui.DashboardScreen
import com.retarget.app.ui.DashboardViewModel
import com.retarget.app.ui.DailyPacingSummary
import com.retarget.app.ui.SettingsScreen
import com.retarget.app.ui.TransparencyScreen
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
        requestNotificationPermissionIfNeeded()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Root()
                }
            }
        }
    }

    /**
     * POST_NOTIFICATIONS runtime request (Android 13+). Without it, notification
     * delivery is silently denied and the worker retries forever (gatekeeper B4).
     * Called once at launch; user can change anytime in system settings.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
        }
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 1001
    }
}

@Composable
private fun Root(rootViewModel: RootViewModel = hiltViewModel()) {
    val hasGoals = rootViewModel.hasGoals.collectAsState(initial = false)
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = getStartDestination(hasGoals.value)) {
        composable("onboarding") {
            OnboardingScreen(
                onFinished = {
                    // Navigation will re-route automatically when hasGoals flips
                },
            )
        }
        composable("dashboard") {
            DashboardScreen(
                viewModel = hiltViewModel(),
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToTransparency = {
                    navController.navigate("transparency")
                },
            )
        }
        composable("settings") {
            SettingsScreen()
        }
        composable("transparency") {
            TransparencyRoute()
        }
    }
}

@Composable
private fun TransparencyRoute(viewModel: DashboardViewModel = hiltViewModel()) {
    val goals by viewModel.activeGoals.collectAsState(initial = emptyList())
    val notifPacing by viewModel.notificationPacingSummary.collectAsState(
        initial = DailyPacingSummary(0, 0),
    )
    val goal = goals.firstOrNull()
    TransparencyScreen(
        goalName = goal?.displayName ?: "",
        techniqueRationale = stringResource(R.string.transparency_default_rationale),
        notificationCountToday = notifPacing.countToday,
        notificationBudget = notifPacing.targetPerDay,
        onOpenSettings = { /* handled by nav; transparency route includes its own settings link */ },
    )
}

@Composable
private fun getStartDestination(hasGoals: Boolean): String =
    if (hasGoals) "dashboard" else "onboarding"
