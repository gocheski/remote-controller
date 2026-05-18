package com.kire.remotecontroller.epg

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProgrammeEntity::class], version = 1, exportSchema = false)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun epgDao(): EpgDao

    companion object {
        @Volatile private var instance: EpgDatabase? = null

        fun get(context: Context): EpgDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EpgDatabase::class.java,
                    "epg.db",
                ).build().also { instance = it }
            }
    }
}
