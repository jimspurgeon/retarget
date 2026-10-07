/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.retarget.app.MainActivity
import com.retarget.app.R
import java.io.File

/**
 * Home-screen widget (M3.2): renders [WidgetSnapshot] as a glanceable card.
 *
 * - [WidgetSnapshot.Empty] → onboarding hint, tap opens the app
 * - [WidgetSnapshot.Active] → creative image background (or themed fallback),
 *   goal name, pacing text, one-tap check-in button
 * - [WidgetSnapshot.Error] → plain message, tap reopens the app
 *
 * Dependencies are obtained via Hilt EntryPoint (Glance constructs widgets itself).
 */
class RetargetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val stateSource: WidgetStateSource = WidgetEntryPoint.get(context).widgetStateSource()
        val snapshot = stateSource.current()
        val channels = stateSource.channelEnablement()

        val strings =
            object : WidgetUiTextMapper.Strings {
                override val emptyTitle = context.getString(R.string.widget_empty_title)
                override val emptyHint = context.getString(R.string.widget_empty_hint)
                override val widget_error_message = context.getString(R.string.widget_error_message)
                override val checkInButton = context.getString(R.string.widget_check_in_button)
                override val checkInDoneButton = context.getString(R.string.widget_check_in_done_button)
                override val pacingFormat = context.getString(R.string.widget_pacing_text)
            }
        val uiText = WidgetUiTextMapper.snapshotToUiText(snapshot, strings, channels.wallpaperEnabled, channels.notificationEnabled)

        provideContent {
            when (snapshot) {
                is WidgetSnapshot.Empty -> EmptyContent(uiText)
                is WidgetSnapshot.Error -> ErrorContent(uiText)
                is WidgetSnapshot.Active -> ActiveContent(snapshot, uiText)
            }
        }
    }

    /** Empty state: onboarding hint; whole widget opens the app. */
    @Composable
    private fun EmptyContent(uiText: WidgetUiText) {
        WidgetCard(modifier = GlanceModifier.background(FALLBACK_COLOR)) {
            Text(text = uiText.headline.orEmpty(), style = TITLE_STYLE)
            Text(text = uiText.body.orEmpty(), style = BODY_STYLE)
        }
    }

    /** Error state: plain message; tapping reopens the app. */
    @Composable
    private fun ErrorContent(uiText: WidgetUiText) {
        WidgetCard(modifier = GlanceModifier.background(ERROR_COLOR)) {
            Text(
                text = uiText.headline.orEmpty(),
                style = TITLE_STYLE,
            )
        }
    }

    /** Active state: creative background (fallback themed color), goal, pacing, check-in. */
    @Composable
    private fun ActiveContent(
        active: WidgetSnapshot.Active,
        uiText: WidgetUiText,
    ) {
        val backgroundMod =
            if (active.creativeImagePath != null && File(active.creativeImagePath).isFile) {
                try {
                    val bitmap = BitmapFactory.decodeFile(active.creativeImagePath)
                    if (bitmap != null) {
                        GlanceModifier.background(ImageProvider(bitmap))
                    } else {
                        GlanceModifier.background(FALLBACK_COLOR)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode creative image ${active.creativeImagePath}", e)
                    GlanceModifier.background(FALLBACK_COLOR)
                }
            } else {
                GlanceModifier.background(FALLBACK_COLOR)
            }

        WidgetCard(modifier = backgroundMod) {
            uiText.headline?.let { headline ->
                Text(text = headline, style = TITLE_STYLE)
            }
            uiText.body?.let { body ->
                Text(text = body, style = BODY_STYLE)
            }
            uiText.checkInLabel?.let { label ->
                Button(
                    text = label,
                    onClick =
                        actionRunCallback<CheckInActionCallback>(
                            actionParametersOf(CheckInActionCallback.CHECKED_IN_TODAY to active.checkedInToday),
                        ),
                    modifier = GlanceModifier.padding(top = 8.dp),
                )
            }
        }
    }

    /** Shared card chrome: full-size box opening the app, with centered content column. */
    @Composable
    private fun WidgetCard(
        modifier: GlanceModifier = GlanceModifier,
        content: @Composable () -> Unit,
    ) {
        Box(
            modifier = modifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }

    private companion object {
        private const val TAG = "RetargetWidget"

        // Themed fallback colors (no creative image available).
        private val FALLBACK_COLOR = ColorProvider(Color(0xFF1B5E20))
        private val ERROR_COLOR = ColorProvider(Color(0xFF4E342E))

        private val TITLE_STYLE =
            TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        private val BODY_STYLE = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp)
    }
}
