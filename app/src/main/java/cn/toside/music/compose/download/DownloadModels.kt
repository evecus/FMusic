package cn.toside.music.compose.download

import org.json.JSONArray
import org.json.JSONObject

/** 下载任务类型：歌曲 / MV */
enum class DownloadTaskType { SONG, MV }

/** 任务状态（对齐 mobile：等待 / 下载中 / 已暂停 / 已完成 / 失败） */
enum class DownloadStatus { WAITING, DOWNLOADING, PAUSED, COMPLETED, ERROR }

/**
 * 下载任务（歌曲或 MV）。
 * musicInfoJson 为 MusicInfo.toPersistentJson()，用于下载歌词与还原展示信息。
 */
data class DownloadTask(
    val id: String,
    val type: DownloadTaskType,
    val source: String,
    val musicInfoJson: String,
    val name: String,
    val singer: String,
    val quality: String,
    val fileName: String,
    val status: DownloadStatus,
    val errorMsg: String = "",
    val percent: Float = 0f,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val speed: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val mvId: String?
        get() = if (type == DownloadTaskType.MV) {
            runCatching {
                org.json.JSONObject(musicInfoJson).optString("mv").takeIf { it.isNotEmpty() }
            }.getOrNull()
        } else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("source", source)
        put("musicInfoJson", musicInfoJson)
        put("name", name)
        put("singer", singer)
        put("quality", quality)
        put("fileName", fileName)
        put("status", status.name)
        put("errorMsg", errorMsg)
        put("percent", percent.toDouble())
        put("downloaded", downloaded)
        put("total", total)
        put("speed", speed)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DownloadTask = DownloadTask(
            id = o.optString("id"),
            type = runCatching { DownloadTaskType.valueOf(o.optString("type")) }
                .getOrDefault(DownloadTaskType.SONG),
            source = o.optString("source"),
            musicInfoJson = o.optString("musicInfoJson"),
            name = o.optString("name"),
            singer = o.optString("singer"),
            quality = o.optString("quality"),
            fileName = o.optString("fileName"),
            status = runCatching { DownloadStatus.valueOf(o.optString("status")) }
                .getOrDefault(DownloadStatus.ERROR),
            errorMsg = o.optString("errorMsg"),
            percent = o.optDouble("percent", 0.0).toFloat(),
            downloaded = o.optLong("downloaded", 0L),
            total = o.optLong("total", 0L),
            speed = o.optString("speed"),
            createdAt = o.optLong("createdAt", 0L),
        )

        fun listToJson(tasks: List<DownloadTask>): String =
            JSONArray().apply { tasks.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String): List<DownloadTask> = runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/** 文件大小格式化（如 3.2MB） */
fun sizeFormate(bytes: Long): String {
    if (bytes <= 0) return "0KB"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format("%.1fKB", kb)
        kb < 1024 * 1024 -> String.format("%.1fMB", kb / 1024)
        else -> String.format("%.2fGB", kb / 1024 / 1024)
    }
}
