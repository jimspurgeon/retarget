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

@Database(
    entities = [
        GoalEntity::class,
        com.retarget.creative.CreativePackEntity::class,
        com.retarget.creative.CreativeEntity::class,
    ],
    version = 2,
)
@TypeConverters(GoalConverters::class)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    abstract fun creativePackDao(): com.retarget.creative.CreativePackDao

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
                    ).build()
                    .also { instance = it }
            }
    }
}
