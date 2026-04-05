package com.recordpricer.db

import androidx.room.*

@Dao
interface FavoriteDao {
    @Query("SELECT discogsId FROM favorites")
    suspend fun getAllIds(): List<Int>

    @Query("SELECT COUNT(*) FROM favorites WHERE discogsId = :id")
    suspend fun isFavorite(id: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE discogsId = :id")
    suspend fun deleteById(id: Int)
}
