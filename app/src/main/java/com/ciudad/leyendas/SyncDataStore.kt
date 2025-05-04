package com.ciudad.leyendas

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncDataStore private constructor(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "sync_prefs")

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: SyncDataStore? = null

        fun getInstance(context: Context): SyncDataStore {
            return INSTANCE ?: synchronized(this) {
                val instance = SyncDataStore(context)
                INSTANCE = instance
                instance
            }
        }

        val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
        val TOTAL_STEPS_KEY = longPreferencesKey("total_steps")
        val RECENT_STEPS_KEY = longPreferencesKey("recent_steps")
        val SALT_KEY = stringPreferencesKey("salt")
        val LAST_SYNC_VALUE_KEY = longPreferencesKey("last_sync_value")
        val DAILY_STEPS_MAP_KEY = stringPreferencesKey("daily_steps_map")
    }

    val lastSyncTime: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_SYNC_TIME_KEY]
        }

    val totalSteps: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[TOTAL_STEPS_KEY]
        }

    val recentSteps: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[RECENT_STEPS_KEY]
        }

    val salt: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[SALT_KEY]
        }

    val lastSyncValue: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_SYNC_VALUE_KEY]
        }

    val dailyStepsMap: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[DAILY_STEPS_MAP_KEY]
        }

    suspend fun saveLastSyncTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = time
        }
    }

    suspend fun saveTotalSteps(steps: Long) {
        context.dataStore.edit { preferences ->
            preferences[TOTAL_STEPS_KEY] = steps
        }
    }

    suspend fun saveRecentSteps(nuevosPasos: Long) {
        context.dataStore.edit { preferences ->
            preferences[RECENT_STEPS_KEY] = nuevosPasos
        }
    }

    suspend fun saveSalt(salt: String) {
        context.dataStore.edit { preferences ->
            preferences[SALT_KEY] = salt
        }
    }

    suspend fun saveLastSyncValue(steps: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_VALUE_KEY] = steps
        }
    }

    suspend fun saveDailyStepsMap(dailyStepsJson: String) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_STEPS_MAP_KEY] = dailyStepsJson
        }
    }
}