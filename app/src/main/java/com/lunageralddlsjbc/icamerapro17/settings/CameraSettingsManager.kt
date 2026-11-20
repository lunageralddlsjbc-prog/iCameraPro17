package com.lunageralddlsjbc.icamerapro17.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore(name = "icamera_settings")

object CameraSettingsManager {
    private val GRID = booleanPreferencesKey("grid")
    private val LEVEL = booleanPreferencesKey("level")
    private val MIRROR = booleanPreferencesKey("mirror")
    private val LENS_CORRECTION = booleanPreferencesKey("lens_correction")
    
    fun getGridFlow(ctx: Context): Flow<Boolean> = ctx.cameraDataStore.data.map { it[GRID] ?: false }
    fun getLevelFlow(ctx: Context): Flow<Boolean> = ctx.cameraDataStore.data.map { it[LEVEL] ?: true }
    fun getMirrorFlow(ctx: Context): Flow<Boolean> = ctx.cameraDataStore.data.map { it[MIRROR] ?: false }
    fun getLensCorrectionFlow(ctx: Context): Flow<Boolean> = ctx.cameraDataStore.data.map { it[LENS_CORRECTION] ?: true }
    
    suspend fun toggleGrid(ctx: Context) { ctx.cameraDataStore.edit { it[GRID] = !(it[GRID] ?: false) } }
    suspend fun toggleLevel(ctx: Context) { ctx.cameraDataStore.edit { it[LEVEL] = !(it[LEVEL] ?: true) } }
    suspend fun toggleMirror(ctx: Context) { ctx.cameraDataStore.edit { it[MIRROR] = !(it[MIRROR] ?: false) } }
    suspend fun toggleLensCorrection(ctx: Context) { ctx.cameraDataStore.edit { it[LENS_CORRECTION] = !(it[LENS_CORRECTION] ?: true) } }
}
