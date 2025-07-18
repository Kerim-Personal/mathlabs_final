package com.codenzi.mathlabs.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DrawingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(path: DrawingPath)

    @Query("SELECT * FROM drawing_paths WHERE pdfAssetName = :pdfName AND pageIndex = :pageIndex")
    suspend fun getPathsForPage(pdfName: String, pageIndex: Int): List<DrawingPath>

    @Query("DELETE FROM drawing_paths WHERE pdfAssetName = :pdfName AND pageIndex = :pageIndex")
    suspend fun clearPage(pdfName: String, pageIndex: Int)
}