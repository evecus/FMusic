package com.music.fm.download

/**
 * 歌词合并工具（逐行移植 lx-music-mobile src/utils/download/lrcTool.ts）：
 * 将主歌词、翻译歌词、罗马音歌词按时间戳逐行合并。
 */
object LrcTool {
    private val timeTagRegex = Regex("""\[(\d{2}:\d{2}\.\d{2,3})\]""")
    private val lineSplit = Regex("\r\n|\n|\r")

    private fun parseLrcToMap(lrc: String?): Map<String, List<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        if (lrc.isNullOrEmpty()) return map
        for (line in lrc.split(lineSplit)) {
            val tags = timeTagRegex.findAll(line).map { it.value }.toList()
            if (tags.isEmpty()) continue
            val text = line.replace(timeTagRegex, "").trim()
            if (text.isEmpty()) continue
            for (tag in tags) map.getOrPut(tag) { mutableListOf() }.add(text)
        }
        return map
    }

    fun mergeLyrics(lrc: String, tlrc: String?, rlrc: String?): String {
        if (lrc.isEmpty() || (tlrc.isNullOrEmpty() && rlrc.isNullOrEmpty())) return lrc

        val main = parseLrcToMap(lrc)
        val trans = parseLrcToMap(tlrc)
        val roma = parseLrcToMap(rlrc)
        val result = mutableListOf<String>()

        // 保留 LRC 头部元数据标签（[ti:] [ar:] 等）
        val meta = lrc.split(lineSplit).filter { !timeTagRegex.containsMatchIn(it) && it.startsWith("[") }
        if (meta.isNotEmpty()) {
            result.addAll(meta)
            result.add("")
        }
        for (ts in main.keys.sorted()) {
            main[ts]?.forEach { result.add(ts + it) }
            trans[ts]?.forEach { result.add(ts + it) }
            roma[ts]?.forEach { result.add(ts + it) }
        }
        return result.joinToString("\n")
    }
}
