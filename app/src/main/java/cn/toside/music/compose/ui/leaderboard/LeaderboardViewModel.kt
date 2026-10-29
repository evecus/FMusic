package cn.toside.music.compose.ui.leaderboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.toside.music.compose.MusicApplication
import cn.toside.music.compose.data.model.BoardItem
import cn.toside.music.compose.data.model.BoardsResult
import cn.toside.music.compose.data.model.BoardListResult
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 排行榜页（对齐 mobile 版 src/screens/Home/Views/Leaderboard）：
 * 五平台音源切换 → 各平台榜单列表 → 榜内歌曲分页加载 → 点击播放。
 * 榜单列表按源缓存，切回已加载过的源不重复请求。
 */
data class LeaderboardUiState(
    val source: String = Sources.KW,
    val enabledSources: List<String> = LeaderboardViewModel.LEADERBOARD_SOURCES,
    val boards: List<BoardItem> = emptyList(),
    val currentBoard: BoardItem? = null,
    val songs: List<MusicInfo> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val loadingBoards: Boolean = false,
    val loadingSongs: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val loadedAll: Boolean get() = total in 1..songs.size
}

class LeaderboardViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    /** 榜单列表缓存：source → boards */
    private val boardsCache = HashMap<String, List<BoardItem>>()

    /** 请求序号：切源 / 切榜后丢弃过期响应 */
    private var loadSeq = 0

    init {
        loadBoards(_uiState.value.source, selectFirst = true)
    }

    fun setSource(source: String) {
        if (source == _uiState.value.source) return
        _uiState.value = _uiState.value.copy(source = source)
        loadBoards(source, selectFirst = true)
    }

    fun selectBoard(board: BoardItem) {
        if (board.id == _uiState.value.currentBoard?.id) return
        _uiState.value = _uiState.value.copy(currentBoard = board)
        loadSongs(board)
    }

    fun playAt(index: Int) {
        val list = _uiState.value.songs
        if (index !in list.indices) return
        graph.playerRepository.playList(list, index)
    }

    fun loadMore() {
        val state = _uiState.value
        val board = state.currentBoard ?: return
        if (state.loadingSongs || state.loadingMore || state.loadedAll) return
        if (state.songs.isEmpty()) return
        val seq = ++loadSeq
        _uiState.value = state.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BoardListResult.parse(
                        graph.jsController.boardList(state.source, board.bangid, state.page + 1),
                        state.source,
                    )
                }
                if (seq != loadSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    songs = dedup(_uiState.value.songs + result.list),
                    page = _uiState.value.page + 1,
                    total = if (result.total > 0) result.total else _uiState.value.total,
                )
            } catch (e: Throwable) {
                if (seq != loadSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    fun refresh() {
        val board = _uiState.value.currentBoard ?: return
        loadSongs(board)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** 加载平台榜单列表；selectFirst 时自动选中第一个榜单并拉取歌曲 */
    private fun loadBoards(source: String, selectFirst: Boolean) {
        val seq = ++loadSeq
        val cached = boardsCache[source]
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                boards = cached,
                loadingBoards = false,
            )
            if (selectFirst) cached.firstOrNull()?.let { selectBoard(it) }
            return
        }
        _uiState.value = _uiState.value.copy(
            boards = emptyList(),
            currentBoard = null,
            songs = emptyList(),
            total = 0,
            page = 1,
            loadingBoards = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BoardsResult.parse(graph.jsController.getBoards(source), source)
                }
                if (seq != loadSeq) return@launch
                boardsCache[source] = result.list
                _uiState.value = _uiState.value.copy(
                    boards = result.list,
                    loadingBoards = false,
                )
                if (selectFirst) {
                    val first = result.list.firstOrNull()
                    if (first != null) {
                        _uiState.value = _uiState.value.copy(currentBoard = first)
                        loadSongs(first)
                    }
                }
            } catch (e: Throwable) {
                if (seq != loadSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingBoards = false,
                    error = e.message ?: "获取榜单失败",
                )
            }
        }
    }

    private fun loadSongs(board: BoardItem) {
        val seq = ++loadSeq
        _uiState.value = _uiState.value.copy(
            songs = emptyList(),
            page = 1,
            total = 0,
            loadingSongs = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    BoardListResult.parse(
                        graph.jsController.boardList(_uiState.value.source, board.bangid, 1),
                        _uiState.value.source,
                    )
                }
                if (seq != loadSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingSongs = false,
                    songs = dedup(result.list),
                    page = 1,
                    total = result.total,
                )
            } catch (e: Throwable) {
                if (seq != loadSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingSongs = false,
                    error = e.message ?: "获取榜单歌曲失败",
                )
            }
        }
    }

    private fun dedup(list: List<MusicInfo>): List<MusicInfo> {
        val seen = HashSet<String>()
        return list.filter { seen.add(it.uniqueKey) }
    }

    companion object {
        /** 支持排行榜的五个平台（对齐 mobile 版 leaderboard state.sources） */
        val LEADERBOARD_SOURCES = listOf(Sources.KW, Sources.KG, Sources.TX, Sources.WY, Sources.MG)

        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication
                LeaderboardViewModel(app)
            }
        }
    }
}
