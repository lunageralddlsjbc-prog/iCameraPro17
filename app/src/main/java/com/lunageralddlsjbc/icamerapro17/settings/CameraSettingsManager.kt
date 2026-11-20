package com.lunageralddlsjbc.icamerapro17.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "camera_settings")

object CameraSettingsManager {
    private val GRID_KEY = booleanPreferencesKey("grid_enabled")
    private val LEVEL_KEY = booleanPreferencesKey("level_enabled")
    private val MIRROR_KEY = booleanPreferencesKey("mirror_front_camera")
    private val LENS_CORRECTION_KEY = booleanPreferencesKey("lens_correction")
    private val SAVE_TO_LIBRARY_KEY = booleanPreferencesKey("save_to_library")
    
    fun getGridFlow(context: Context): Flow<Boolean> = 
        context.dataStore.data.map { prefs -> prefs[GRID_KEY] ?: false }
        
    fun getLevelFlow(context: Context): Flow<Boolean> = 
        context.dataStore.data.map { prefs -> prefs[LEVEL_KEY] ?: true }
        
    fun getMirrorFlow(context: Context): Flow<Boolean> = 
        context.dataStore.data.map { prefs -> prefs[MIRROR_KEY] ?: false }
        
    fun getLensCorrectionFlow(context: Context): Flow<Boolean> = 
        context.dataStore.data.map { prefs -> prefs[LENS_CORRECTION_KEY] ?: true }
        
    fun getSaveToLibraryFlow(context: Context): Flow<Boolean> = 
        context.dataStore.data.map { prefs -> prefs[SAVE_TO_LIBRARY_KEY] ?: true }
    
    suspend fun toggleGrid(context: Context) {
        context.dataStore.edit { it[GRID_KEY] = !(it[GRID_KEY] ?: false) }
    }
    
    suspend fun toggleLevel(context: Context) {
        context.dataStore.edit { it[LEVEL_KEY] = !(it[LEVEL_KEY] ?: true) }
    }
    
    suspend fun toggleMirror(context: Context) {
        context.dataStore.edit { it[MIRROR_KEY] = !(it[MIRROR_KEY] ?: false) }
    }
    
    suspend fun toggleLensCorrection(context: Context) {
        context.dataStore.edit { it[LENS_CORRECTION_KEY] = !(it[LENS_CORRECTION_KEY] ?: true) }
    }
    
    suspend fun toggleSaveToLibrary(context: Context) {
        context.dataStore.edit { it[SAVE_TO_LIBRARY_KEY] = !(it[SAVE_TO_LIBRARY_KEY] ?: true) }
    }
}
