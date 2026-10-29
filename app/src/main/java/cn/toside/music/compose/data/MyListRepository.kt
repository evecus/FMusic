package cn.toside.music.compose.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.toside.music.compose.data.model.MusicInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private val Context.myListStore: DataStore<Preferences> by preferencesDataStore(name = "mylist")

/**
 * 歌单元数据。内置两个列表（对齐 mobile 版）：试听列表 / 我的收藏，
 * 其余为用户自建（可重命名、删除）。
 */
data class MyListMeta(
    val id: String,
    val name: String,
    val builtin: Boolean,
)

/**
 * “收藏”页数据仓库：DataStore 持久化歌单元数据与各歌单歌曲
 * （歌曲存 [MusicInfo.toPersistentJson] 数组，保证原样还原后仍可取 URL / 歌词 / 封面）。
 */
class MyListRepository(private val context: Context) {

    companion object {
        const val DEFAULT_ID = "default"
        const val LOVE_ID = "love"

        private const val DEFAULT_NAME = "试听列表"
        private const val LOVE_NAME = "我的收藏"
        private const val MAX_LISTS = 50
        private const val MAX_SONGS_PER_LIST = 2000

        private val json = Json { ignoreUnknownKeys = true }
    }

    private object Keys {
        val META = stringPreferencesKey("lists_meta")

        fun songs(listId: String) = stringPreferencesKey("songs_$listId")
    }

    private val mutex = Mutex()

    // ── 读取 ─────────────────────────────────────────────────────

    /** 全部歌单（内置在前，自建按创建顺序） */
    val lists: Flow<List<MyListMeta>> = context.myListStore.data.map { prefs ->
        builtinLists() + parseUserMetas(prefs[Keys.META])
    }

    /** 某歌单内的歌曲（按加入顺序） */
    fun songsOf(listId: String): Flow<List<MusicInfo>> = context.myListStore.data.map { prefs ->
        parseSongs(prefs[Keys.songs(listId)])
    }

    // ── 歌单管理 ─────────────────────────────────────────────────

    suspend fun createList(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val user = parseUserMetas(prefs[Keys.META]).toMutableList()
                if (user.size >= MAX_LISTS) return@edit
                user += MyListMeta(
                    id = "u_${System.currentTimeMillis()}",
                    name = trimmed,
                    builtin = false,
                )
                prefs[Keys.META] = JsonArray(user.map { metaToJson(it) }).toString()
            }
        }
    }

    suspend fun renameList(listId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || listId.startsWith("u_").not()) return
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val user = parseUserMetas(prefs[Keys.META]).toMutableList()
                val idx = user.indexOfFirst { it.id == listId }
                if (idx >= 0) {
                    user[idx] = user[idx].copy(name = trimmed)
                    prefs[Keys.META] = JsonArray(user.map { metaToJson(it) }).toString()
                }
            }
        }
    }

    suspend fun removeList(listId: String) {
        if (!listId.startsWith("u_")) return
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val user = parseUserMetas(prefs[Keys.META]).filterNot { it.id == listId }
                prefs[Keys.META] = JsonArray(user.map { metaToJson(it) }).toString()
                prefs.remove(Keys.songs(listId))
            }
        }
    }

    // ── 歌曲操作 ─────────────────────────────────────────────────

    /** 添加歌曲（按 uniqueKey 去重） */
    suspend fun addSongs(listId: String, songs: List<MusicInfo>) {
        if (songs.isEmpty()) return
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val existing = parseSongs(prefs[Keys.songs(listId)]).toMutableList()
                val keys = existing.mapTo(HashSet()) { it.uniqueKey }
                var added = false
                for (song in songs) {
                    if (song.uniqueKey in keys) continue
                    if (existing.size >= MAX_SONGS_PER_LIST) break
                    existing += song
                    keys += song.uniqueKey
                    added = true
                }
                if (added) {
                    prefs[Keys.songs(listId)] = songsToJson(existing)
                }
            }
        }
    }

    /** 移除单首（按 uniqueKey 匹配） */
    suspend fun removeSong(listId: String, song: MusicInfo) {
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val existing = parseSongs(prefs[Keys.songs(listId)])
                    .filterNot { it.uniqueKey == song.uniqueKey }
                prefs[Keys.songs(listId)] = songsToJson(existing)
            }
        }
    }

    /** 清空歌单 */
    suspend fun clearList(listId: String) {
        mutex.withLock {
            context.myListStore.edit { prefs ->
                prefs[Keys.songs(listId)] = "[]"
            }
        }
    }

    /** 上移 / 下移歌曲（delta: -1 上移，+1 下移） */
    suspend fun moveSong(listId: String, index: Int, delta: Int) {
        if (delta == 0) return
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val existing = parseSongs(prefs[Keys.songs(listId)]).toMutableList()
                val target = index + delta
                if (index !in existing.indices || target !in existing.indices) return@edit
                val song = existing.removeAt(index)
                existing.add(target, song)
                prefs[Keys.songs(listId)] = songsToJson(existing)
            }
        }
    }

    // ── 序列化 ───────────────────────────────────────────────────

    private fun builtinLists() = listOf(
        MyListMeta(DEFAULT_ID, DEFAULT_NAME, builtin = true),
        MyListMeta(LOVE_ID, LOVE_NAME, builtin = true),
    )

    private fun parseUserMetas(raw: String?): List<MyListMeta> {
        if (raw.isNullOrEmpty()) return emptyList()
        val arr = runCatching { json.parseToJsonElement(raw) as JsonArray }.getOrNull()
            ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            MyListMeta(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id,
                builtin = false,
            )
        }
    }

    private fun metaToJson(meta: MyListMeta) = JsonObject(
        mapOf(
            "id" to JsonPrimitive(meta.id),
            "name" to JsonPrimitive(meta.name),
        ),
    )

    private fun parseSongs(raw: String?): List<MusicInfo> {
        if (raw.isNullOrEmpty()) return emptyList()
        val arr = runCatching { json.parseToJsonElement(raw) as JsonArray }.getOrNull()
            ?: return emptyList()
        return arr.mapNotNull { el ->
            (el as? JsonObject)?.let { MusicInfo.parse(it) }
        }
    }

    private fun songsToJson(songs: List<MusicInfo>): String =
        JsonArray(songs.map { it.toPersistentJson() }).toString()
}
