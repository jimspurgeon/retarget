/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.retarget.creative.ExposureDao
import com.retarget.creative.ExposureEntity

@Database(entities = [GoalEntity::class, ExposureEntity::class], version = 2)
@TypeConverters(GoalConverters::class)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    abstract fun exposureDao(): ExposureDao

    companion object {
        @Volatile
        private var instance: GoalDatabase? = null

        fun get(context: Context): GoalDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        GoalDatabase::class.java,
                        "retarget.db",
                    )
                    // Pre-alpha (no tagged release shipped schema v1): a schema
                    // change falls back to an empty DB rather than shipping a
                    // migration. Replace with real migrations at first release tag.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
