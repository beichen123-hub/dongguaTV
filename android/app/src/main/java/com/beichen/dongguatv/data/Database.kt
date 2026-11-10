package com.beichen.dongguatv.data

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.Database
import androidx.room.RoomDatabase

@Entity(tableName = "favorites", indices = [Index(value = ["vodId", "siteKey"], unique = true)])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vodId: String,
    val siteKey: String,
    val vodName: String,
    val vodPic: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history", indices = [Index(value = ["vodId", "siteKey"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vodId: String,
    val siteKey: String,
    val vodName: String,
    val vodPic: String,
    val episodeIndex: Int = 0,
    /** 该集内已播放到的毫秒位置，用于真正意义上的「继续播放」 */
    val positionMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "follows", indices = [Index(value = ["vodId", "siteKey"], unique = true)])
data class FollowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vodId: String,
    val siteKey: String,
    val vodName: String,
    val vodPic: String,
    val lastEpisode: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): LiveData<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE vodId = :vodId AND siteKey = :siteKey LIMIT 1")
    suspend fun find(vodId: String, siteKey: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE vodId = :vodId AND siteKey = :siteKey")
    suspend fun delete(vodId: String, siteKey: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC")
    fun observeAll(): LiveData<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE vodId = :vodId AND siteKey = :siteKey LIMIT 1")
    suspend fun find(vodId: String, siteKey: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM follows ORDER BY updatedAt DESC")
    fun observeAll(): LiveData<List<FollowEntity>>

    @Query("SELECT * FROM follows")
    suspend fun getAll(): List<FollowEntity>

    @Query("SELECT * FROM follows WHERE vodId = :vodId AND siteKey = :siteKey LIMIT 1")
    suspend fun find(vodId: String, siteKey: String): FollowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FollowEntity)

    @Query("UPDATE follows SET lastEpisode = :ep, updatedAt = :ts WHERE id = :id")
    suspend fun updateEpisode(id: Long, ep: Int, ts: Long)

    @Query("DELETE FROM follows WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [FavoriteEntity::class, HistoryEntity::class, FollowEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun followDao(): FollowDao
}
