package cn.toside.music.compose.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cn.toside.music.compose.data.SettingsRepository
import cn.toside.music.compose.data.model.LyricInfo
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.MusicUrl
import cn.toside.music.compose.data.model.Qualitys
import cn.toside.music.compose.js.UserApiController
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
        _uiState.value = _uiState.value.copy(
            current = info,
            loading = true,
            error = null,
            queue = queue.toList(),
            currentIndex = currentIndex,
        )
        scope.launch {
            val quality = _uiState.value.quality
            try {
                val urlJson = withContext(Dispatchers.IO) {
                    jsController.musicUrl(info.source, info.toJson(), quality)
                }
                val musicUrl = MusicUrl.parse(urlJson)
                if (musicUrl == null || musicUrl.url.isEmpty()) {
                    throw RuntimeException("获取播放地址失败")
                }
                val item = buildMediaItem(info, musicUrl)
                val c = controller
                withContext(Dispatchers.Main) {
                    c?.setMediaItem(item)
                    c?.prepare()
                    c?.play()
                }
                _uiState.value = _uiState.value.copy(loading = false)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "播放失败",
                )
            }
        }
    }

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
