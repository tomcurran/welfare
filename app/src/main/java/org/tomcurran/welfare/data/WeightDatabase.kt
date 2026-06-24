package org.tomcurran.welfare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(entities = [WeightEntity::class], version = 2, exportSchema = false)
abstract class WeightDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao

    companion object {
        private const val DATABASE_NAME = "weight.db"

        fun provideWeightDatabase(
            context: Context,
            repository: dagger.Lazy<WeightRepository>,
        ): WeightDatabase = Room.databaseBuilder(context, WeightDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(object : Callback() {
                private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    scope.launch { repository.get().resetSync() }
                }
            }).build()
    }
}
