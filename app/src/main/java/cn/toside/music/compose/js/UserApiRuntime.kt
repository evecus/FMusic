package cn.toside.music.compose.js

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 用户音源脚本沙箱（自定义音源 js）。
 *
 * 执行顺序（都在 [JsEngineThread]）：
 *   1. 创建 QuickJSContext
 *   2. 注入 Kotlin 函数：__lx_native_call__ + 6 个 utils（user-api-preload 契约）
 *   3. evaluate(user-api-preload.js)  → 定义 globalThis.lx / lx_setup
 *   4. evaluate(用户脚本源码)           → 脚本调 lx_setup(...) 完成注册
 *
 * JS → 原生（__lx_native_call__(key, action, dataJson)）：
 *   request / cancelRequest / response / init / showUpdateAlert
 * JS → 原生（独立函数）：__lx_native_call__set_timeout / utils_*
 * 原生 → JS（globalThis.__lx_native__(key, action, dataJson)）：
 *   request（触发脚本 request 事件）/ response（HTTP 结果回传）/ __set_timeout__
 */
class UserApiRuntime(
    private val engine: JsEngineThread,
    private val http: ScriptHttp,
    private val listener: Listener,
) {
    interface Listener {
        /** init 回调：脚本 sourceInfo 注册完成（或失败） */
        fun onInit(infoJson: String?, error: String?)
        /** HTTP 响应回传脚本 request 处理器 */
        fun onScriptResponse(requestKey: String, ok: Boolean, resultJson: String?, error: String?)
        fun onUpdateAlert(log: String, updateUrl: String?)
    }

    private var ctx: QuickJSContext? = null
    private val key: String = UUID.randomUUID().toString()
    private val pendingTimers = ConcurrentHashMap<Int, Runnable>()
    private val scriptCallbacks = ConcurrentHashMap<String, CompletableDeferred<String>>()

    @Volatile
    var ready: Boolean = false
        private set

    private fun createContext(): QuickJSContext {
        val context = QuickJSContext.create()
        // QuickJS 默认无 console 对象：preload 与脚本内的 console.log 会直接抛
        // ReferenceError 中断执行。initConsoleLog 挂上 console 并输出到 logcat。
        runCatching { com.whl.quickjs.android.QuickJSLoader.initConsoleLog(context, "lx-user-api") }
        return context
    }

    fun start() {
        engine.runBlocking(60_000) {
            val context = createContext()
            ctx = context
            injectFunctions(context)
            context.evaluate(Assets.read("script/user-api-preload.js"), "user-api-preload.js")
            ready = true
        }
    }

    private fun injectFunctions(context: QuickJSContext) {
        val global = context.globalObject
        // 沙箱密钥：preload 顶层读取后即删，所有 __lx_native_call__ 均须携带
        global.setProperty("__lx_user_key__", key)
        global.setProperty("__lx_native_call__", JSCallFunction { args ->
            handleNativeCall(args)
            null
        })
            // ── user-api-preload 的 6 个原生函数 ──
            global.setProperty("__lx_native_call__set_timeout", JSCallFunction { args ->
                val id = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
                val runnable = Runnable {
                    pendingTimers.remove(id)
                    dispatch("__set_timeout__", JSONObject().put("id", id).toString())
                }
                pendingTimers[id] = runnable
                engine.postDelayed(runnable, if (ms < 0) 0 else ms)
                null
            })
            global.setProperty("__lx_native_call__utils_str2b64", JSCallFunction { args ->
                Crypto.b64Encode(s(args, 0).toByteArray(Charsets.UTF_8))
            })
            global.setProperty("__lx_native_call__utils_b642buf", JSCallFunction { args ->
                bytesToJsonArray(Crypto.b64Decode(s(args, 0))).toString()
            })
            global.setProperty("__lx_native_call__utils_str2md5", JSCallFunction { args ->
                Crypto.md5Hex(decodeUri(s(args, 0)))
            })
            global.setProperty("__lx_native_call__utils_aes_encrypt", JSCallFunction { args ->
                Crypto.aesEncrypt(s(args, 3), s(args, 0), s(args, 1), s(args, 2))
            })
            global.setProperty("__lx_native_call__utils_rsa_encrypt", JSCallFunction { args ->
                Crypto.rsaEncrypt(s(args, 0), s(args, 1), s(args, 2))
            })
    }

    /**
     * 重新加载用户脚本。lx_setup 是一次性的（脚本内 delete globalThis.lx_setup），
     * 重复加载必须先销毁旧 context 再重建。
     */
    fun reloadScript(scriptText: String) {
        engine.runBlocking(60_000) {
            runCatching { ctx?.destroy() }
            ctx = null
            ready = false
            pendingTimers.values.forEach { engine.handler.removeCallbacks(it) }
            pendingTimers.clear()
            val context = createContext()
            ctx = context
            injectFunctions(context)
            context.evaluate(Assets.read("script/user-api-preload.js"), "user-api-preload.js")
            ready = true
            try {
                context.evaluate(scriptText, "user-script.js")
            } catch (e: Throwable) {
                listener.onInit(null, e.message ?: "script error")
            }
        }
    }

    /** 读取脚本 meta（globalThis.lx.currentScriptInfo，由 lx_setup 写入） */
    fun evalMeta(): String? {
        var result: String? = null
        engine.runBlocking(10_000) {
            val context = ctx ?: return@runBlocking
            result = runCatching {
                context.evaluate(
                    "JSON.stringify(globalThis.lx && globalThis.lx.currentScriptInfo ? globalThis.lx.currentScriptInfo : null)",
                    "meta"
                ) as? String
            }.getOrNull()
        }
        return result
    }

    /** native → JS：触发脚本 request 事件处理器 */
    fun dispatchRequest(requestKey: String, dataJson: String) {
        engine.post {
            dispatch("request", JSONObject().apply {
                put("requestKey", requestKey)
                put("data", JSONObject(dataJson))
            }.toString())
        }
    }

    /** native → JS：回传 HTTP 结果给 preload（handleNativeResponse） */
    fun dispatchResponse(resultJson: String) {
        engine.post { dispatch("response", resultJson) }
    }

    /** native → JS：通知脚本运行错误（清掉 inited 锁） */
    fun notifyRunError() {
        engine.post { dispatch("__run_error__", null) }
    }

    fun registerScriptCallback(requestKey: String, deferred: CompletableDeferred<String>) {
        scriptCallbacks[requestKey] = deferred
    }

    fun clearScriptCallbacks() {
        scriptCallbacks.values.forEach { it.completeExceptionally(RuntimeException("cancelled")) }
        scriptCallbacks.clear()
    }

    fun shutdown() {
        engine.runBlocking(10_000) {
            runCatching { ctx?.destroy() }
            ctx = null
            ready = false
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private fun handleNativeCall(args: Array<out Any?>) {
        val incomingKey = s(args, 0)
        if (incomingKey != key) return
        when (val action = s(args, 1)) {
            "request" -> {
                val data = o(args, 2)
                val requestKey = data.optString("requestKey")
                http.request(requestKey, data.optString("url"), data.optJSONObject("options")?.toString()) { resultJson ->
                    // ScriptHttp 结果 { requestKey, ok, data|error } → preload 期望
                    // { requestKey, error, response }（error==null 时回调 response）
                    val r = JSONObject(resultJson)
                    val mapped = JSONObject().apply {
                        put("requestKey", requestKey)
                        if (r.optBoolean("ok")) {
                            put("error", JSONObject.NULL)
                            put("response", r.optJSONObject("data"))
                        } else {
                            put("error", r.optString("error"))
                        }
                    }
                    dispatchResponse(mapped.toString())
                }
            }
            "cancelRequest" -> {
                http.cancelRequest(o(args, 2).optString("requestKey"))
            }
            "response" -> {
                val data = o(args, 2)
                val requestKey = data.optString("requestKey")
                val deferred = scriptCallbacks.remove(requestKey)
                if (data.optBoolean("status")) {
                    deferred?.complete(data.optString("result") ?: "null")
                } else {
                    deferred?.completeExceptionally(
                        RuntimeException(data.optString("errorMessage").ifEmpty { "request failed" })
                    )
                }
                listener.onScriptResponse(
                    requestKey,
                    data.optBoolean("status"),
                    data.optString("result").takeIf { it.isNotEmpty() },
                    data.optString("errorMessage").takeIf { it.isNotEmpty() },
                )
            }
            "init" -> {
                val data = o(args, 2)
                if (data.optBoolean("status")) {
                    listener.onInit(data.optJSONObject("info")?.toString(), null)
                } else {
                    listener.onInit(null, data.optString("errorMessage").ifEmpty { "init failed" })
                }
            }
            "showUpdateAlert" -> {
                val data = o(args, 2)
                listener.onUpdateAlert(data.optString("log"), data.optString("updateUrl").ifEmpty { null })
            }
        }
    }

    private fun dispatch(action: String, dataJson: String?) {
        val context = ctx ?: return
        try {
            context.evaluate(
                "globalThis.__lx_native__(${q(key)}, ${q(action)}, ${q(dataJson)})",
                "user-native-dispatch"
            )
        } catch (_: Throwable) {
        }
    }

    private fun s(args: Array<out Any?>, i: Int): String = args.getOrNull(i)?.toString() ?: ""

    private fun o(args: Array<out Any?>, i: Int): JSONObject =
        runCatching { JSONObject(s(args, i)) }.getOrNull() ?: JSONObject()

    private fun q(s: String?): String = if (s == null) "null" else JSONObject.quote(s)

    private fun decodeUri(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun bytesToJsonArray(bytes: ByteArray): org.json.JSONArray =
        org.json.JSONArray().apply { bytes.forEach { put(it.toInt() and 0xFF) } }
}
