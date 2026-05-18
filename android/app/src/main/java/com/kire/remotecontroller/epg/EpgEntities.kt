package com.kire.remotecontroller.epg

import androidx.room.Entity
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
)
