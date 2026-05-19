package com.kire.remotecontroller.epg

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "programmes")
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String = "",
    val categories: String = "",
    val stableKey: String = "",
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "programme_tags", primaryKeys = ["stableKey", "tagId"])
data class ProgrammeTagEntity(
    val stableKey: String,
    val tagId: Long,
)

data class ProgrammeSlot(
    val programmeId: Long,
    val stableKey: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val categories: String,
    val description: String,
    val tagNames: List<String>,
)

data class ChannelGuideRow(
    val channelId: String,
    val channelNumber: Int?,
    val channelName: String,
    val slots: List<ProgrammeSlot>,
)

enum class GenreFilter(val label: String) {
    ALL("All"),
    SPORT("Sport"),
    MUSIC("Music"),
    MOVIES("Movies"),
    GENERAL("General"),
}
