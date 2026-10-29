package cn.toside.music.compose.js

import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 内置音源 SDK 沙箱（kw/kg/tx/wy/mg/xm）。
 *
 * 执行顺序（都在 [JsEngineThread]）：
 *   1. 创建 QuickJSContext
 *   2. 注入 Kotlin 侧函数（见 [BuiltinSdkRuntime] 头注）
 *   3. evaluate(builtin-sdk-preload.js)  → 提供 fetch/global/Buffer 兼容环境
 *   4. evaluate(music-sdk.js)            → 设置 globalThis.__lx_builtin_sdk
 *
 * JS → 原生 action：
 *   fetch / fetch_abort / userApiCall / set_timeout / clear_timeout / __lx_result__
 * 原生 → JS action（经 evaluate 调 globalThis.__lx_native__）：
 *   fetch_result / userApi_result / set_timeout
 */
class BuiltinSdkRuntime(
    private val engine: JsEngineThread,
    private val http: ScriptHttp,
    private val activeApiProvider: () -> String,
    private val userApiCallHandler: (
        callId: Int,
        action: String,
        dataJson: String?,
        onComplete: (dataText: String) -> Unit,
        onFail: (Throwable) -> Unit,
    ) -> Unit,
) {
    private var ctx: QuickJSContext? = null
    private val key: String = UUID.randomUUID().toString()
    private var builtinCallSeq = 0
    private val pendingCalls = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val pendingTimers = ConcurrentHashMap<Int, Runnable>()

    @Volatile
    var ready: Boolean = false
        private set

    private companion object {
        const val CALL_TIMEOUT_MS = 90_000L
    }

    fun start() {
        engine.runBlocking(60_000) {
            val context = QuickJSContext.create()
            // QuickJS 默认无 console 对象（SDK 内部与脚本均使用 console.log）
            runCatching { com.whl.quickjs.android.QuickJSLoader.initConsoleLog(context, "lx-sdk") }
            ctx = context
            val global = context.globalObject

            global.setProperty("__lx_builtin_key__", key)
            global.setProperty("__lx_native_call__", JSCallFunction { args ->
                handleNativeCall(args)
                null
            })
            global.setProperty("__lx_active_api__", JSCallFunction {
                activeApiProvider()
            })
            // 加密 / 编码原生函数（builtin-sdk-preload 契约）
            // 注意：builtin 沙箱的 aes 调用契约是 (data, key, iv, mode)，
            // 与 user 沙箱一致；Crypto.aesXxx 签名为 (mode, data, key, iv)，此处做映射
            global.setProperty("__lx_native_call__utils_b64md5", JSCallFunction { args ->
                Crypto.md5B64Hex(str(args, 0))
            })
            global.setProperty("__lx_native_call__utils_str2md5", JSCallFunction { args ->
                Crypto.md5Hex(decodeUri(str(args, 0)))
            })
            global.setProperty("__lx_native_call__utils_sha1", JSCallFunction { args ->
                Crypto.sha1Hex(str(args, 0))
            })
            global.setProperty("__lx_native_call__utils_aes_encrypt", JSCallFunction { args ->
                Crypto.aesEncrypt(str(args, 3), str(args, 0), str(args, 1), str(args, 2))
            })
            global.setProperty("__lx_native_call__utils_aes_decrypt", JSCallFunction { args ->
                Crypto.aesDecrypt(str(args, 3), str(args, 0), str(args, 1), str(args, 2))
            })
            global.setProperty("__lx_native_call__utils_rsa_encrypt", JSCallFunction { args ->
                Crypto.rsaEncrypt(str(args, 0), str(args, 1), str(args, 2).ifEmpty { "3" })
            })
            global.setProperty("__lx_native_call__utils_decode_charset", JSCallFunction { args ->
                Crypto.decodeCharset(str(args, 0), str(args, 1))
            })
            global.setProperty("__lx_native_call__utils_encode_charset", JSCallFunction { args ->
                Crypto.encodeCharset(str(args, 0), str(args, 1))
            })

            context.evaluate(Assets.read("script/builtin-sdk-preload.js"), "builtin-sdk-preload.js")
            context.evaluate(Assets.read("script/music-sdk.js"), "music-sdk.js")
            ready = true
        }
    }

    /** Kotlin → SDK 统一入口，返回结果 JSON 字符串（失败抛异常）。任意线程可调。 */
    fun call(method: String, argsJson: String?): String {
        val deferred = CompletableDeferred<String>()
        engine.post {
            val context = ctx ?: run {
                deferred.completeExceptionally(IllegalStateException("sdk not started"))
                return@post
            }
            val callId = ++builtinCallSeq
            pendingCalls[callId] = deferred
            try {
                context.evaluate(
                    "globalThis.__lx_builtin_call__(${q(method)}, ${q(argsJson)})",
                    "builtin-call"
                )
            } catch (e: Throwable) {
                pendingCalls.remove(callId)
                deferred.completeExceptionally(e)
            }
        }
        return runBlocking {
            withTimeoutOrNull(CALL_TIMEOUT_MS) { deferred.await() }
                ?: throw java.util.concurrent.TimeoutException("sdk call timeout: $method")
        }
    }

    fun shutdown() {
        engine.runBlocking(10_000) {
            runCatching { ctx?.destroy() }
            ctx = null
            ready = false
        }
    }

    // ── JS → 原生 ────────────────────────────────────────────────

    private fun handleNativeCall(args: Array<out Any?>) {
        val incomingKey = str(args, 0)
        if (incomingKey != key) return
        when (val action = str(args, 1)) {
            "fetch" -> {
                val data = obj(args, 2)
                http.fetch(
                    data.optInt("callId"),
                    data.optString("url"),
                    data.optJSONObject("options")?.toString()
                ) { resultJson ->
                    engine.post { dispatch("fetch_result", resultJson) }
                }
            }
            "fetch_abort" -> {
                http.fetchAbort(obj(args, 2).optInt("callId"))
            }
            "userApiCall" -> {
                val data = obj(args, 2)
                val callId = data.optInt("callId")
                val action2 = data.optString("action")
                // 转发到用户脚本沙箱；dataText 为 userApi_result 的 data（原始 JSON 文本，
                // 可能是对象 / 裸字符串 / null，不能过 JSONObject(String) 构造）
                userApiCallHandler(
                    callId,
                    action2,
                    data.optJSONObject("data")?.toString(),
                    { dataText ->
                        engine.post {
                            val json = buildString {
                                append("{\"callId\":").append(callId)
                                append(",\"ok\":true,\"data\":").append(dataText)
                                append("}")
                            }
                            dispatch("userApi_result", json)
                        }
                    },
                    { err ->
                        engine.post {
                            val json = buildString {
                                append("{\"callId\":").append(callId)
                                append(",\"ok\":false,\"error\":")
                                append(q(err.message ?: "user api failed"))
                                append("}")
                            }
                            dispatch("userApi_result", json)
                        }
                    },
                )
            }
            "set_timeout" -> {
                val data = obj(args, 2)
                val id = data.optInt("id")
                val delay = data.optLong("timeout", 0L)
                val runnable = Runnable {
                    pendingTimers.remove(id)
                    dispatch("set_timeout", JSONObject().put("id", id).toString())
                }
                pendingTimers[id] = runnable
                engine.postDelayed(runnable, if (delay < 0) 0 else delay)
            }
            "clear_timeout" -> {
                val id = obj(args, 2).optInt("id")
                pendingTimers.remove(id)?.let { engine.handler.removeCallbacks(it) }
            }
            "__lx_result__" -> {
                val data = obj(args, 2)
                val callId = data.optInt("callId")
                val deferred = pendingCalls.remove(callId) ?: return
                if (data.optBoolean("ok")) {
                    deferred.complete(data.optString("data") ?: "null")
                } else {
                    deferred.completeExceptionally(
                        RuntimeException(data.optString("error").ifEmpty { "sdk call failed" })
                    )
                }
            }
        }
    }

    // ── 原生 → JS ────────────────────────────────────────────────

    private fun dispatch(action: String, dataJson: String?) {
        val context = ctx ?: return
        try {
            context.evaluate(
                "globalThis.__lx_native__(${q(key)}, ${q(action)}, ${q(dataJson)})",
                "native-dispatch"
            )
        } catch (_: Throwable) {
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────

    private fun str(args: Array<out Any?>, i: Int): String = args.getOrNull(i)?.toString() ?: ""

    private fun obj(args: Array<out Any?>, i: Int): JSONObject =
        runCatching { JSONObject(str(args, i)) }.getOrNull() ?: JSONObject()

    private fun q(s: String?): String =
        if (s == null) "null" else JSONObject.quote(s)

    private fun decodeUri(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
