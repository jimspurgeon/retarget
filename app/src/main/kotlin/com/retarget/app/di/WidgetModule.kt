/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.di

import com.retarget.widget.DefaultWidgetStateSource
import com.retarget.widget.WidgetStateSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency graph for the M3.2 widget render layer (Worker B).
 *
 * Binds the [WidgetStateSource] seam so Glance-constructed classes
 * ([com.retarget.widget.RetargetWidget], [com.retarget.widget.CheckInActionCallback])
 * can resolve it via [com.retarget.widget.WidgetEntryPoint]. CheckInDao is
 * provided by AppModule since M3.1 export landed on main.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    @Singleton
    abstract fun bindWidgetStateSource(impl: DefaultWidgetStateSource): WidgetStateSource
}
