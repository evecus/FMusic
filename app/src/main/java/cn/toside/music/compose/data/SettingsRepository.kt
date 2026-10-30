package cn.toside.music.compose.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/** 显示模式：自动按设备检测，或强制按指定形态的布局渲染 */
enum class DisplayMode(val key: String) {
    AUTO("auto"),
    PHONE("phone"),
    TABLET("tablet"),
    TV("tv");

    val label: String
        get() = when (this) {
            AUTO -> "自动"
            PHONE -> "手机端"
            TABLET -> "平板端"
            TV -> "电视端"
        }

    companion object {
        fun fromKey(key: String?): DisplayMode =
            entries.firstOrNull { it.key == key } ?: AUTO
    }
}

data class AppSettings(
    val apiScriptId: String = "",
    val defaultQuality: String = Qualitys.Q128,
    val searchSources: Set<String> = setOf(Sources.KW, Sources.KG, Sources.TX, Sources.WY, Sources.MG),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val displayMode: DisplayMode = DisplayMode.AUTO,
    val writeTags: Boolean = true,
    val writeCover: Boolean = true,
    val writeEmbeddedLyric: Boolean = true,
    /** 下载默认音质（下载弹窗预选） */
    val downloadQuality: String = Qualitys.Q128,
    /** 下载歌曲时同时保存 .lrc 歌词文件 */
    val downloadLyric: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_SCRIPT_ID = stringPreferencesKey("api_script_id")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SEARCH_SOURCES = stringSetPreferencesKey("search_sources")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val DOWNLOAD_LYRIC = booleanPreferencesKey("download_lyric")
        val WRITE_TAGS = booleanPreferencesKey("write_tags")
        val WRITE_COVER = booleanPreferencesKey("write_cover")
        val WRITE_EMBEDDED_LYRIC = booleanPreferencesKey("write_embedded_lyric")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiScriptId = prefs[Keys.API_SCRIPT_ID] ?: "",
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: Qualitys.Q128,
            searchSources = prefs[Keys.SEARCH_SOURCES] ?: AppSettings().searchSources,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            displayMode = DisplayMode.fromKey(prefs[Keys.DISPLAY_MODE]),
            downloadQuality = prefs[Keys.DOWNLOAD_QUALITY] ?: Qualitys.Q128,
            downloadLyric = prefs[Keys.DOWNLOAD_LYRIC] ?: false,
            writeTags = prefs[Keys.WRITE_TAGS] ?: true,
            writeCover = prefs[Keys.WRITE_COVER] ?: true,
            writeEmbeddedLyric = prefs[Keys.WRITE_EMBEDDED_LYRIC] ?: true,
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

    suspend fun setDisplayMode(mode: DisplayMode) {
        context.dataStore.edit { it[Keys.DISPLAY_MODE] = mode.key }
    }

    suspend fun setDownloadQuality(quality: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_QUALITY] = quality }
    }

    suspend fun setDownloadLyric(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOWNLOAD_LYRIC] = enabled }
    }

    suspend fun setWriteTags(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRITE_TAGS] = enabled }
    }

    suspend fun setWriteCover(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRITE_COVER] = enabled }
    }

    suspend fun setWriteEmbeddedLyric(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRITE_EMBEDDED_LYRIC] = enabled }
    }
}
