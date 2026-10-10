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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.retarget.analytics.CheckInDao
import com.retarget.analytics.CheckInEntity
import com.retarget.creative.ExposureDao
import com.retarget.creative.ExposureEntity
import com.retarget.learning.LearningStateDao
import com.retarget.learning.LearningStateEntity

@Database(
    entities = [
        GoalEntity::class,
        ExposureEntity::class,
        com.retarget.creative.CreativePackEntity::class,
        com.retarget.creative.CreativeEntity::class,
        CheckInEntity::class,
        LearningStateEntity::class,
    ],
    version = 4,
)
@TypeConverters(GoalConverters::class)
abstract class GoalDatabase : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    abstract fun exposureDao(): ExposureDao

    abstract fun creativePackDao(): com.retarget.creative.CreativePackDao

    abstract fun checkInDao(): CheckInDao

    abstract fun learningStateDao(): LearningStateDao

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
                    // v3 -> v4 (M3.4) is a pure ADDITIVE migration: only the
                    // learning_state table is new. Below v3 the schema predates
                    // any tagged release (v0.3.x shipped version 3); anything
                    // older still falls back destructively.
                    .addMigrations(LEARNING_STATE_MIGRATION)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        /**
         * v3 → v4: create learning_state (composite PK goalId+bucket+subTheme,
         * goalId index) and leave every prior table untouched. Existing user
         * data (goals, check-ins, exposures) is preserved (gatekeeper M1).
         */
        /**
         * Public so test builders (inMemoryDatabaseBuilder paths) can register
         * the same migration the production singleton uses.
         */
        val LEARNING_STATE_MIGRATION =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `learning_state` " +
                            "(`goalId` INTEGER NOT NULL, `bucket` INTEGER NOT NULL, " +
                            "`subTheme` TEXT NOT NULL, `attempts` INTEGER NOT NULL DEFAULT 0, " +
                            "`scoreSum` REAL NOT NULL DEFAULT 0.0, `scoreCount` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`goalId`, `bucket`, `subTheme`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_learning_state_goalId` ON `learning_state` (`goalId`)",
                    )
                }
            }
    }
}
