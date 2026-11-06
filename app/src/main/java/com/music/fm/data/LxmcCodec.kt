package com.music.fm.data

import com.music.fm.data.model.MusicInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 一个待导出的列表（歌曲为 FM 内部的 SDK 旧格式） */
data class ExportList(
    val id: String,
    val name: String,
    val sourceListId: String?,
    val songs: List<MusicInfo>,
)

/** 解析出的列表（id 已映射为 FM 内部 id：love / u_xxx） */
data class ImportList(
    val id: String,
    val name: String,
    val sourceListId: String?,
    val songs: List<MusicInfo>,
)

data class LxmcParseResult(
    val lists: List<ImportList>,
    /** 因格式不支持（如 local 本地歌曲）或缺字段被丢弃的歌曲数 */
    val skippedSongs: Int,
)

/**
 * lx-music「列表备份」文件（.lxmc）编解码，与 lx-music 手机版 / 桌面版列表备份互通。
 * 移植自 lx-music-mobile：
 *  - screens/Home/Views/Setting/settings/Backup/actions.ts（文件类型与导入导出流程）
 *  - utils/index.ts 的 toNewMusicInfo / toOldMusicInfo（歌曲新旧格式互转）
 *
 * FM 内部存的是 SDK 旧格式歌曲（songmid / types / _types / hash …），
 * lx 文件里是新格式（id / meta{songId, qualitys, _qualitys, …}）。
 */
object LxmcCodec {

    const val DEFAULT_LIST_ID = "default"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // ── 导出 ─────────────────────────────────────────────────────

    /** 导出为 lx 的 playList_v2 文件内容 */
    fun encode(lists: List<ExportList>): String {
        val data = JsonArray(lists.map { list ->
            JsonObject(buildMap {
                put("id", JsonPrimitive(list.id))
                put("name", JsonPrimitive(list.name))
                put("list", JsonArray(list.songs.map { songToNew(it) }))
                if (list.id != MyListRepository.LOVE_ID) {
                    list.sourceListId?.takeIf { it.isNotEmpty() }?.let {
                        put("source", JsonPrimitive(it.substringBefore("__")))
                        put("sourceListId", JsonPrimitive(it))
                    }
                    put("locationUpdateTime", JsonNull)
                }
            })
        })
        return JsonObject(
            mapOf("type" to JsonPrimitive("playList_v2"), "data" to data),
        ).toString()
    }

    /** lx 的 .lxmc 是 gzip 压缩的 JSON，导出同样压缩以便 lx 直接导入 */
    fun encodeBytes(lists: List<ExportList>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(encode(lists).toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** 旧格式（FM 内部）→ lx 新格式，对应 toNewMusicInfo */
    fun songToNew(song: MusicInfo): JsonObject {
        val raw = song.raw
        val source = song.source
        val songmid = raw["songmid"]?.str() ?: song.id
        val meta = LinkedHashMap<String, JsonElement>()
        meta["songId"] = JsonPrimitive(songmid)
        (song.albumName ?: raw["albumName"]?.str())?.let { meta["albumName"] = JsonPrimitive(it) }
        (song.img ?: raw["img"]?.str())?.let { meta["picUrl"] = JsonPrimitive(it) }

        var id = "${source}_$songmid"
        // 音质：flac32bit 归一为 flac24bit（对齐 toNewMusicInfo）
        val types = (raw["types"] as? JsonArray) ?: JsonArray(emptyList())
        val _types = (raw["_types"] as? JsonObject) ?: JsonObject(emptyMap())
        var qualitys: JsonArray = types
        var _qualitys: JsonObject = _types
        if (_qualitys.containsKey("flac32bit") && !_qualitys.containsKey("flac24bit")) {
            _qualitys = JsonObject(
                LinkedHashMap(_qualitys).also { it["flac24bit"] = it.remove("flac32bit")!! },
            )
            qualitys = JsonArray(types.map { q ->
                val o = q as? JsonObject ?: return@map q
                if (o["type"]?.str() == "flac32bit") {
                    JsonObject(LinkedHashMap(o).also { it["type"] = JsonPrimitive("flac24bit") })
                } else q
            })
        }
        meta["qualitys"] = qualitys
        meta["_qualitys"] = _qualitys
        raw["albumId"]?.takeIf { it !is JsonNull }?.let { meta["albumId"] = it }
        raw["mv"]?.takeIf { it !is JsonNull && it.str().let { s -> !s.isNullOrEmpty() && s != "null" } }
            ?.let { meta["mv"] = it }

        when (source) {
            "kg" -> {
                raw["hash"]?.takeIf { it !is JsonNull }?.let { meta["hash"] = it }
                id = songmid + "_" + (raw["hash"]?.str() ?: "")
            }
            "tx" -> {
                raw["strMediaMid"]?.takeIf { it !is JsonNull }?.let { meta["strMediaMid"] = it }
                raw["albumMid"]?.takeIf { it !is JsonNull }?.let { meta["albumMid"] = it }
                raw["songId"]?.takeIf { it !is JsonNull }?.let { meta["id"] = it }
            }
            "mg" -> {
                for (k in listOf("copyrightId", "lrcUrl", "mrcUrl", "trcUrl")) {
                    raw[k]?.takeIf { it !is JsonNull }?.let { meta[k] = it }
                }
            }
        }

        return JsonObject(buildMap {
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(song.name))
            put("singer", JsonPrimitive(song.singer))
            put("source", JsonPrimitive(source))
            (song.interval ?: raw["interval"]?.str())?.let { put("interval", JsonPrimitive(it)) }
            put("meta", JsonObject(meta))
        })
    }

    // ── 导入 ─────────────────────────────────────────────────────

    /** 解析备份文件字节：gzip（lx 的 .lxmc）或纯 JSON（.json）均可 */
    fun decodeBytes(bytes: ByteArray): LxmcParseResult {
        val isGzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val text = if (isGzip) {
            runCatching {
                java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
                    .toString(Charsets.UTF_8)
            }.getOrElse { throw IllegalArgumentException("文件解压失败") }
        } else bytes.toString(Charsets.UTF_8)
        return decode(text.removePrefix("\uFEFF"))
    }

    /** 解析 lx 列表备份文件；不支持的文件类型抛 [IllegalArgumentException] */
    fun decode(text: String): LxmcParseResult {
        var el = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull()
        // 兼容 PC v1.14.0 导出数据被序列化两次的问题
        if (el is JsonPrimitive && el.isString) {
            el = runCatching { json.parseToJsonElement(el.content) }.getOrNull()
        }
        val root = el as? JsonObject
            ?: throw IllegalArgumentException("文件格式不正确")
        var skipped = 0
        fun songs(arr: JsonElement?, isNew: Boolean): List<MusicInfo> {
            val out = ArrayList<MusicInfo>()
            val seen = HashSet<String>()
            for (el in (arr as? JsonArray) ?: return out) {
                val obj = el as? JsonObject
                val info = obj?.let { if (isNew) songFromNew(it) else songFromOld(it) }
                // 对齐 filterMusicList：丢弃无 id / 无歌名 / 重复项
                if (info == null || info.name.isEmpty()) { skipped++; continue }
                if (!seen.add(info.uniqueKey)) continue
                out += info
            }
            return out
        }
        fun list(obj: JsonObject, isNew: Boolean): ImportList? {
            val rawId = obj["id"]?.str() ?: return null
            val name = obj["name"]?.str() ?: rawId
            val sourceListId = obj["sourceListId"]?.str()?.takeIf { it.isNotEmpty() }
            val id = when (rawId) {
                MyListRepository.LOVE_ID -> MyListRepository.LOVE_ID
                else -> if (rawId.startsWith("u_")) rawId else "u_$rawId"
            }
            val displayName = if (rawId == DEFAULT_LIST_ID) "试听列表" else name
            return ImportList(id, displayName, sourceListId, songs(obj["list"], isNew))
        }
        fun lists(arr: JsonElement?, isNew: Boolean): List<ImportList> =
            ((arr as? JsonArray) ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonObject)?.let { o -> list(o, isNew) } }

        val result: List<ImportList> = when (val type = root["type"]?.str()) {
            "playList_v2" -> lists(root["data"], true)
            "allData_v2" -> lists(root["playList"], true)
            "playList" -> lists(root["data"], false)
            "allData" -> {
                // 兼容 0.6.2 及以前：defaultList 单独存放
                val def = root["defaultList"] as? JsonObject
                if (def != null) listOfNotNull(list(def.withId(DEFAULT_LIST_ID), false))
                else lists(root["playList"], false)
            }
            "defautlList" -> listOfNotNull(
                (root["data"] as? JsonObject)?.withId(DEFAULT_LIST_ID)?.let { list(it, false) },
            )
            "playListPart_v2" -> listOfNotNull((root["data"] as? JsonObject)?.let { list(it, true) })
            "playListPart" -> listOfNotNull((root["data"] as? JsonObject)?.let { list(it, false) })
            else -> throw IllegalArgumentException(
                "不支持的备份文件类型：${type ?: "未知"}（需要 lx-music 的列表备份文件 .lxmc）",
            )
        }
        // 默认列表（试听列表）为空时不导入，避免多出一个空列表
        val filtered = result.filterNot { it.name == "试听列表" && it.songs.isEmpty() && it.id.startsWith("u_") }
        return LxmcParseResult(filtered, skipped)
    }

    /** 新格式 → FM 内部旧格式，对应 toOldMusicInfo；local 本地歌曲返回 null */
    private fun songFromNew(obj: JsonObject): MusicInfo? {
        val source = obj["source"]?.str() ?: return null
        if (source == "local") return null
        val meta = obj["meta"] as? JsonObject ?: return null
        val songId = meta["songId"]?.str() ?: return null
        val old = LinkedHashMap<String, JsonElement>()
        old["name"] = JsonPrimitive(obj["name"]?.str() ?: "")
        old["singer"] = JsonPrimitive(obj["singer"]?.str() ?: "")
        old["source"] = JsonPrimitive(source)
        old["songmid"] = JsonPrimitive(songId)
        obj["interval"]?.takeIf { it !is JsonNull }?.let { old["interval"] = it }
        old["albumName"] = meta["albumName"] ?: JsonPrimitive("")
        old["img"] = meta["picUrl"] ?: JsonPrimitive("")
        old["typeUrl"] = JsonObject(emptyMap())
        meta["albumId"]?.takeIf { it !is JsonNull }?.let { old["albumId"] = it }
        // flac32bit 归一为 flac24bit（对齐 fixNewMusicInfoQuality）
        var types = (meta["qualitys"] as? JsonArray) ?: JsonArray(emptyList())
        var _types = (meta["_qualitys"] as? JsonObject) ?: JsonObject(emptyMap())
        if (_types.containsKey("flac32bit") && !_types.containsKey("flac24bit")) {
            _types = JsonObject(LinkedHashMap(_types).also { it["flac24bit"] = it.remove("flac32bit")!! })
            types = JsonArray(types.map { q ->
                val o = q as? JsonObject ?: return@map q
                if (o["type"]?.str() == "flac32bit") {
                    JsonObject(LinkedHashMap(o).also { it["type"] = JsonPrimitive("flac24bit") })
                } else q
            })
        }
        old["types"] = types
        old["_types"] = _types
        meta["mv"]?.takeIf { it !is JsonNull }?.let { old["mv"] = it }
        when (source) {
            "kg" -> meta["hash"]?.takeIf { it !is JsonNull }?.let { old["hash"] = it }
            "tx" -> {
                meta["strMediaMid"]?.takeIf { it !is JsonNull }?.let { old["strMediaMid"] = it }
                meta["albumMid"]?.takeIf { it !is JsonNull }?.let { old["albumMid"] = it }
                meta["id"]?.takeIf { it !is JsonNull }?.let { old["songId"] = it }
            }
            "mg" -> for (k in listOf("copyrightId", "lrcUrl", "mrcUrl", "trcUrl")) {
                meta[k]?.takeIf { it !is JsonNull }?.let { old[k] = it }
            }
        }
        return MusicInfo.parse(JsonObject(old))
    }

    /** 0.x 旧版列表里的歌曲本身就是 SDK 旧格式 */
    private fun songFromOld(obj: JsonObject): MusicInfo? {
        if (obj["source"]?.str() == "local") return null
        if (obj["songmid"]?.str() == null) return null
        val patched = LinkedHashMap(obj)
        if (patched["types"] !is JsonArray) patched["types"] = JsonArray(emptyList())
        if (patched["_types"] !is JsonObject) patched["_types"] = JsonObject(emptyMap())
        // 旧版 id 字段含义不同，去掉以免覆盖 songmid
        patched.remove("id")
        return MusicInfo.parse(JsonObject(patched))
    }

    private fun JsonObject.withId(id: String) =
        JsonObject(LinkedHashMap(this).also { it["id"] = JsonPrimitive(id) })

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull
}
