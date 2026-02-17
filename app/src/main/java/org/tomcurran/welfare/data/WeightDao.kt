package org.tomcurran.welfare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY time DESC")
    fun getAllByTimeDesc(): Flow<List<WeightEntity>>

    @Upsert
    suspend fun upsert(entity: WeightEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WeightEntity>)

    @Query("DELETE FROM weight WHERE healthConnectId = :id")
    suspend fun deleteByHealthConnectId(id: String)
}
