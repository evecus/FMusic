package com.music.fm.js

import com.music.fm.MusicApplication

/** 从 assets 读取 JS 资产（UTF-8）。 */
object Assets {
    fun read(path: String): String =
        MusicApplication.instance.assets.open(path).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
}
