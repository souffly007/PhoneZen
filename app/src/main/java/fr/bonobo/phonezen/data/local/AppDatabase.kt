package fr.bonobo.phonezen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.bonobo.phonezen.data.dao.BlockedNumberDao       // ✅ package data.dao
import fr.bonobo.phonezen.data.model.BlockedNumber        // ✅ package data.model

@Database(
    entities     = [BlockedCall::class, CallNote::class, BlockedNumber::class],
    version      = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao
    abstract fun callNoteDao(): CallNoteDao
    abstract fun blockedNumberDao(): BlockedNumberDao      // ✅

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "phonezen_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}