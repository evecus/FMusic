package com.music.fm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.music.fm.data.model.MusicInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val Context.myListStore: DataStore<Preferences> by preferencesDataStore(name = "mylist")

/**
 * 歌单元数据。内置一个固定列表：喜欢，其余为用户自建/收藏在线歌单
 * （可重命名、删除）。
 * [sourceListId] 非空表示该歌单由在线歌单收藏而来（mobile 版 createList 同款，
 * 形如 `kw__xxxx`），用于重复收藏识别与全量同步。
 */
data class MyListMeta(
    val id: String,
    val name: String,
    val builtin: Boolean,
    val sourceListId: String? = null,
)

/**
 * “收藏”页数据仓库：DataStore 持久化歌单元数据与各歌单歌曲
 * （歌曲存 [MusicInfo.toPersistentJson] 数组，保证原样还原后仍可取 URL / 歌词 / 封面）。
 */
class MyListRepository(private val context: Context) {

    companion object {
        /** 固定内置歌单：喜欢（唯一默认歌单） */
        const val LOVE_ID = "love"

        private const val LOVE_NAME = "喜欢"
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

    /**
     * 更新单首（按 uniqueKey 匹配后原位替换，位置不变）。
     * 用于「重新导入」补全旧歌曲缺失的 mv 字段（mobile listMvRefresh 同款）。
     */
    suspend fun updateSong(listId: String, song: MusicInfo) {
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val existing = parseSongs(prefs[Keys.songs(listId)]).toMutableList()
                val idx = existing.indexOfFirst { it.uniqueKey == song.uniqueKey }
                if (idx >= 0) {
                    existing[idx] = song
                    prefs[Keys.songs(listId)] = songsToJson(existing)
                }
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

    /**
     * 收藏在线歌单到我的列表（对齐 mobile 版 handleCollect → createList）：
     * 以 [sourceListId]（形如 `kw__xxxx`）查重——
     * 已存在则用 [songs] 全量覆盖该歌单（syncSourceList 同步语义），返回 true；
     * 不存在则新建同名歌单（与内置列表同级）并写入全部歌曲，返回 false。
     * 歌单 id 取 `u_` + sourceListId 的 MD5，保证同源歌单稳定且可重命名/删除。
     */
    suspend fun upsertCollectedList(name: String, sourceListId: String, songs: List<MusicInfo>): Boolean {
        if (songs.isEmpty()) return false
        val listId = "u_" + md5(sourceListId)
        var existed = false
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val user = parseUserMetas(prefs[Keys.META]).toMutableList()
                val idx = user.indexOfFirst { it.sourceListId == sourceListId || it.id == listId }
                if (idx >= 0) {
                    existed = true
                    // 同步：歌单名保持用户已有的（mobile syncSourceList 不改名）
                } else {
                    if (user.size >= MAX_LISTS) return@edit
                    user += MyListMeta(
                        id = listId,
                        name = name,
                        builtin = false,
                        sourceListId = sourceListId,
                    )
                    prefs[Keys.META] = JsonArray(user.map { metaToJson(it) }).toString()
                }
                val trimmed = songs.take(MAX_SONGS_PER_LIST)
                prefs[Keys.songs(listId)] = songsToJson(trimmed)
            }
        }
        return existed
    }

    // ── 备份与恢复 ───────────────────────────────────────────────

    /** 导出用快照：喜欢 + 全部自建/收藏歌单（含空歌单） */
    suspend fun snapshotAll(): List<ExportList> {
        val prefs = context.myListStore.data.first()
        val metas = builtinLists() + parseUserMetas(prefs[Keys.META])
        return metas.map { m ->
            ExportList(m.id, m.name, m.sourceListId, parseSongs(prefs[Keys.songs(m.id)]))
        }
    }

    data class ImportReport(
        val lists: Int,
        val songs: Int,
        val truncated: Boolean,
        val listsSkipped: Int,
    )

    /**
     * 导入列表（对齐 lx 导入：同 id 覆盖，其余新建）。
     * 覆盖时保留本地已有的歌单名；超出 [MAX_LISTS]/[MAX_SONGS_PER_LIST] 的部分被丢弃并在报告中标记。
     */
    suspend fun importLists(incoming: List<ImportList>): ImportReport {
        var listCount = 0
        var songCount = 0
        var truncated = false
        var skippedLists = 0
        mutex.withLock {
            context.myListStore.edit { prefs ->
                val user = parseUserMetas(prefs[Keys.META]).toMutableList()
                for (item in incoming) {
                    val isLove = item.id == LOVE_ID
                    if (!isLove) {
                        val idx = user.indexOfFirst { it.id == item.id }
                        if (idx < 0) {
                            if (user.size >= MAX_LISTS) { skippedLists++; continue }
                            user += MyListMeta(item.id, item.name, false, item.sourceListId)
                        }
                    }
                    val songs = if (item.songs.size > MAX_SONGS_PER_LIST) {
                        truncated = true
                        item.songs.take(MAX_SONGS_PER_LIST)
                    } else item.songs
                    prefs[Keys.songs(item.id)] = songsToJson(songs)
                    listCount++
                    songCount += songs.size
                }
                prefs[Keys.META] = JsonArray(user.map { metaToJson(it) }).toString()
            }
        }
        return ImportReport(listCount, songCount, truncated, skippedLists)
    }

    // ── 序列化 ───────────────────────────────────────────────────

    private fun md5(s: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun builtinLists() = listOf(
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
                sourceListId = obj["sourceListId"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun metaToJson(meta: MyListMeta) = JsonObject(
        buildMap {
            put("id", JsonPrimitive(meta.id))
            put("name", JsonPrimitive(meta.name))
            if (!meta.sourceListId.isNullOrEmpty()) {
                put("sourceListId", JsonPrimitive(meta.sourceListId))
            }
        },
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
