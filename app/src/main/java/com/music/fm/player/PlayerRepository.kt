package com.music.fm.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.fm.data.SettingsRepository
import com.music.fm.data.model.LyricInfo
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.MusicUrl
import com.music.fm.data.model.Qualitys
import com.music.fm.js.UserApiController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class PlayMode { LIST_LOOP, SHUFFLE, SINGLE_LOOP }

data class PlayerUiState(
    val current: MusicInfo? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playMode: PlayMode = PlayMode.LIST_LOOP,
    val queue: List<MusicInfo> = emptyList(),
    val currentIndex: Int = -1,
    val loading: Boolean = false,
    val error: String? = null,
    /** 状态提示（如「尝试切换到其他来源...」，对齐 lx setStatusText） */
    val statusText: String? = null,
    val quality: String = Qualitys.Q128,
)

/**
 * 播放仓库：MediaController 连接 PlaybackService，管理播放队列、
 * 取 URL 播放（经 [UserApiController]）、播放模式与进度轮询。
 */
class PlayerRepository(
    private val context: Context,
    private val jsController: UserApiController,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null

    private val queue = mutableListOf<MusicInfo>()
    private var currentIndex = -1

    /** 换源进行中标记：防止取 URL 失败与 Media3 播放错误回调重复触发 */
    @Volatile
    private var togglingSource = false

    /** 播放请求序号：切歌后旧请求的异步结果一律丢弃（对齐 lx diffCurrentMusicInfo） */
    @Volatile
    private var playToken = 0L

    /** 已自动换源过的歌曲 key：同一首歌最多自动换源一次，避免错误循环 */
    @Volatile
    private var lastToggledKey: String? = null

    init {
        connectController()
        scope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.value = _uiState.value.copy(
                quality = settings.defaultQuality,
                playMode = _uiState.value.playMode,
            )
        }
        scope.launch { pollProgress() }
    }

    private fun connectController() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        // 回调必须在应用主线程：MediaController 方法非主线程调用会抛 IllegalStateException
        future.addListener({
            try {
                controller = future.get().also { it.addListener(playerListener) }
            } catch (e: Exception) {
                android.util.Log.w("PlayerRepository", "media controller connect failed", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onTrackEnded()
            syncState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // 暂停时不再轮询，seek 后需要主动刷新一次进度
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.value = _uiState.value.copy(error = error.message ?: "播放失败")
            // 播放中 URL 失效（403/下线/网络断）也触发自动换源；同一首只换一次
            val cur = _uiState.value.current
            if (cur != null && !togglingSource && lastToggledKey != cur.uniqueKey) {
                lastToggledKey = cur.uniqueKey
                playWithToggleSource(cur, _uiState.value.quality)
            }
            syncState()
        }
    }

    // ── 播放控制 ──────────────────────────────────────────────────

    /** 播放列表（点击某首时传入索引） */
    fun playList(list: List<MusicInfo>, index: Int) {
        if (list.isEmpty()) return
        queue.clear()
        queue.addAll(list)
        currentIndex = index.coerceIn(list.indices)
        playCurrent()
    }

    fun togglePlay() {
        scope.launch(Dispatchers.Main.immediate) {
            val c = controller ?: return@launch
            if (c.isPlaying) c.pause() else {
                if (c.mediaItemCount == 0) playCurrent() else c.play()
            }
        }
    }

    /** 暂停当前歌曲（播放 MV 前调用，避免歌曲与 MV 同时出声） */
    fun pause() {
        scope.launch(Dispatchers.Main.immediate) {
            controller?.pause()
        }
    }

    /**
     * 停止播放并释放播放服务（TV 连按两次返回键退出应用时用）：
     * 停止 MediaController、清空队列并停掉 PlaybackService，确保不再后台播放。
     */
    fun stopPlayback() {
        scope.launch(Dispatchers.Main.immediate) {
            controller?.let { c ->
                runCatching {
                    c.stop()
                    c.clearMediaItems()
                    c.release()
                }
            }
            controller = null
            queue.clear()
            currentIndex = -1
            _uiState.value = _uiState.value.copy(
                current = null,
                queue = emptyList(),
                isPlaying = false,
                loading = false,
                positionMs = 0,
                durationMs = 0,
            )
            runCatching {
                context.stopService(android.content.Intent(context, PlaybackService::class.java))
            }
        }
    }

    fun next() {
        if (queue.isEmpty()) return
        when (_uiState.value.playMode) {
            PlayMode.SINGLE_LOOP -> playCurrent()
            PlayMode.SHUFFLE -> {
                currentIndex = if (queue.size == 1) 0 else (queue.indices - currentIndex).random()
                playCurrent()
            }
            PlayMode.LIST_LOOP -> {
                currentIndex = (currentIndex + 1) % queue.size
                playCurrent()
            }
        }
    }

    fun previous() {
        if (queue.isEmpty()) return
        scope.launch(Dispatchers.Main.immediate) {
            val c = controller
            if (c != null && c.currentPosition > 3000) {
                c.seekTo(0)
            } else {
                currentIndex = if (currentIndex - 1 < 0) queue.size - 1 else currentIndex - 1
                playCurrent()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            controller?.seekTo(positionMs.coerceAtLeast(0))
        }
    }

    fun setPlayMode(mode: PlayMode) {
        _uiState.value = _uiState.value.copy(playMode = mode)
    }

    fun cyclePlayMode() {
        val next = when (_uiState.value.playMode) {
            PlayMode.LIST_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.LIST_LOOP
        }
        setPlayMode(next)
    }

    fun setQuality(quality: String) {
        scope.launch { settingsRepository.setDefaultQuality(quality) }
        _uiState.value = _uiState.value.copy(quality = quality)
        // 重新取当前曲目 URL 生效
        if (currentIndex in queue.indices) playCurrent()
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private fun onTrackEnded() {
        next()
    }

    private fun playCurrent() {
        val info = queue.getOrNull(currentIndex) ?: return
        lastToggledKey = null
        togglingSource = false
        val token = ++playToken
        _uiState.value = _uiState.value.copy(
            current = info,
            loading = true,
            error = null,
            statusText = null,
            queue = queue.toList(),
            currentIndex = currentIndex,
        )
        scope.launch {
            val quality = _uiState.value.quality
            try {
                val musicUrl = fetchMusicUrl(info, quality)
                if (token != playToken) return@launch
                startPlayback(info, musicUrl)
                _uiState.value = _uiState.value.copy(loading = false)
            } catch (e: Throwable) {
                if (token != playToken) return@launch
                // 自动换源（对齐 lx-music）：取播放地址失败后到其它音乐平台找同首歌重试
                playWithToggleSource(info, quality, token)
            }
        }
    }

    private suspend fun fetchMusicUrl(info: MusicInfo, quality: String): MusicUrl {
        val urlJson = withContext(Dispatchers.IO) {
            jsController.musicUrl(info.source, info.toJson(), quality)
        }
        val musicUrl = MusicUrl.parse(urlJson)
        if (musicUrl == null || musicUrl.url.isEmpty()) {
            throw RuntimeException("获取播放地址失败")
        }
        return musicUrl
    }

    private fun startPlayback(info: MusicInfo, url: MusicUrl) {
        val item = buildMediaItem(info, url)
        val c = controller
        scope.launch(Dispatchers.Main) {
            c?.setMediaItem(item)
            c?.prepare()
            c?.play()
        }
    }

    // ── 播放失败自动换源（移植 lx-music handleGetOnlineMusicUrl + getOnlineOtherSourceMusicUrl）──

    /**
     * 换源重试流程：
     *  1. findMusic 用「歌名 + 歌手」到其它平台搜索同首歌（SDK 内置 lx 同款匹配排序）
     *  2. 按匹配度逐个尝试，每个平台只试一次（对齐 retryedSource 去重）
     *  3. 成功 → 当前播放信息换成新平台的歌（队列条目不变，对齐 lx onToggleSource）
     *  4. 全部失败 → 提示手动切歌
     */
    private fun playWithToggleSource(failed: MusicInfo, quality: String, token: Long = playToken) {
        if (togglingSource) return
        togglingSource = true
        _uiState.value = _uiState.value.copy(
            loading = true,
            error = null,
            statusText = "尝试切换到其他来源...",
        )
        scope.launch {
            try {
                val candidatesJson = withContext(Dispatchers.IO) {
                    jsController.findMusic(failed.toJson())
                }
                if (token != playToken) return@launch
                val candidates = parseMusicInfoArray(candidatesJson)
                val triedSources = mutableSetOf(failed.source)
                for (candidate in candidates) {
                    if (token != playToken) return@launch
                    if (candidate.source in triedSources) continue
                    triedSources.add(candidate.source)
                    // 对齐 lx getPlayQuality：候选平台不支持目标音质时降级
                    for (q in candidateQualities(candidate, quality)) {
                        try {
                            val url = fetchMusicUrl(candidate, q)
                            if (token != playToken) return@launch
                            // 换源成功：标记已换过，避免新地址再失败时与 onPlayerError 循环
                            lastToggledKey = candidate.uniqueKey
                            _uiState.value = _uiState.value.copy(
                                current = candidate,
                                loading = false,
                                error = null,
                                statusText = null,
                            )
                            startPlayback(candidate, url)
                            return@launch
                        } catch (_: Throwable) {
                            // 该音质取址失败，降级重试 / 继续下一个候选平台
                        }
                    }
                }
                if (token == playToken) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "自动换源失败，请手动切换其他歌曲",
                        statusText = null,
                    )
                }
            } catch (_: Throwable) {
                if (token == playToken) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "自动换源失败，请手动切换其他歌曲",
                        statusText = null,
                    )
                }
            } finally {
                if (token == playToken) togglingSource = false
            }
        }
    }

    /**
     * 候选歌曲的取址音质顺序（对齐 lx getPlayQuality）：
     * 目标音质 → 候选平台支持的更低高品质 → 128k 兜底，去重。
     */
    private fun candidateQualities(candidate: MusicInfo, wanted: String): List<String> {
        val tryList = listOf(Qualitys.FLAC24, Qualitys.FLAC, Qualitys.Q320)
        val supported = (candidate.raw["_types"] as? JsonObject)?.keys ?: emptySet()
        val picked = if (wanted in tryList) {
            tryList.drop(tryList.indexOf(wanted)).firstOrNull { it in supported } ?: Qualitys.Q128
        } else {
            wanted
        }
        return listOf(picked, Qualitys.Q128).distinct()
    }

    /** 解析 findMusic 返回的候选歌曲数组 */
    private fun parseMusicInfoArray(json: String): List<MusicInfo> = runCatching {
        val el = Json.parseToJsonElement(json)
        (el as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { o -> MusicInfo.parse(o) } }
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun buildMediaItem(info: MusicInfo, url: MusicUrl): MediaItem {
        val extras = Bundle().apply {
            putString(EXTRA_MUSIC_JSON, info.toJson())
            putString(EXTRA_MUSIC_URL, url.url)
            putString(EXTRA_QUALITY, url.type.ifEmpty { _uiState.value.quality })
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(info.name)
            .setArtist(info.singer)
            .setAlbumTitle(info.albumName)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(info.uniqueKey)
            .setUri(url.url)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * 进度轮询。MediaController 的方法只能在应用主线程调用
     * （Media3 会 verifyApplicationThread，否则抛 IllegalStateException），
     * 因此读取部分整体切到 Main 线程执行。
     */
    private suspend fun pollProgress() {
        while (true) {
            // 暂停 / 无播放时挂起，不再每 500ms 唤醒主线程空转；
            // 状态变化由 playerListener → syncState 驱动，恢复播放时自动继续轮询。
            if (!_uiState.value.isPlaying) {
                _uiState.first { it.isPlaying }
            }
            withContext(Dispatchers.Main.immediate) {
                val c = controller
                if (c != null && _uiState.value.current != null) {
                    val duration = c.duration
                    if (duration > 0) {
                        _uiState.value = _uiState.value.copy(
                            positionMs = c.currentPosition,
                            durationMs = duration,
                            isPlaying = c.isPlaying,
                            isBuffering = c.playbackState == Player.STATE_BUFFERING,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isPlaying = c.isPlaying,
                            isBuffering = c.playbackState == Player.STATE_BUFFERING,
                        )
                    }
                }
            }
            delay(500)
        }
    }

    /** Player.Listener 回调本身在 Main 线程，直接读 controller 安全 */
    private fun syncState() {
        val c = controller ?: return
        _uiState.value = _uiState.value.copy(
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition,
            durationMs = c.duration,
        )
    }

    // ── 歌词 / 封面 ───────────────────────────────────────────────

    suspend fun loadLyric(info: MusicInfo): LyricInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val json = jsController.lyric(info.source, info.toJson())
            LyricInfo.parse(json)
        }.getOrNull()
    }

    suspend fun loadPic(info: MusicInfo): String? = withContext(Dispatchers.IO) {
        runCatching {
            val json = jsController.pic(info.source, info.toJson())
            // pic 结果为 url 字符串
            val el = Json.parseToJsonElement(json)
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> el["url"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }.getOrNull()
    }

    companion object {
        const val EXTRA_MUSIC_JSON = "lx_music_json"
        const val EXTRA_MUSIC_URL = "lx_music_url"
        const val EXTRA_QUALITY = "lx_quality"
    }
}
