package com.example.taskmanagerapp.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = enumValueOf<SyncStatus>(value)
}