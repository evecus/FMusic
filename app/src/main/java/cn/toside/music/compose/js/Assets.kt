package cn.toside.music.compose.js

import cn.toside.music.compose.MusicApplication

/** 从 assets 读取 JS 资产（UTF-8）。 */
object Assets {
    fun read(path: String): String =
        MusicApplication.instance.assets.open(path).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
}
