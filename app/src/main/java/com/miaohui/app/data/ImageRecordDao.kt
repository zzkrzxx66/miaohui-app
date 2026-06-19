package com.miaohui.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageRecordDao {

    @Query("SELECT * FROM image_records ORDER BY createdAt DESC")
    fun getAllRecords(): Flow<List<ImageRecord>>

    @Query("SELECT * FROM image_records WHERE id = :id")
    suspend fun getRecordById(id: Long): ImageRecord?

    @Query("SELECT * FROM image_records WHERE id = :id")
    fun getRecordByIdFlow(id: Long): Flow<ImageRecord?>

    @Query("SELECT * FROM image_records WHERE type = 'generate' AND parentId IS NULL ORDER BY createdAt DESC")
    fun getRootRecords(): Flow<List<ImageRecord>>

    @Query("SELECT * FROM image_records WHERE parentId = :parentId ORDER BY createdAt ASC")
    fun getChildRecords(parentId: Long): Flow<List<ImageRecord>>

    @Insert
    suspend fun insert(record: ImageRecord): Long

    @Delete
    suspend fun delete(record: ImageRecord)

    @Query("DELETE FROM image_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
