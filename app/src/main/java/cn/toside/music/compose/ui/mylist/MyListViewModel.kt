package cn.toside.music.compose.ui.mylist

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.toside.music.compose.MusicApplication
import cn.toside.music.compose.data.MyListMeta
import cn.toside.music.compose.data.MyListRepository
import cn.toside.music.compose.data.model.MusicInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MyListUiState(
    val lists: List<MyListMeta> = emptyList(),
    val activeListId: String = MyListRepository.DEFAULT_ID,
    val songs: List<MusicInfo> = emptyList(),
    val toast: String? = null,
)

/**
 * “收藏”页（对齐 mobile 版 Mylist）：
 * 左侧抽屉为歌单列表（试听列表 / 我的收藏 / 自建歌单），右侧为当前歌单的歌曲。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MyListViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as Application
                MyListViewModel(app)
            }
        }
    }

    private val graph = (app as MusicApplication).graph
    private val repo: MyListRepository = graph.myListRepository
    val playerRepository = graph.playerRepository
    private val jsController = graph.jsController
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 「重新导入MV」进行中标记（防重入） */
    private var refreshingMv = false

    private val _uiState = MutableStateFlow(MyListUiState())
    val uiState: StateFlow<MyListUiState> = _uiState.asStateFlow()

    /** 当前激活歌单 id（UI 可写），驱动歌曲流 */
    private val activeListId = MutableStateFlow(MyListRepository.DEFAULT_ID)

    init {
        // 歌单元数据：兜底修正失效的激活 id
        viewModelScope.launch {
            repo.lists.collect { lists ->
                val current = activeListId.value
                if (lists.none { it.id == current }) {
                    activeListId.value = MyListRepository.DEFAULT_ID
                }
                _uiState.value = _uiState.value.copy(
                    lists = lists,
                    activeListId = activeListId.value,
                )
            }
        }
        // 歌曲列表跟随激活歌单
        viewModelScope.launch {
            activeListId.flatMapLatest { repo.songsOf(it) }.collect { songs ->
                _uiState.value = _uiState.value.copy(songs = songs)
            }
        }
    }

    fun selectList(listId: String) {
        activeListId.value = listId
        _uiState.value = _uiState.value.copy(activeListId = listId)
    }

    fun createList(name: String) {
        viewModelScope.launch {
            repo.createList(name)
            _uiState.value = _uiState.value.copy(toast = "已创建歌单")
        }
    }

    fun renameList(listId: String, name: String) {
        viewModelScope.launch {
            repo.renameList(listId, name)
            _uiState.value = _uiState.value.copy(toast = "已重命名")
        }
    }

    fun removeList(listId: String) {
        viewModelScope.launch {
            repo.removeList(listId)
            if (_uiState.value.activeListId == listId) {
                _uiState.value = _uiState.value.copy(activeListId = MyListRepository.DEFAULT_ID)
            }
            _uiState.value = _uiState.value.copy(toast = "已删除歌单")
        }
    }

    /** 点击播放：整个歌单作为播放队列 */
    fun playAt(index: Int) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        playerRepository.playList(songs, index)
    }

    fun playAll() = playAt(0)

    /** 当前歌单内的移除 */
    fun removeSongAt(index: Int) {
        val song = _uiState.value.songs.getOrNull(index) ?: return
        viewModelScope.launch { repo.removeSong(_uiState.value.activeListId, song) }
    }

    fun moveSongAt(index: Int, delta: Int) {
        viewModelScope.launch { repo.moveSong(_uiState.value.activeListId, index, delta) }
    }

    fun clearActiveList() {
        viewModelScope.launch {
            repo.clearList(_uiState.value.activeListId)
            _uiState.value = _uiState.value.copy(toast = "已清空")
        }
    }

    /** 收藏歌曲到指定歌单（供外部页面调用） */
    fun addTo(listId: String, song: MusicInfo) {
        viewModelScope.launch {
            repo.addSongs(listId, listOf(song))
            val name = _uiState.value.lists.firstOrNull { it.id == listId }?.name ?: "歌单"
            _uiState.value = _uiState.value.copy(toast = "已收藏到「$name」")
        }
    }

    /** 网易无 MV 时存的数值 0，与缺失等价（mobile 同款判断） */
    private fun isMvMissing(song: MusicInfo): Boolean {
        if (song.source != "wy" && song.source != "kg") return false
        val mv = song.mv
        return mv.isNullOrEmpty() || (song.source == "wy" && mv == "0")
    }

    /**
     * 「重新导入」（mobile listMvRefresh 同款）：逐首从平台拉取最新歌曲信息，
     * 补全当前歌单内网易/酷狗歌曲缺失的 mv 字段，使播放MV/下载MV菜单可用。
     * 拉取成功但源本身没有 MV 的歌曲不写入（保持无 MV 状态）。
     */
    fun refreshMvInfo() {
        if (refreshingMv) return
        val listId = _uiState.value.activeListId
        val targets = _uiState.value.songs.filter { isMvMissing(it) }
        if (targets.isEmpty()) {
            toast("列表内没有需要重新导入的网易/酷狗歌曲")
            return
        }
        refreshingMv = true
        toast("开始重新拉取 ${targets.size} 首歌曲的信息...")
        viewModelScope.launch {
            var updated = 0
            var failed = 0
            try {
                for (song in targets) {
                    try {
                        val json = withContext(Dispatchers.IO) {
                            jsController.mvInfo(song.source, song.toJson())
                        }
                        val mv = JSONObject(json).optString("mv").takeIf { it.isNotEmpty() }
                        if (mv != null) {
                            repo.updateSong(listId, song.copy(mv = mv))
                            updated++
                        }
                    } catch (e: Throwable) {
                        failed++
                    }
                    delay(300)
                }
            } finally {
                refreshingMv = false
            }
            toast("重新导入完成：$updated 首补全了MV信息${if (failed > 0) "，$failed 首获取失败" else ""}")
        }
    }

    private fun toast(msg: String) {
        mainHandler.post { Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show() }
    }

    fun consumeToast() {
        _uiState.value = _uiState.value.copy(toast = null)
    }
}
