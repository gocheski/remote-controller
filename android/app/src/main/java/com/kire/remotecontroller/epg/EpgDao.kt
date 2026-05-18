package com.kire.remotecontroller.epg

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int

    @Query(
        """
        SELECT * FROM programmes
        WHERE startMillis <= :until AND endMillis >= :since
        ORDER BY channelName, startMillis
        LIMIT :limit
        """,
    )
    suspend fun programmesBetween(since: Long, until: Long, limit: Int): List<ProgrammeEntity>

    @Query("SELECT DISTINCT channelId, channelName FROM programmes ORDER BY channelName")
    suspend fun channels(): List<ChannelRow>
}

data class ChannelRow(val channelId: String, val channelName: String)
