package cn.toside.music.compose.ui.play

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.toside.music.compose.MusicApplication
import cn.toside.music.compose.data.model.LyricInfo
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.player.PlayerRepository
import cn.toside.music.compose.player.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayExtraState(
    val coverUrl: String? = null,
    val lyric: LyricInfo? = null,
    val lyricLoading: Boolean = false,
)

class PlayViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph
    val playerRepository: PlayerRepository = graph.playerRepository

    private val _extra = MutableStateFlow(PlayExtraState())
    val extra: StateFlow<PlayExtraState> = _extra.asStateFlow()

    init {
        viewModelScope.launch {
            playerRepository.uiState.collect { state ->
                val info = state.current
                if (info != null && info.uniqueKey != loadedKey) {
                    loadedKey = info.uniqueKey
                    loadExtras(info)
                }
            }
        }
    }

    private var loadedKey: String? = null

    private fun loadExtras(info: MusicInfo) {
        _extra.value = PlayExtraState(lyricLoading = true)
        viewModelScope.launch {
            // 封面与歌词并行加载：getPic 失败时回退到 musicInfo 自带的 img 字段
            val picDeferred = async { playerRepository.loadPic(info) }
            val lyricDeferred = async { playerRepository.loadLyric(info) }
            val pic = picDeferred.await()
            _extra.value = _extra.value.copy(coverUrl = pic ?: info.img)
            val lyric = lyricDeferred.await()
            _extra.value = _extra.value.copy(lyric = lyric, lyricLoading = false)
        }
    }

    fun togglePlay() = playerRepository.togglePlay()
    fun next() = playerRepository.next()
    fun previous() = playerRepository.previous()
    fun seekTo(ms: Long) = playerRepository.seekTo(ms)
    fun cyclePlayMode() = playerRepository.cyclePlayMode()
    fun consumeError() = playerRepository.consumeError()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication
                PlayViewModel(app)
            }
        }
    }
}
