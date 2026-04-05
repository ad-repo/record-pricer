package com.recordpricer.db

import androidx.room.*

@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY savedAt DESC")
    suspend fun getAll(): List<SavedSearchEntity>

    @Query("SELECT * FROM saved_searches WHERE id = :id")
    suspend fun getById(id: Int): SavedSearchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(search: SavedSearchEntity): Long

    @Query("DELETE FROM saved_searches WHERE id = :id")
    suspend fun deleteById(id: Int)
}
