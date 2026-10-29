package cn.toside.music.compose.js

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

    private fun buildCall(url: String, opts: JSONObject): Call {
        val method = opts.optString("method", "get").uppercase()
        val headers = opts.optJSONObject("headers") ?: JSONObject()
        val builder = Request.Builder().url(url)
        for (key in headers.keys()) {
            builder.addHeader(key, headers.optString(key))
        }

        var bodyStr: String? = null
        when {
            opts.has("body") -> {
                val b = opts.get("body")
                bodyStr = if (b is String) b else b.toString()
            }
            opts.has("form") -> {
                val form = opts.optJSONObject("form")
                if (form != null) {
                    bodyStr = form.keys().asSequence()
                        .map { "$it=${java.net.URLEncoder.encode(form.optString(it), "UTF-8")}" }
                        .joinToString("&")
                }
            }
        }
        val request = if (method == "GET" || method == "HEAD") {
            builder.method(method, null).build()
        } else {
            val contentType = headers.optString("Content-Type")
                .ifEmpty { headers.optString("content-type") }
                .ifEmpty { null }
            val media = contentType?.toMediaTypeOrNull()
            builder.method(method, (bodyStr ?: "").toRequestBody(media)).build()
        }
        val timeout = opts.optLong("timeout", 0)
        val effective = if (timeout in 1..60_000) timeout else 60_000
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
                        headerObj.put(resp.headers.name(i), resp.headers.value(i))
                    }
                    payload.put("headers", headerObj)
                    payload.put("contentType", resp.body?.contentType()?.toString() ?: "")
                    if (binary) {
                        payload.put("bodyB64", Base64.encodeToString(bodyBytes, Base64.NO_WRAP))
                        payload.put("bodySize", bodyBytes.size)
                    } else {
                        payload.put("bodyText", String(bodyBytes, Charsets.UTF_8))
                    }
                    // fetch 结构字段
                    payload.put("statusCode", resp.code)
                    payload.put("ok", resp.isSuccessful)
                    payload.put("url", resp.request.url.toString())
                    payload.put("bodySize", bodyBytes.size)
                    // request 结构字段
                    payload.put("statusMessage", resp.message)
                    payload.put("body", if (binary) payload.optString("bodyB64") else payload.optString("bodyText"))
                    engine.post { onDone(true, payload) }
                }
            }
        })
    }
}
