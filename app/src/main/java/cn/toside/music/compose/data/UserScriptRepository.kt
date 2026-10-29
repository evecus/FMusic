package cn.toside.music.compose.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 用户音源脚本元数据 */
data class UserScript(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val description: String = "",
    val updatedAt: Long = 0L,
)

/**
 * 用户脚本仓库：脚本内容存 filesDir/user-scripts/<id>.js（可达 100KB+，
 * 不进 DataStore），元数据列表 JSON 存 filesDir/user-scripts/index.json。
 */
class UserScriptRepository(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "user-scripts").apply { mkdirs() }

    private val _scripts = MutableStateFlow<List<UserScript>>(emptyList())

    val scripts: Flow<List<UserScript>> = _scripts

    /**
     * 从脚本提取 meta，兼容两代模板：
     *  - 旧模板：globalThis.lx_setup(key, id, name, description, version, author, homepage, rawScript)
     *  - 新模板：文件头注释 @name / @version / @author / @description / @homepage
     */
    fun parseMeta(scriptText: String): UserScript {
        val lxSetup = parseLxSetup(scriptText)
        val comment = parseCommentHeader(scriptText)
        fun pick(field: String, fallback: String = ""): String =
            lxSetup[field]?.takeIf { it.isNotEmpty() } ?: comment[field]?.takeIf { it.isNotEmpty() } ?: fallback
        return UserScript(
            id = "",
            name = pick("name"),
            version = pick("version"),
            author = pick("author"),
            homepage = pick("homepage"),
            description = pick("description"),
        )
    }

    /** 供 JS 层读取的 meta JSON（name/version/author/homepage/description） */
    fun parseMetaJson(scriptText: String): JSONObject {
        val m = parseMeta(scriptText)
        return JSONObject().apply {
            put("name", m.name)
            put("version", m.version)
            put("author", m.author)
            put("homepage", m.homepage)
            put("description", m.description)
        }
    }

    private fun parseLxSetup(scriptText: String): Map<String, String> {
        val regex = Regex(
            """globalThis\.lx_setup\s*\(([^)]*)\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val args = regex.find(scriptText)?.groupValues?.get(1) ?: return emptyMap()
        val parts = splitArgs(args)
        fun at(i: Int) = parts.getOrNull(i)?.trim('"', '\'', ' ').orEmpty()
        return mapOf(
            "id" to at(1),
            "name" to at(2),
            "description" to at(3),
            "version" to at(4),
            "author" to at(5),
            "homepage" to at(6),
        )
    }

    private fun parseCommentHeader(scriptText: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        // 只扫前 60 行注释头
        scriptText.lineSequence().take(60).forEach { line ->
            val m = Regex("""@(name|version|author|description|homepage)\s+(.+)""").find(line) ?: return@forEach
            val key = m.groupValues[1]
            if (out.containsKey(key)) return@forEach
            out[key] = m.groupValues[2].trim().trimEnd('*', '/', ' ').trim()
        }
        return out
    }

    /** 保存脚本，返回脚本 id */
    suspend fun save(scriptText: String, existingId: String? = null): UserScript =
        withContext(Dispatchers.IO) {
            val meta = parseMeta(scriptText)
            val scriptId = existingId ?: UUID.randomUUID().toString()
            File(dir, "$scriptId.js").writeText(scriptText)
            val current = readIndex().toMutableList()
            val idx = current.indexOfFirst { it.id == scriptId }
            val script = meta.copy(
                id = scriptId,
                name = meta.name.ifEmpty { "未命名脚本" },
                updatedAt = System.currentTimeMillis(),
            )
            if (idx >= 0) current[idx] = script else current.add(script)
            writeIndex(current)
            _scripts.value = current
            script
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.js").delete()
        val current = readIndex().filter { it.id != id }
        writeIndex(current)
        _scripts.value = current
    }

    suspend fun load(): List<UserScript> = withContext(Dispatchers.IO) {
        readIndex().also { _scripts.value = it }
    }

    suspend fun readScript(id: String): String? = withContext(Dispatchers.IO) {
        File(dir, "$id.js").takeIf { it.exists() }?.readText()
    }

    private fun splitArgs(s: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var strCh = ' '
        val cur = StringBuilder()
        for (c in s) {
            when {
                inStr -> {
                    cur.append(c)
                    if (c == strCh) inStr = false
                }
                c == '"' || c == '\'' -> {
                    inStr = true
                    strCh = c
                    cur.append(c)
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    cur.append(c)
                }
                c == ')' || c == ']' || c == '}' -> {
                    depth--
                    cur.append(c)
                }
                c == ',' && depth == 0 -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    private fun readIndex(): List<UserScript> {
        val f = File(dir, "index.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                UserScript(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    version = o.optString("version"),
                    author = o.optString("author"),
                    homepage = o.optString("homepage"),
                    description = o.optString("description"),
                    updatedAt = o.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(list: List<UserScript>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("version", s.version)
                    put("author", s.author)
                    put("homepage", s.homepage)
                    put("description", s.description)
                    put("updatedAt", s.updatedAt)
                }
            )
        }
        File(dir, "index.json").writeText(arr.toString())
    }
}
