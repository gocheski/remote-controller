package com.kire.remotecontroller.epg

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProgrammeEntity::class, TagEntity::class, ProgrammeTagEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun epgDao(): EpgDao

    companion object {
        @Volatile private var instance: EpgDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programmes ADD COLUMN categories TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE programmes ADD COLUMN stableKey TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS programme_tags (
                        stableKey TEXT NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY(stableKey, tagId)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): EpgDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EpgDatabase::class.java,
                    "epg.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }

        fun resetInstance() {
            instance = null
        }
    }
}
