package com.music.fm.ui.download

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.music.fm.MusicApplication
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.MusicUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class MvUiState(
    val loading: Boolean = false,
    val url: String? = null,
    val title: String = "",
)

/** MV 播放：取直链后弹出全屏播放器 */
class MvViewModel(app: Application) : AndroidViewModel(app) {

    private val jsController = (app as MusicApplication).graph.jsController
    private val playerRepository = (app as MusicApplication).graph.playerRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _uiState = MutableStateFlow(MvUiState())
    val uiState: StateFlow<MvUiState> = _uiState.asStateFlow()

    fun load(info: MusicInfo) {
        if (info.source != "wy" && info.source != "kg") {
            toast("该来源暂不支持MV")
            return
        }
        val mvId = info.mv
        if (mvId.isNullOrEmpty() || (info.source == "wy" && mvId == "0")) {
            toast("该歌曲没有MV")
            return
        }
        // 播放 MV 前暂停当前歌曲，避免歌曲与 MV 同时出声
        playerRepository.pause()
        _uiState.value = MvUiState(loading = true, title = "${info.name} - ${info.singer}")
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { jsController.mvUrl(info.source, mvId) }
                val url = MusicUrl.parse(json)?.url
                    ?: throw IllegalStateException("获取MV链接失败")
                _uiState.value = MvUiState(loading = false, url = url, title = "${info.name} - ${info.singer}")
            } catch (e: Throwable) {
                _uiState.value = MvUiState()
                toast(e.message ?: "获取MV链接失败")
            }
        }
    }

    fun dismiss() {
        _uiState.value = MvUiState()
    }

    private fun toast(msg: String) {
        mainHandler.post { Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MvViewModel(this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}
