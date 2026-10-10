/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Retarget — turning Advertising's own toolbox toward your goals.
 * Copyright (C) 2026 Jim Spurgeon. For license text see LICENSE.
 */

package com.retarget.goal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retarget.learning.LearningStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Gatekeeper M1 regression pin (2026-10-10): the v3→v4 migration must be
 * additive — existing rows survive, learning_state starts empty.
 *
 * Room's exported-schema JSONs were never checked in (exportSchemas was
 * disabled), so MigrationTestHelper can't load a v3 schema. Instead this test
 * hand-builds a v3-shaped SQLite file (DDL mirroring the entities at tag
 * v0.3.3, the last version=3 release), stamps user_version=3, and lets the
 * production migration bring it to 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningStateMigrationTest {
    @Test
    fun `migrate 3 to 4 preserves existing rows and creates empty learning_state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile: File = context.getDatabasePath("migrate-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            // v3 DDL, mirroring entities at tag v0.3.3.
            raw.execSQL(
                "CREATE TABLE `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`presetId` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, " +
                    "`settingsJson` TEXT NOT NULL)",
            )
            raw.execSQL(
                "INSERT INTO goals (presetId, displayName, createdAt, active, settingsJson) " +
                    "VALUES ('hydration', 'Hydration', 0, 1, '{}')",
            )
            raw.execSQL(
                "CREATE TABLE `exposures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`creativeId` TEXT NOT NULL, `subTheme` TEXT NOT NULL, " +
                    "`channel` INTEGER NOT NULL, `atMs` INTEGER NOT NULL)",
            )
            raw.execSQL(
                "INSERT INTO exposures (creativeId, subTheme, channel, atMs) " +
                    "VALUES ('c1', 'focus', 1, 42)",
            )
            raw.execSQL(
                "CREATE TABLE `check_ins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`goalId` INTEGER NOT NULL, `atMs` INTEGER NOT NULL, `notes` TEXT)",
            )
            raw.execSQL(
                "CREATE TABLE `creative_packs` (`id` TEXT NOT NULL, `goalTheme` TEXT NOT NULL, " +
                    "`ingestTimestamp` INTEGER NOT NULL, `imageCount` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            raw.execSQL(
                "CREATE TABLE `creatives` (`id` TEXT NOT NULL, `packId` TEXT NOT NULL, " +
                    "`subTheme` TEXT NOT NULL, `imagePath` TEXT NOT NULL, " +
                    "`attribution` TEXT, `licenseUrl` TEXT NOT NULL, `sha256` TEXT NOT NULL, " +
                    "`baseAppeal` REAL NOT NULL, `copyPoolJson` TEXT NOT NULL, " +
                    "`unsplashId` TEXT, `photographer` TEXT, `photographerUrl` TEXT, " +
                    "`sourceUrl` TEXT, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`packId`) REFERENCES `creative_packs`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_creatives_packId` ON `creatives` (`packId`)")
            raw.execSQL("CREATE INDEX IF NOT EXISTS `index_creatives_subTheme` ON `creatives` (`subTheme`)")
            raw.version = 3
        }

        val db =
            Room
                .databaseBuilder(context, GoalDatabase::class.java, dbFile.name)
                .addMigrations(GoalDatabase.LEARNING_STATE_MIGRATION)
                .allowMainThreadQueries()
                .build()
        try {
            // Force open; Room applies the 3->4 migration on first access.
            db.query("SELECT COUNT(*) FROM goals", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0)) // goal row survived
            }
            db.query("SELECT COUNT(*) FROM exposures", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0)) // exposure row survived
            }
            db.query("SELECT COUNT(*) FROM learning_state", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0)) // learning starts empty
            }
            // DAO works on the migrated schema.
            db.learningStateDao().upsert(
                LearningStateEntity(goalId = 1, bucket = 2, subTheme = "focus", attempts = 3, scoreSum = 1.5, scoreCount = 2),
            )
            assertEquals(3, db.learningStateDao().get(1, 2, "focus")?.attempts)
            // And Room agrees the DB is now at version 4.
            db.query("PRAGMA user_version", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(4, c.getInt(0))
            }
        } finally {
            db.close()
            dbFile.delete()
        }
    }
}
