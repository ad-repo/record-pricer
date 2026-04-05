package com.recordpricer.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val discogsId: Int,
    val title: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val queryString: String,
    val photoPath: String?,
    val topTitle: String?,
    val topDiscogsId: Int?,
    val topUri: String?,
    val resultsJson: String,  // JSON array of full DiscogsRelease objects
    val savedAt: Long = System.currentTimeMillis()
)
