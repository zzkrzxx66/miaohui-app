package com.miaohui.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_records")
data class ImageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val imageFilePath: String,
    val size: String,
    val quality: String,
    val model: String,
    val createdAt: Long,
    val parentId: Long? = null,
    val type: String = "generate" // "generate" or "edit"
)
