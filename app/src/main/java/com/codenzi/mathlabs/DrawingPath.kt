package com.codenzi.mathlabs.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawing_paths")
data class DrawingPath(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfAssetName: String,
    val pageIndex: Int,
    val color: Int,
    val strokeWidth: Float,
    val isEraser: Boolean,
    // Path'i serileştirilmiş bir string olarak saklayacağız.
    // Örnek: "M100,200 L200,300 Q300,400,350,450"
    val serializedPath: String
)