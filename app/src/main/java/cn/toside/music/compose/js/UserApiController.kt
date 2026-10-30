package cn.toside.music.compose.js

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/**
 * 用户音源脚本总控：串起两个沙箱与 HTTP 桥。
 *
 *  - 内置 SDK 沙箱负责 search / 取 URL / 歌词 / 封面（kw/kg/tx/wy/mg/xm）
 *  - 用户脚本沙箱负责实际播放取 URL / 歌词 / 封面（发货版已移除内置取 URL 接口）
 *  - activeApi = '' 时 SDK 抛 "Api is not found"（无可用播放源）
 *  - activeApi = 'user_api_<id>' 时 SDK 经 __lx_user_api_call__ 转发到本控制器
 */
class UserApiController(
    /** 脚本注释头 meta 解析（新模板无 lx_setup 时的回退），由上层注入 */
    private val userScriptMetaProvider: (String) -> JSONObject? = { null },
) {

    val engine = JsEngineThread()
    private val http = ScriptHttp(engine)

    @Volatile
    private var activeApiId: String = ""

    private val _initState = MutableStateFlow<UserApiInitState>(UserApiInitState.Idle)
    val initState: StateFlow<UserApiInitState> = _initState.asStateFlow()

    private val userListener = object : UserApiRuntime.Listener {
        override fun onInit(infoJson: String?, error: String?) {
            if (error != null) {
                _initState.value = UserApiInitState.Failed(error)
            } else {
                val info = infoJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                _initState.value = UserApiInitState.Ready(
                    sourceInfo = info,
                    scriptName = currentScriptName,
                )
            }
        }

        override fun onScriptResponse(requestKey: String, ok: Boolean, resultJson: String?, error: String?) {
            // 由 handleUserApiCall 内部的 deferred 消费，这里仅作日志面
        }

        override fun onUpdateAlert(log: String, updateUrl: String?) {}
    }

    private val userRuntime = UserApiRuntime(engine, http, userListener)

    private val builtinRuntime = BuiltinSdkRuntime(
        engine = engine,
        http = http,
        activeApiProvider = { if (activeApiId.isEmpty()) "" else "user_api_$activeApiId" },
        userApiCallHandler = ::handleUserApiCall,
    )

    @Volatile
    private var currentScriptName: String = ""

    @Volatile
    private var currentScriptId: String = ""

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        userRuntime.start()
        builtinRuntime.start()
    }

    fun shutdown() {
        scope.cancel()
        userRuntime.shutdown()
        builtinRuntime.shutdown()
        engine.quitEngine()
    }

    /** 设置当前播放 api 源（null/'' → 无源） */
    fun setActiveApi(scriptId: String?) {
        activeApiId = scriptId?.takeIf { it.isNotEmpty() } ?: ""
    }

    fun activeApi(): String = activeApiId

    /** JS 双沙箱是否就绪（启动为异步，UI 调用前先检查） */
    fun isReady(): Boolean = userRuntime.ready && builtinRuntime.ready

    /**
     * 加载用户脚本。执行顺序：销毁旧 user context → 重建 → evaluate(preload) →
     * evaluate(用户脚本)（新模板直接解构 globalThis.lx；旧模板调 lx_setup）→
     * 读 currentScriptInfo 拿 meta → 等脚本自发 send('inited')（可选）。
     * 若脚本未自发 init，request 事件链路依然可用，只是 UI 拿不到音质清单。
     */
    fun loadUserScript(id: String, scriptText: String) {
        _initState.value = UserApiInitState.Loading
        userRuntime.clearScriptCallbacks()
        userRuntime.reloadScript(scriptText)
        currentScriptId = id
        val metaJson = userRuntime.evalMeta()
        val meta = metaJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        // 新模板无 lx_setup → currentScriptInfo 为空，回退到脚本注释头 meta
        val commentMeta = userScriptMetaProvider(scriptText)
        currentScriptName = meta?.optString("name")?.takeIf { it.isNotEmpty() }
            ?: commentMeta?.optString("name")?.takeIf { it.isNotEmpty() }
            ?: ""
        _initState.value = UserApiInitState.Ready(
            sourceInfo = null,
            scriptName = currentScriptName.ifEmpty { "未命名脚本" },
        )
    }

    // ── SDK 直连能力（搜索） ──────────────────────────────────────

    fun search(source: String, keyword: String, page: Int, limit: Int): String =
        builtinRuntime.call(
            "search",
            JSONObject().apply {
                put("source", source)
                put("keyword", keyword)
                put("page", page)
                put("limit", limit)
            }.toString()
        )

    /** 换源重试：传入 musicInfo（含 source 字段） */
    fun findMusic(musicInfoJson: String): String =
        builtinRuntime.call("findMusic", musicInfoJson)

    /** 平台榜单列表（kw/kg/tx/wy/mg），返回 { list: [{id, name, bangid}], source } */
    fun getBoards(source: String): String =
        builtinRuntime.call(
            "getBoards",
            JSONObject().apply {
                put("source", source)
            }.toString()
        )

    /** 榜内歌曲分页，id 为榜单 bangid，返回 { total, list, limit, page, source } */
    fun boardList(source: String, bangid: String, page: Int): String =
        builtinRuntime.call(
            "boardList",
            JSONObject().apply {
                put("source", source)
                put("id", bangid)
                put("page", page)
            }.toString()
        )

    /** 歌单排序方式（静态数据），返回 [{name, id, tid}] */
    fun songListSorts(source: String): String =
        builtinRuntime.call(
            "songListSorts",
            JSONObject().apply { put("source", source) }.toString()
        )

    /** 歌单标签，返回 { tags: [{name, list: [{id, name, ...}]}], hotTag: [...], source } */
    fun songListTags(source: String): String =
        builtinRuntime.call(
            "songListTags",
            JSONObject().apply { put("source", source) }.toString()
        )

    /** 歌单列表分页，返回 { list: [{id, name, img, author, play_count, ...}], total, page, limit } */
    fun songList(source: String, sortId: String, tagId: String, page: Int): String =
        builtinRuntime.call(
            "songList",
            JSONObject().apply {
                put("source", source)
                put("sortId", sortId)
                put("tagId", tagId)
                put("page", page)
            }.toString()
        )

    /** 歌单内歌曲分页，返回 { list, total, page, limit, info: {name, img, ...} } */
    fun songListDetail(source: String, id: String, page: Int): String =
        builtinRuntime.call(
            "songListDetail",
            JSONObject().apply {
                put("source", source)
                put("id", id)
                put("page", page)
            }.toString()
        )

    // ── 用户脚本沙箱能力（取 URL / 歌词 / 封面） ──────────────────

    fun musicUrl(source: String, musicInfoJson: String, type: String): String =
        builtinRuntime.call(
            "musicUrl",
            JSONObject().apply {
                put("source", source)
                put("musicInfo", JSONObject(musicInfoJson))
                put("type", type)
            }.toString()
        )

    fun lyric(source: String, musicInfoJson: String): String =
        builtinRuntime.call(
            "lyric",
            JSONObject().apply {
                put("source", source)
                put("musicInfo", JSONObject(musicInfoJson))
            }.toString()
        )

    fun pic(source: String, musicInfoJson: String): String =
        builtinRuntime.call(
            "pic",
            JSONObject().apply {
                put("source", source)
                put("musicInfo", JSONObject(musicInfoJson))
            }.toString()
        )

    /** MV 播放直链（wy/kg 内置直连，不依赖用户脚本），返回 { url } */
    fun mvUrl(source: String, mvId: String): String =
        builtinRuntime.call(
            "mvUrl",
            JSONObject().apply {
                put("source", source)
                put("mvId", mvId)
            }.toString()
        )

    /** 单曲 MV 信息补全（wy/kg，mobile listMvRefresh 同款），返回 { mv }（无 MV 时 mv 为空串） */
    fun mvInfo(source: String, musicInfoJson: String): String =
        builtinRuntime.call(
            "mvInfo",
            JSONObject().apply {
                put("source", source)
                put("musicInfo", JSONObject(musicInfoJson))
            }.toString()
        )

    // ── builtin → user 沙箱转发 ───────────────────────────────────

    private fun handleUserApiCall(
        callId: Int,
        action: String,
        dataJson: String?,
        onComplete: (dataText: String) -> Unit,
        onFail: (Throwable) -> Unit,
    ) {
        val scriptResult = CompletableDeferred<String>()
        val data = dataJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

        val requestKey = UUID.randomUUID().toString()
        userRuntime.registerScriptCallback(requestKey, scriptResult)

        // builtin data { source, type, musicInfo } → user request info
        // musicUrl → info = { type, musicInfo }；lyric/pic → info = musicInfo
        val info = JSONObject().apply {
            put("source", data.optString("source"))
            put("action", action)
            when (action) {
                "musicUrl" -> put(
                    "info",
                    JSONObject().apply {
                        put("type", data.optString("type"))
                        put("musicInfo", data.opt("musicInfo") ?: JSONObject.NULL)
                    }
                )
                else -> put("info", data.opt("musicInfo") ?: JSONObject.NULL)
            }
        }
        userRuntime.dispatchRequest(requestKey, info.toString())

        scope.launch {
            try {
                // 脚本结果经 preload 校验为 { source, action, data }，取 data 上抛。
                // data 可能是对象 / 裸字符串 / null，保留原始 JSON 文本嵌入
                val resultJson = scriptResult.await()
                val result = runCatching { JSONObject(resultJson) }.getOrNull()
                val v = result?.opt("data")
                val dataText = when (v) {
                    null, JSONObject.NULL -> "null"
                    is String -> org.json.JSONObject.quote(v)
                    else -> v.toString()
                }
                onComplete(dataText)
            } catch (e: Throwable) {
                onFail(e)
            }
        }
    }
}

sealed interface UserApiInitState {
    object Idle : UserApiInitState
    object Loading : UserApiInitState
    data class Ready(val sourceInfo: JSONObject?, val scriptName: String) : UserApiInitState
    data class Failed(val error: String) : UserApiInitState
}
