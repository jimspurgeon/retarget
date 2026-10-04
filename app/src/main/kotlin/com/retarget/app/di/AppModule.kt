/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * App-wide bindings. Kept intentionally small until Phase 1 adds the
 * database/rotator/scheduler graph. Dispatchers are injected (never hard-coded)
 * so domain logic stays JVM-unit-testable.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context,
    ): Context = context

    @Provides
    @Singleton
    fun provideGoalDatabase(
        @ApplicationContext context: Context,
    ): com.retarget.goal.GoalDatabase =
        com.retarget.goal.GoalDatabase
            .get(context)

    @Provides
    fun provideGoalDao(db: com.retarget.goal.GoalDatabase): com.retarget.goal.GoalDao = db.goalDao()

    @Provides
    @Singleton
    fun provideGoalRepository(dao: com.retarget.goal.GoalDao): com.retarget.goal.GoalRepository = com.retarget.goal.GoalRepository(dao)

    @Provides
    @Singleton
    fun provideCreativePackDao(db: com.retarget.goal.GoalDatabase): com.retarget.creative.CreativePackDao = db.creativePackDao()

    @Provides
    @Singleton
    fun providePersistentCreativeRepository(
        context: Context,
        dao: com.retarget.creative.CreativePackDao,
    ): com.retarget.creative.PersistentCreativeRepository = com.retarget.creative.PersistentCreativeRepository(context, dao)

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
