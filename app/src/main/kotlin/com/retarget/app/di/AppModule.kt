/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.app.di

import android.content.Context
import androidx.work.WorkManager
import com.retarget.creative.ExposureDao
import com.retarget.creative.RoomExposureLedger
import com.retarget.scheduler.WallpaperScheduler
import com.retarget.scheduler.WallpaperSchedulerManager
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

    /**
     * Provides [RoomExposureLedger] wired to the Room database.
     * Singleton-scoped to share exposure state across the app lifetime.
     */
    @Provides
    @Singleton
    fun provideExposureLedger(dao: ExposureDao): RoomExposureLedger = RoomExposureLedger(dao)

    /**
     * Provides WorkManager instance for wallpaper scheduling.
     * Initialized lazily via getInstance()—no direct injection needed,
     * but binding it here documents the dependency and enables mocking in tests.
     */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /**
     * Exposes [WallpaperScheduler] as an injectable service for clean separation
     * between domain logic and Android scheduling plumbing.
     */
    @Provides
    @Singleton
    fun provideWallpaperScheduler(): WallpaperScheduler = WallpaperScheduler

    /**
     * Provides [WallpaperSchedulerManager] to bind goal lifecycle to scheduler state.
     * Monitors active goals and auto-starts/stops wallpaper rotation.
     */
    @Provides
    @Singleton
    fun provideWallpaperSchedulerManager(
        @ApplicationContext context: Context,
        repository: com.retarget.goal.GoalRepository,
    ): WallpaperSchedulerManager = WallpaperSchedulerManager(context, repository)
}
