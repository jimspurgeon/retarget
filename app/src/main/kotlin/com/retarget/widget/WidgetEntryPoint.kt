/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Access to the widget's Hilt graph from Glance-constructed classes.
 *
 * Glance instantiates [RetargetWidget] and [CheckInActionCallback] itself, so
 * there is no constructor injection site; they resolve their collaborator
 * ([WidgetStateSource], suspend methods) through this entry point instead.
 */
interface WidgetEntryPoint {
    fun widgetStateSource(): WidgetStateSource

    companion object {
        /** Resolves the entry point from the application context (any caller Context works). */
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    Holder::class.java,
                ).let { holder ->
                    object : WidgetEntryPoint {
                        override fun widgetStateSource(): WidgetStateSource = holder.widgetStateSource()
                    }
                }
    }

    /** Hilt-generated holder; separate from the public interface to keep it internal. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Holder {
        fun widgetStateSource(): WidgetStateSource
    }
}
