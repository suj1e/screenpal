package com.suj1e.screenpal

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.suj1e.screenpal.util.SettingsRepository

val Application.dataStore by preferencesDataStore(name = "settings")

class ScreenPalApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }
}
