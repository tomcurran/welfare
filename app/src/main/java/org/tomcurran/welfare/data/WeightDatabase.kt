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

        @Volatile
        private var INSTANCE: WeightDatabase? = null

        fun getInstance(context: Context): WeightDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): WeightDatabase {
            val appContext = context.applicationContext
            return Room.databaseBuilder(
                appContext,
                WeightDatabase::class.java,
                DATABASE_NAME,
            ).fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        runBlocking {
                            WeightRepository.getInstance(appContext).resetSync()
                        }
                    }
                })
                .build()
        }
    }
}
