package cn.toside.music.compose.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 远程音源脚本下载器。
 *
 * 支持直接下载 js 文本，并把常见的「网页版」链接自动规整成直链：
 *  - github.com/{owner}/{repo}/blob/{ref}/{path}  -> raw.githubusercontent.com/...
 *  - github.com/{owner}/{repo}/raw/{ref}/{path}    -> raw.githubusercontent.com/...
 *  - gitee.com/{owner}/{repo}/blob/{ref}/{path}    -> gitee.com/{owner}/{repo}/raw/{ref}/{path}
 *  - raw.githubusercontent.com / cdn.jsdelivr.net 等直链原样使用
 */
object RemoteScriptFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    /** 规整链接：补全 scheme，GitHub / Gitee 网页链接转直链 */
    fun normalize(raw: String): String {
        var url = raw.trim()
            .trim('"', '\'', '`', ' ', '\n', '\r', '\t', '　')
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            url = "https://$url"
        }

        githubBlob(url)?.let { return it }
        githubRaw(url)?.let { return it }
        giteeBlob(url)?.let { return it }

        return url
    }

    private fun githubBlob(url: String): String? {
        val m = Regex(
            """^(?:https?://)?(?:www\.)?github\.com/([^/?#]+)/([^/?#]+)/blob/([^/?#]+)/(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(url) ?: return null
        return "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}" +
            "/${m.groupValues[3]}/${m.groupValues[4]}"
    }

    private fun githubRaw(url: String): String? {
        val m = Regex(
            """^(?:https?://)?(?:www\.)?github\.com/([^/?#]+)/([^/?#]+)/raw/([^/?#]+)/(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(url) ?: return null
        return "https://raw.githubusercontent.com/${m.groupValues[1]}/${m.groupValues[2]}" +
            "/${m.groupValues[3]}/${m.groupValues[4]}"
    }

    private fun giteeBlob(url: String): String? {
        val m = Regex(
            """^(?:https?://)?(?:www\.)?gitee\.com/([^/?#]+)/([^/?#]+)/blob/([^/?#]+)/(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(url) ?: return null
        return "https://gitee.com/${m.groupValues[1]}/${m.groupValues[2]}" +
            "/raw/${m.groupValues[3]}/${m.groupValues[4]}"
    }

    /** 下载脚本内容（需在 IO 线程调用） */
    fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalArgumentException("请求失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalArgumentException("响应为空")
            val text = body.string()
            if (text.isBlank()) throw IllegalArgumentException("下载内容为空，请确认是否为 js 直链")
            return text
        }
    }
}
