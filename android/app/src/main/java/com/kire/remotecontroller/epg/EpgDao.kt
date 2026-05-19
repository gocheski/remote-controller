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
    suspend fun clearProgrammes()

    @Query("DELETE FROM programmes")
    suspend fun clear()

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Query("DELETE FROM programme_tags")
    suspend fun clearProgrammeTags()

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

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    suspend fun allTags(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT id FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTagId(name: String): Long?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun tagById(id: Long): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkTag(link: ProgrammeTagEntity)

    @Query("DELETE FROM programme_tags WHERE stableKey = :stableKey AND tagId = :tagId")
    suspend fun unlinkTag(stableKey: String, tagId: Long)

    @Query(
        """
        SELECT t.name FROM tags t
        INNER JOIN programme_tags pt ON pt.tagId = t.id
        WHERE pt.stableKey = :stableKey
        ORDER BY t.name
        """,
    )
    suspend fun tagNamesForStableKey(stableKey: String): List<String>

    @Query(
        """
        SELECT pt.stableKey FROM programme_tags pt
        WHERE pt.tagId = :tagId
        """,
    )
    suspend fun stableKeysForTag(tagId: Long): List<String>

    @Query("SELECT stableKey, tagId FROM programme_tags")
    suspend fun allProgrammeTagLinks(): List<ProgrammeTagEntity>
}

data class ChannelRow(val channelId: String, val channelName: String)
