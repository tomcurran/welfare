package org.tomcurran.welfare.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.runBlocking

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
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    runBlocking { repository.get().resetSync() }
                }
            }).build()
    }
}
