package com.developer_rahul.docunova.RoomDB

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecentFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: RecentFile)

    @Query("SELECT * FROM recent_files ORDER BY id DESC")
    fun getAllFiles(): LiveData<List<RecentFile>>

    @Delete
    suspend fun delete(file: RecentFile)
}
