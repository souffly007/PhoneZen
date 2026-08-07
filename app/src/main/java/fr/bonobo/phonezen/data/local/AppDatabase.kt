// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.bonobo.phonezen.data.dao.BlockedNumberDao
import fr.bonobo.phonezen.data.dao.HealthcareWhitelistDao
import fr.bonobo.phonezen.data.model.BlockedNumber
import fr.bonobo.phonezen.data.model.HealthcareEntry

@Database(
    entities     = [BlockedCall::class, CallNote::class, BlockedNumber::class, HealthcareEntry::class],
    version      = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao
    abstract fun callNoteDao(): CallNoteDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun healthcareWhitelistDao(): HealthcareWhitelistDao  // ✅ nouveau

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS call_notes (
                        number    TEXT NOT NULL PRIMARY KEY,
                        note      TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_blocked_calls_number ON blocked_calls(number)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_blocked_calls_timestamp ON blocked_calls(timestamp)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS blocked_numbers (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        number    TEXT NOT NULL,
                        label     TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL,
                        UNIQUE(number)
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_numbers_number ON blocked_numbers(number)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS healthcare_whitelist (
                        id        TEXT NOT NULL PRIMARY KEY,
                        number    TEXT,
                        prefix    TEXT,
                        name      TEXT NOT NULL,
                        type      TEXT NOT NULL DEFAULT 'hospital',
                        region    TEXT NOT NULL DEFAULT 'France',
                        verified  INTEGER NOT NULL DEFAULT 1,
                        source    TEXT NOT NULL DEFAULT 'official',
                        syncedAt  INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_healthcare_number  ON healthcare_whitelist(number)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_healthcare_prefix  ON healthcare_whitelist(prefix)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_healthcare_verified ON healthcare_whitelist(verified)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phonezen_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}