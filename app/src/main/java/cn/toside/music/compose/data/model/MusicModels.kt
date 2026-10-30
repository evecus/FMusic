package cn.toside.music.compose.data.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 音乐平台源 */
object Sources {
    const val KW = "kw" // 酷我
    const val KG = "kg" // 酷狗
    const val TX = "tx" // QQ 音乐
    const val WY = "wy" // 网易云
    const val MG = "mg" // 咪咕
    const val XM = "xm" // 虾米

    val all = listOf(KW, KG, TX, WY, MG, XM)

    fun label(source: String): String = when (source) {
        KW -> "酷我"
        KG -> "酷狗"
        TX -> "QQ音乐"
        WY -> "网易云"
        MG -> "咪咕"
        XM -> "虾米"
        else -> source
    }
}

/** 音质类型 */
object Qualitys {
    const val Q128 = "128k"
    const val Q320 = "320k"
    const val FLAC = "flac"
    const val FLAC24 = "flac24bit"

    val all = listOf(Q128, Q320, FLAC, FLAC24)

    fun label(type: String): String = when (type) {
        Q128 -> "标准 128K"
        Q320 -> "高品 320K"
        FLAC -> "无损 FLAC"
        FLAC24 -> "超高 FLAC24"
        else -> type
    }
}

/**
 * 单首音乐。music-sdk 的 musicInfo 字段随平台而异（songmid / musicId / hash / mid 等），
 * 因此保留原始 JSON（[raw]），取 URL / 歌词 / 换源重试时原样回传给 SDK。
 */
data class MusicInfo(
    val id: String,
    val name: String,
    val singer: String,
    val source: String,
    val albumName: String?,
    val interval: String?,
    val img: String?,
    val mv: String?,
    val raw: JsonObject,
) {
    val uniqueKey: String get() = "$source-$id"

    fun toJson(): String = raw.toString()

    /**
     * 持久化用：在 raw 基础上补齐派生字段（raw 里可能没有 source/id 等规范化字段），
     * 保证读回时无需外部上下文即可通过 [parse] 还原。
     */
    fun toPersistentJson(): JsonObject = JsonObject(
        LinkedHashMap(raw).apply {
            put("source", JsonPrimitive(source))
            put("id", JsonPrimitive(id))
            put("name", JsonPrimitive(name))
            put("singer", JsonPrimitive(singer))
            albumName?.let { put("albumName", JsonPrimitive(it)) }
            interval?.let { put("interval", JsonPrimitive(it)) }
            img?.let { put("img", JsonPrimitive(it)) }
            mv?.let { put("mv", JsonPrimitive(it)) }
        },
    )

    companion object {
        fun parse(obj: JsonObject, defaultSource: String = ""): MusicInfo? {
            val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: defaultSource
            val id = (obj["id"] ?: obj["songmid"] ?: obj["musicId"] ?: obj["mid"])
                ?.jsonPrimitive?.contentOrNull
                ?: return null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val singer = (obj["singer"] ?: obj["artists"])?.let { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull ?: ""
                    is JsonObject -> el["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    else -> ""
                }
            } ?: ""
            val album = (obj["albumName"] ?: obj["album"])?.let { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull
                    is JsonObject -> el["name"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
            val interval = obj["interval"]?.jsonPrimitive?.contentOrNull
            val img = obj["img"]?.jsonPrimitive?.contentOrNull
                ?: obj["pic"]?.jsonPrimitive?.contentOrNull
            val mv = obj["mv"]?.let {
                (it as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotEmpty() && v != "null" }
            }
            return MusicInfo(
                id = id,
                name = name,
                singer = singer,
                source = source.ifEmpty { defaultSource },
                albumName = album,
                interval = interval,
                img = img,
                mv = mv,
                raw = obj,
            )
        }
    }
}

/** 一页搜索结果 */
data class SearchResult(
    val list: List<MusicInfo>,
    val total: Int,
    val page: Int,
    val limit: Int,
) {
    companion object {
        fun parse(json: String, fallbackSource: String): SearchResult {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
                ?: return SearchResult(emptyList(), 0, 1, 30)
            val listEl = obj["list"] as? kotlinx.serialization.json.JsonArray
            val list = listEl?.mapNotNull { el ->
                (el as? JsonObject)?.let { MusicInfo.parse(it, fallbackSource) }
            } ?: emptyList()
            val total = obj["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: list.size
            return SearchResult(
                list = list,
                total = total,
                page = obj["page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                limit = obj["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 30,
            )
        }
    }
}

/** 歌词（对齐 user-api-preload 的 verifyLyricInfo 输出） */
data class LyricInfo(
    val lyric: String,
    val tlyric: String?,
    val rlyric: String?,
    val lxlyric: String?,
) {
    companion object {
        fun parse(json: String): LyricInfo? {
            val obj = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
            }.getOrNull() ?: return null
            val lyric = obj["lyric"]?.jsonPrimitive?.contentOrNull ?: return null
            fun opt(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
            return LyricInfo(lyric, opt("tlyric"), opt("rlyric"), opt("lxlyric"))
        }
    }
}

/** 取 URL 结果 */
data class MusicUrl(
    val type: String,
    val url: String,
) {
    companion object {
        fun parse(json: String): MusicUrl? {
            val el = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json)
            }.getOrNull() ?: return null
            val obj = el as? JsonObject ?: return null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return null
            return MusicUrl(obj["type"]?.jsonPrimitive?.contentOrNull ?: "", url)
        }
    }
}

/** 平台榜单条目（id 形如 kw__93，bangid 为平台原始榜单 id） */
data class BoardItem(
    val id: String,
    val name: String,
    val bangid: String,
    val source: String,
) {
    companion object {
        fun parse(obj: JsonObject, source: String): BoardItem? {
            fun s(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            val bangid = s("bangid") ?: return null
            return BoardItem(
                id = s("id") ?: "${source}__$bangid",
                name = s("name") ?: "",
                bangid = bangid,
                source = source,
            )
        }
    }
}

/** getBoards 返回结果：{ list: [{id, name, bangid}], source } */
data class BoardsResult(
    val source: String,
    val list: List<BoardItem>,
) {
    companion object {
        fun parse(json: String, fallbackSource: String): BoardsResult {
            val obj = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
            }.getOrNull() ?: return BoardsResult(fallbackSource, emptyList())
            val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: fallbackSource
            val items = (obj["list"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
                (el as? JsonObject)?.let { BoardItem.parse(it, source) }
            } ?: emptyList()
            return BoardsResult(source, items)
        }
    }
}

/** 榜内歌曲分页结果，结构同搜索结果（复用 [SearchResult] 解析） */
data class BoardListResult(
    val list: List<MusicInfo>,
    val total: Int,
    val page: Int,
    val limit: Int,
) {
    companion object {
        fun parse(json: String, fallbackSource: String): BoardListResult {
            val r = SearchResult.parse(json, fallbackSource)
            return BoardListResult(r.list, r.total, r.page, r.limit)
        }
    }
}

/** 歌单排序项（kw 最新/最热等，各源静态定义于 SDK 的 songList.sortList） */
data class SongListSort(
    val id: String,
    val name: String,
) {
    companion object {
        fun parse(el: JsonElement): SongListSort? {
            val obj = el as? JsonObject ?: return null
            fun s(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            val id = s("id") ?: return null
            return SongListSort(id, s("name") ?: id)
        }
    }
}

/** 歌单标签项 */
data class SongListTag(
    val id: String,
    val name: String,
    val parentId: String?,
    val parentName: String?,
) {
    companion object {
        fun parse(el: JsonElement): SongListTag? {
            val obj = el as? JsonObject ?: return null
            fun s(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            val id = s("id") ?: return null
            return SongListTag(id, s("name") ?: id, s("parent_id"), s("parent_name"))
        }
    }
}

/** 歌单标签分组 */
data class SongListTagGroup(
    val name: String,
    val list: List<SongListTag>,
)

/** getTags 返回结果：{ tags: [{name, list}], hotTag: [...], source } */
data class SongListTagsResult(
    val source: String,
    val hotTags: List<SongListTag>,
    val groups: List<SongListTagGroup>,
) {
    companion object {
        fun parse(json: String, fallbackSource: String): SongListTagsResult {
            val obj = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
            }.getOrNull() ?: return SongListTagsResult(fallbackSource, emptyList(), emptyList())
            val source = obj["source"]?.jsonPrimitive?.contentOrNull ?: fallbackSource
            val hotTags = (obj["hotTag"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { SongListTag.parse(it) } ?: emptyList()
            val groups = (obj["tags"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { gEl ->
                val g = gEl as? JsonObject ?: return@mapNotNull null
                val name = g["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val list = (g["list"] as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { SongListTag.parse(it) } ?: emptyList()
                if (list.isEmpty()) null else SongListTagGroup(name, list)
            } ?: emptyList()
            return SongListTagsResult(source, hotTags, groups)
        }
    }
}

/** 歌单列表项 */
data class SongListItem(
    val id: String,
    val name: String,
    val img: String?,
    val author: String?,
    val playCount: String?,
    val total: String?,
    val source: String,
) {
    companion object {
        fun parse(obj: JsonObject, defaultSource: String): SongListItem? {
            fun s(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            val id = s("id") ?: return null
            return SongListItem(
                id = id,
                name = s("name") ?: "",
                img = s("img"),
                author = s("author"),
                playCount = s("play_count"),
                total = s("total"),
                source = s("source") ?: defaultSource,
            )
        }
    }
}

/** 歌单列表分页结果：{ list, total, page, limit } */
data class SongListPageResult(
    val list: List<SongListItem>,
    val total: Int,
    val page: Int,
    val limit: Int,
) {
    companion object {
        fun parse(json: String, fallbackSource: String): SongListPageResult {
            val obj = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
            }.getOrNull() ?: return SongListPageResult(emptyList(), 0, 1, 30)
            val items = (obj["list"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
                (el as? JsonObject)?.let { SongListItem.parse(it, fallbackSource) }
            } ?: emptyList()
            fun i(key: String, def: Int) = obj[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: def
            return SongListPageResult(items, i("total", items.size), i("page", 1), i("limit", 30))
        }
    }
}

/** 歌单详情信息（列表头） */
data class SongListDetailInfo(
    val name: String?,
    val img: String?,
    val author: String?,
    val desc: String?,
) {
    companion object {
        fun parse(obj: JsonObject?): SongListDetailInfo? {
            obj ?: return null
            fun s(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
            return SongListDetailInfo(s("name"), s("img"), s("author"), s("desc"))
        }
    }
}

/** 歌单内歌曲分页结果：{ list, total, page, limit, info } */
data class SongListDetailResult(
    val list: List<MusicInfo>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val info: SongListDetailInfo?,
) {
    companion object {
        fun parse(json: String, fallbackSource: String): SongListDetailResult {
            val obj = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json) as? JsonObject
            }.getOrNull() ?: return SongListDetailResult(emptyList(), 0, 1, 30, null)
            val list = (obj["list"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
                (el as? JsonObject)?.let { MusicInfo.parse(it, fallbackSource) }
            } ?: emptyList()
            fun i(key: String, def: Int) = obj[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: def
            return SongListDetailResult(
                list = list,
                total = i("total", list.size),
                page = i("page", 1),
                limit = i("limit", 30),
                info = SongListDetailInfo.parse(obj["info"] as? JsonObject),
            )
        }
    }
}

private val JsonElement.jsonPrimitive: JsonPrimitive?
    get() = this as? JsonPrimitive

private fun <T> T?.orEmpty(): String where T : CharSequence = this?.toString() ?: ""
