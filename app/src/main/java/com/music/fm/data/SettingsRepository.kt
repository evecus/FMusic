package com.music.fm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.music.fm.data.model.Qualitys
import com.music.fm.data.model.Sources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
    val displayMode: DisplayMode = DisplayMode.AUTO,
    val writeTags: Boolean = true,
    val writeCover: Boolean = true,
    val writeEmbeddedLyric: Boolean = true,
    /** 下载默认音质（下载弹窗预选） */
    val downloadQuality: String = Qualitys.Q128,
    /** 下载歌曲时同时保存 .lrc 歌词文件（对齐 lx download.writeLyric，默认关） */
    val downloadLyric: Boolean = false,
    /** 歌词中附带罗马音（对齐 lx download.writeRomaLyric，默认关） */
    val writeRomaLyric: Boolean = false,
    /** 下载文件命名格式（对齐 lx download.fileName：「歌名 - 歌手」/「歌手 - 歌名」/「歌名」） */
    val fileNameFormat: String = FileNameFormats.NAME_SINGER,
)

object FileNameFormats {
    const val NAME_SINGER = "歌名 - 歌手"
    const val SINGER_NAME = "歌手 - 歌名"
    const val NAME = "歌名"
    val all = listOf(NAME_SINGER, SINGER_NAME, NAME)

    /** 按格式生成文件名（不含扩展名与非法字符过滤）；一次性替换，避免歌名中含「歌手」二字被二次替换 */
    fun format(format: String, name: String, singer: String): String = when (format) {
        SINGER_NAME -> "$singer - $name"
        NAME -> name
        else -> "$name - $singer"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_SCRIPT_ID = stringPreferencesKey("api_script_id")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val SEARCH_SOURCES = stringSetPreferencesKey("search_sources")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val DOWNLOAD_LYRIC = booleanPreferencesKey("download_lyric")
        val WRITE_TAGS = booleanPreferencesKey("write_tags")
        val WRITE_COVER = booleanPreferencesKey("write_cover")
        val WRITE_EMBEDDED_LYRIC = booleanPreferencesKey("write_embedded_lyric")
        val WRITE_ROMA_LYRIC = booleanPreferencesKey("write_roma_lyric")
        val FILE_NAME_FORMAT = stringPreferencesKey("download_file_name_format")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiScriptId = prefs[Keys.API_SCRIPT_ID] ?: "",
            defaultQuality = prefs[Keys.DEFAULT_QUALITY] ?: Qualitys.Q128,
            searchSources = prefs[Keys.SEARCH_SOURCES] ?: AppSettings().searchSources,
            displayMode = DisplayMode.fromKey(prefs[Keys.DISPLAY_MODE]),
            downloadQuality = prefs[Keys.DOWNLOAD_QUALITY] ?: Qualitys.Q128,
            downloadLyric = prefs[Keys.DOWNLOAD_LYRIC] ?: false,
            writeTags = prefs[Keys.WRITE_TAGS] ?: true,
            writeCover = prefs[Keys.WRITE_COVER] ?: true,
            writeEmbeddedLyric = prefs[Keys.WRITE_EMBEDDED_LYRIC] ?: true,
            writeRomaLyric = prefs[Keys.WRITE_ROMA_LYRIC] ?: false,
            fileNameFormat = prefs[Keys.FILE_NAME_FORMAT]?.takeIf { it in FileNameFormats.all }
                ?: FileNameFormats.NAME_SINGER,
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

    suspend fun setFileNameFormat(format: String) {
        context.dataStore.edit { it[Keys.FILE_NAME_FORMAT] = format }
    }

    suspend fun setWriteRomaLyric(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRITE_ROMA_LYRIC] = enabled }
    }

    suspend fun setWriteEmbeddedLyric(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRITE_EMBEDDED_LYRIC] = enabled }
    }
}
