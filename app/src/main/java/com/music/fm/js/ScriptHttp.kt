package com.music.fm.js

import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 音源脚本的 HTTP 桥：builtin 沙箱 fetch 与 user 沙箱 request 都经此执行。
 * 同步阻塞调用放在 IO 线程，完成后切回 [JsEngineThread] 由 preload 消费结果。
 *
 * payload 结构对齐 preload 契约：
 *  - fetch  ： { statusCode, ok, url, headers, contentType, bodyText | bodyB64, bodySize }
 *  - request： { statusCode, statusMessage, headers, body }（binary 时 body 为 base64）
 */
class ScriptHttp(private val engine: JsEngineThread) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val pending = mutableMapOf<Int, Call>()
    private val pendingRequests = mutableMapOf<String, Call>()

    // ── builtin 沙箱 fetch ────────────────────────────────────────

    fun fetch(callId: Int, url: String, optionsJson: String?, onResult: (String) -> Unit) {
        val opts = optionsJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val binary = opts.optBoolean("binary", true)
        val call = buildCall(url, opts)
        synchronized(pending) { pending[callId] = call }
        enqueue(call, binary) { ok, payload ->
            synchronized(pending) { pending.remove(callId) }
            val json = JSONObject().apply {
                put("callId", callId)
                put("ok", ok)
                if (ok) put("data", payload) else put("error", payload.optString("error"))
            }
            onResult(json.toString())
        }
    }

    fun fetchAbort(callId: Int) {
        synchronized(pending) { pending.remove(callId) }?.cancel()
    }

    // ── user 沙箱 request ─────────────────────────────────────────

    fun request(requestKey: String, url: String, optionsJson: String?, onResult: (String) -> Unit) {
        val opts = optionsJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val binary = opts.optBoolean("binary", false)
        val call = buildCall(url, opts)
        synchronized(pendingRequests) { pendingRequests[requestKey] = call }
        enqueue(call, binary) { ok, payload ->
            synchronized(pendingRequests) { pendingRequests.remove(requestKey) }
            val json = JSONObject().apply {
                put("requestKey", requestKey)
                put("ok", ok)
                if (ok) put("data", payload) else put("error", payload.optString("error"))
            }
            onResult(json.toString())
        }
    }

    fun cancelRequest(requestKey: String) {
        synchronized(pendingRequests) { pendingRequests.remove(requestKey) }?.cancel()
    }

    // ── 内部 ──────────────────────────────────────────────────────

    // 对齐 lx 原版 fetchData：默认 UA / Accept，脚本显式传的 header 覆盖默认值
    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36",
        "Accept" to "application/json",
    )

    private fun buildCall(url: String, opts: JSONObject): Call {
        val method = opts.optString("method", "get").uppercase()
        val headers = opts.optJSONObject("headers") ?: JSONObject()
        // 脚本 headers（大小写不敏感查找）
        val scriptHeaders = java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
        for (key in headers.keys()) {
            scriptHeaders[key] = headers.optString(key)
        }

        // 请求体：对齐 lx fetchData（POST 时 form→urlencoded、body 对象→JSON、
        // formData→multipart，均自动补 Content-Type；脚本显式指定则不覆盖）
        var bodyStr: String? = null
        var contentType: String? = scriptHeaders.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
        if (method != "GET" && method != "HEAD") {
            when {
                opts.has("body") -> {
                    val b = opts.get("body")
                    bodyStr = if (b is String) b else b.toString()
                    if (contentType == null) contentType = "application/json"
                }
                opts.has("form") -> {
                    val form = opts.optJSONObject("form")
                    if (form != null) {
                        bodyStr = form.keys().asSequence()
                            .map { "${java.net.URLEncoder.encode(it, "UTF-8")}=${java.net.URLEncoder.encode(form.optString(it), "UTF-8")}" }
                            .joinToString("&")
                        if (contentType == null) contentType = "application/x-www-form-urlencoded"
                    }
                }
                opts.has("formData") -> {
                    val formData = opts.optJSONObject("formData")
                    if (formData != null) {
                        val boundary = "----lxform" + java.util.UUID.randomUUID().toString().replace("-", "")
                        bodyStr = formData.keys().asSequence().joinToString("\r\n") { key ->
                            "--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n${formData.optString(key)}"
                        } + "\r\n--$boundary--"
                        if (contentType == null) contentType = "multipart/form-data; boundary=$boundary"
                    }
                }
            }
        }

        val builder = Request.Builder().url(url)
        // 先挂默认 header，再用脚本 header 覆盖（header() 会替换同名项）
        for ((key, value) in defaultHeaders) {
            if (scriptHeaders.keys.none { it.equals(key, ignoreCase = true) }) {
                builder.header(key, value)
            }
        }
        for ((key, value) in scriptHeaders) {
            builder.header(key, value)
        }
        val request = if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            builder.method(method, (bodyStr ?: "").toRequestBody(contentType?.toMediaTypeOrNull())).build()
        }
        // 对齐 lx fetchData 默认 13s 超时；脚本显式 timeout 上限 60s
        val timeout = opts.optLong("timeout", 0)
        val effective = if (timeout in 1..60_000) timeout else 13_000
        return client.newBuilder()
            .callTimeout(effective, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)
    }

    private fun enqueue(call: Call, binary: Boolean, onDone: (Boolean, JSONObject) -> Unit) {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                engine.post {
                    onDone(false, JSONObject().put("error", e.message ?: "request failed"))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val payload = JSONObject()
                    val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
                    val headerObj = JSONObject()
                    for (i in 0 until resp.headers.size) {
                        // 对齐 RN fetch：响应 header 键统一小写
                        headerObj.put(resp.headers.name(i).lowercase(), resp.headers.value(i))
                    }
                    payload.put("headers", headerObj)
                    payload.put("contentType", resp.body?.contentType()?.toString() ?: "")
                    if (binary) {
                        val bodyB64 = Base64.encodeToString(bodyBytes, Base64.NO_WRAP)
                        payload.put("bodyB64", bodyB64)
                        payload.put("bodySize", bodyBytes.size)
                        payload.put("body", bodyB64)
                    } else {
                        val text = String(bodyBytes, Charsets.UTF_8)
                        payload.put("bodyText", text)
                        payload.put("bodySize", bodyBytes.size)
                        // 对齐 lx fetchData：非二进制响应尝试 JSON.parse，
                        // 脚本拿到的是对象（与 lx 一致）；解析失败保持字符串
                        val trimmed = text.trim()
                        var bodyValue: Any = text
                        if (trimmed.startsWith("{")) {
                            runCatching { JSONObject(text) }.onSuccess { bodyValue = it }
                        } else if (trimmed.startsWith("[")) {
                            runCatching { org.json.JSONArray(text) }.onSuccess { bodyValue = it }
                        }
                        payload.put("body", bodyValue)
                    }
                    // fetch 结构字段
                    payload.put("statusCode", resp.code)
                    payload.put("ok", resp.isSuccessful)
                    payload.put("url", resp.request.url.toString())
                    // request 结构字段
                    payload.put("statusMessage", resp.message)
                    engine.post { onDone(true, payload) }
                }
            }
        })
    }
}
