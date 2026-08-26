package com.suj1e.screenpal

import android.app.Application
import android.media.projection.MediaProjection
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.suj1e.screenpal.util.SettingsRepository

val Application.dataStore by preferencesDataStore(name = "settings")

class ScreenPalApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    var mediaProjection: MediaProjection? = null
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }

    fun setMediaProjection(projection: MediaProjection) {
        mediaProjection = projection
    }

    fun hasValidMediaProjection(): Boolean {
        return mediaProjection != null
    }

    fun releaseMediaProjection() {
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaProjection = null
        }
    }
}
