package org.tomcurran.welfare.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weight",
    indices = [Index(value = ["time"])],
)
data class WeightEntity(
    @PrimaryKey val healthConnectId: String,
    val weight: Double,
    val time: Long,
)
