package org.tomcurran.welfare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.health.connect.client.HealthConnectClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "welfare_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideWeightDatabase(
        @ApplicationContext context: Context,
        repository: dagger.Lazy<WeightRepository>,
    ): WeightDatabase = WeightDatabase.provideWeightDatabase(context, repository)

    @Provides
    fun provideWeightDao(database: WeightDatabase): WeightDao = database.weightDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient? =
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Health Connect not available: ${e.message}")
            null
        }

    private val TAG: String = DataModule::class.java.simpleName
}
