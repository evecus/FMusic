package cn.toside.music.compose.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.toside.music.compose.data.model.Qualitys
import cn.toside.music.compose.data.model.Sources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val apiScriptId: String = "",
    val defaultQuality: String = Qualitys.Q128,
    val searchSources: Set<String> = setOf(Sources.KW, Sources.KG, Sources.TX, Sources.WY, Sources.MG),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_SCRIPT_ID = stringPreferencesKey("api_script_id")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SEARCH_SOURCES = stringSetPreferencesKey("search_sources")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiScriptId = prefs[Keys.API_SCRIPT_ID] ?: "",
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: Qualitys.Q128,
            searchSources = prefs[Keys.SEARCH_SOURCES] ?: AppSettings().searchSources,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
        )
    }

    suspend fun setApiScriptId(id: String) {
        context.dataStore.edit { it[Keys.API_SCRIPT_ID] = id }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality }
    }

    suspend fun setSearchSources(sources: Set<String>) {
        context.dataStore.edit { it[Keys.SEARCH_SOURCES] = sources }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
