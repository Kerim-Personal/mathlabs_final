package com.codenzi.mathlabs.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DrawingPath::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drawingDao(): DrawingDao
}