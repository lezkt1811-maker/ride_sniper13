package com.ridesniper.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Update
    suspend fun update(ride: RideEntity)

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<RideEntity>>

    @Query("SELECT * FROM ride_history WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    fun observeSince(sinceMillis: Long): Flow<List<RideEntity>>

    @Query("SELECT * FROM ride_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RideEntity?

    @Query("UPDATE ride_history SET acceptedState = :state WHERE id = :id")
    suspend fun setAcceptedState(id: Long, state: String)

    @Query("SELECT * FROM ride_history WHERE timestamp >= :sinceMillis")
    suspend fun getSince(sinceMillis: Long): List<RideEntity>

    @Query("SELECT * FROM ride_history")
    suspend fun getAll(): List<RideEntity>

    @Query("DELETE FROM ride_history WHERE id = :id")
    suspend fun delete(id: Long)
}
