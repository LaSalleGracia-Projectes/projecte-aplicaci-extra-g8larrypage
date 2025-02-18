package com.ciudad.leyendas

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncDataStore(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = "sync_prefs")

    companion object {
        val LAST_SYNC_TIME_KEY = longPreferencesKey("last_sync_time")
    }

    val lastSyncTime: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_SYNC_TIME_KEY]
        }

    suspend fun saveLastSyncTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME_KEY] = time
        }
    }
}