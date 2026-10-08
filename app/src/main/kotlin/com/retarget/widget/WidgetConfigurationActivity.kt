/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.retarget.app.R
import com.retarget.goal.GoalRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Widget configuration screen (M3.2, gatekeeper M2 fix): a minimal radio-list
 * of the user's active goals, launched by the system when a widget with a
 * `configure` activity is added (and re-launchable from the launcher's
 * widget settings). Selecting a goal persists the per-goal widget
 * preference and finishes with RESULT_OK so the launcher places the widget.
 *
 * Uses plain Android Views rather than Compose: it stays outside the app's
 * navigation graph and is intentionally minimal (one screen, radio list,
 * save). Glance reads the preference on its next render, so no result
 * extras are required.
 *
 * AGENTS.md §2: user-directed, transparent — the goal list shows exactly
 * what will be displayed; changing it later is as easy as this screen was
 * to reach (launcher long-press → widget settings).
 */
@AndroidEntryPoint
class WidgetConfigurationActivity : ComponentActivity() {
    @Inject
    lateinit var goalRepository: GoalRepository

    @Inject
    lateinit var widgetGoalPreference: WidgetGoalPreferenceStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Canceled until the user actually picks a goal (launcher aborts the pin).
        setResult(RESULT_CANCELED)

        scope.launch {
            val goals = goalRepository.observeActive().first()
            withContext(Dispatchers.Main) { renderGoals(goals.map { it.id to it.displayName }) }
        }
    }

    private fun renderGoals(goals: List<Pair<Long, String>>) {
        if (goals.isEmpty()) {
            Toast.makeText(this, R.string.widget_config_no_goals, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val padding = (resources.displayMetrics.density * 16).toInt()
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
            }

        root.addView(
            TextView(this).apply {
                text = getString(R.string.widget_config_title)
                textSize = 18f
            },
        )

        val radioGroup =
            RadioGroup(this).apply {
                setPadding(0, padding, 0, 0)
            }

        goals.forEach { (id, name) ->
            radioGroup.addView(
                RadioButton(this).apply {
                    text = name
                    tag = id
                },
            )
        }

        root.addView(radioGroup)

        root.addView(
            Button(this).apply {
                text = getString(R.string.widget_config_save)
                setOnClickListener {
                    val checkedId = radioGroup.checkedRadioButtonId
                    if (checkedId == -1) {
                        Toast.makeText(this@WidgetConfigurationActivity, R.string.widget_config_pick_first, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val goalId = radioGroup.findViewById<RadioButton>(checkedId).tag as Long
                    widgetGoalPreference.set(goalId)
                    setResult(RESULT_OK)
                    finish()
                }
            },
        )

        setContentView(
            ScrollView(this).apply {
                addView(
                    root,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
