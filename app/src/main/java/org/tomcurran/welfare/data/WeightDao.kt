package org.tomcurran.welfare.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WeightDao {
    @Query("SELECT * FROM weight ORDER BY time DESC")
    abstract fun getAllByTimeDesc(): Flow<List<WeightEntity>>

    @Upsert
    abstract suspend fun upsertAll(entities: List<WeightEntity>)

    @Query("DELETE FROM weight")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replaceAll(entities: List<WeightEntity>) {
        deleteAll()
        upsertAll(entities)
    }

    @Query("DELETE FROM weight WHERE healthConnectId IN (:ids)")
    abstract suspend fun deleteByHealthConnectIds(ids: List<String>)

    @Transaction
    open suspend fun applyChanges(deletionIds: List<String>, upserts: List<WeightEntity>) {
        if (deletionIds.isNotEmpty()) deleteByHealthConnectIds(deletionIds)
        if (upserts.isNotEmpty()) upsertAll(upserts)
    }
}
