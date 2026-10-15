package com.nqutaoitfteikc.notehub.data
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.map
class LocalSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val themeKey = stringPreferencesKey("theme_mode")
    override val theme = store.data.map { values -> ThemeMode.entries.firstOrNull { it.name == values[themeKey] } ?: ThemeMode.SYSTEM }
    override suspend fun setTheme(mode: ThemeMode) { store.edit { it[themeKey] = mode.name } }
}
