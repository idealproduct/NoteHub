package com.nqutaoitfteikc.notehub.data
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.nqutaoitfteikc.notehub.domain.*
import kotlinx.coroutines.flow.map
class LocalSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontKey = floatPreferencesKey("font_scale")
    val fontScale = store.data.map { FontSizeRules.safe(it[fontKey] ?: 1f) }
    suspend fun setFontScale(scale: Float) {
        require(scale.isFinite() && scale in 0.8f..1.5f) { "字型大小須介於 80% 至 150%" }
        store.edit { it[fontKey] = scale }
    }
    override val theme = store.data.map { values -> ThemeMode.entries.firstOrNull { it.name == values[themeKey] } ?: ThemeMode.SYSTEM }
    override suspend fun setTheme(mode: ThemeMode) { store.edit { it[themeKey] = mode.name } }
}
