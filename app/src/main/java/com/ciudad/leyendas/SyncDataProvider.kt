package com.ciudad.leyendas

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SyncDataProvider : ContentProvider() {

    private lateinit var syncDataStore: SyncDataStore

    override fun onCreate(): Boolean {
        syncDataStore = SyncDataStore.getInstance(context as Context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("total_steps", "recent_steps"))
        runBlocking {
            val totalSteps = syncDataStore.totalSteps.first() ?: 0
            val recentSteps = syncDataStore.recentSteps.first() ?: 0
            cursor.addRow(arrayOf(totalSteps, recentSteps))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }
}