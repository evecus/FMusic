package com.music.fm.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.music.fm.MusicApplication
import com.music.fm.data.SettingsRepository
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.SearchResult
import com.music.fm.data.model.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SearchUiState(
    /** 输入框当前内容（未提交） */
    val keyword: String = "",
    /** 已提交搜索的关键词 */
    val searchText: String = "",
    val source: String = Sources.KW,
    val enabledSources: List<String> = listOf(Sources.KW, Sources.KG, Sources.TX, Sources.WY, Sources.MG),
    /** 搜索建议（tipSearch，防抖后更新） */
    val tips: List<String> = emptyList(),
    /** 当前音源的热门搜索词 */
    val hotList: List<String> = emptyList(),
    /** 搜索历史（最近在前，最多 [MAX_HISTORY] 条） */
    val history: List<String> = emptyList(),
    val results: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * 搜索 ViewModel，逻辑对齐 lx-music：
 *
 *  - 输入即触发搜索建议（tipSearch，防抖 250ms，随音源切换用当前输入刷新）；
 *  - 提交搜索记录历史（去重置顶，最多 15 条，持久化）；
 *  - 热门搜索按音源拉取并缓存（对齐 lx hotSearch.getList 缓存行为）；
 *  - 切换音源：持久化选择；若已有搜索结果则用当前关键词重新搜索（对齐
 *    lx HeaderBar handleSourceChange → loadList 行为）；
 *  - 结果分页加载（30/页，对齐 lx limit）。
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph
    private val settingsRepository: SettingsRepository = graph.settingsRepository

    private val prefs = app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 搜索建议防抖 job */
    private var tipJob: Job? = null
    /** 热搜缓存（per source，对齐 lx hotSearchState.sourceList 缓存） */
    private val hotCache = HashMap<String, List<String>>()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val sources = settings.searchSources.toList()
            val savedSource = prefs.getString(KEY_SOURCE, null)
            _uiState.value = _uiState.value.copy(
                enabledSources = sources,
                source = if (savedSource != null && sources.contains(savedSource)) savedSource
                else sources.firstOrNull() ?: Sources.KW,
                history = loadHistory(),
            )
            loadHotList(_uiState.value.source)
        }
    }

    // ── 搜索历史（持久化，最多 15 条，对齐 lx addHistoryWord）─────────────────

    private fun loadHistory(): List<String> =
        prefs.getString(KEY_HISTORY, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun saveHistory(list: List<String>) {
        prefs.edit().putString(KEY_HISTORY, list.joinToString("\n")).apply()
    }

    private fun addHistory(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + _uiState.value.history.filter { it != trimmed })
            .take(MAX_HISTORY)
        saveHistory(updated)
        _uiState.value = _uiState.value.copy(history = updated)
    }

    /** 点击历史词：填入关键词并立即搜索 */
    fun selectHistory(word: String) = searchWord(word)

    /** 长按删除单条历史（对齐 lx HistorySearch onLongPress） */
    fun removeHistory(word: String) {
        val updated = _uiState.value.history - word
        saveHistory(updated)
        _uiState.value = _uiState.value.copy(history = updated)
    }

    fun clearHistory() {
        saveHistory(emptyList())
        _uiState.value = _uiState.value.copy(history = emptyList())
    }

    // ── 关键词 / 搜索建议 ────────────────────────────────────────

    /**
     * 输入变化（手机输入法 / TV 屏上键盘共用）。
     * 非空 → 防抖 250ms 请求搜索建议（对齐 lx debounceTipSearch 200ms）；
     * 清空 → 建议清空。
     */
    fun onKeywordChange(text: String) {
        _uiState.value = _uiState.value.copy(keyword = text)
        tipJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(tips = emptyList())
            return
        }
        tipJob = viewModelScope.launch {
            delay(TIP_DEBOUNCE_MS)
            fetchTips(trimmed)
        }
    }

    /** TV 屏上键盘：追加字符 */
    fun appendChar(ch: String) = onKeywordChange(_uiState.value.keyword + ch)

    /** TV 屏上键盘：退格 */
    fun backspace() = onKeywordChange(_uiState.value.keyword.dropLast(1))

    /** TV「清空」：清输入与建议，并退出结果视图回发现页（对齐 Lumio clear） */
    fun clearInput() {
        tipJob?.cancel()
        _uiState.value = _uiState.value.copy(
            keyword = "",
            tips = emptyList(),
            searchText = "",
            results = emptyList(),
            total = 0,
        )
    }

    /** 手机输入框「×」：清空输入 = 退出结果视图（对齐 lx handleClearText） */
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchText = "",
            results = emptyList(),
            total = 0,
        )
    }

    /** TV「取消」：同清空（对齐 Lumio cancelSearch → clear） */
    fun cancelInput() = clearInput()

    private fun fetchTips(keyword: String) {
        val source = _uiState.value.source
        if (!graph.jsController.isReady()) return
        viewModelScope.launch {
            try {
                val tips = withContext(Dispatchers.IO) {
                    parseStringList(graph.jsController.tipSearch(source, keyword))
                }
                // 过期结果丢弃（输入已变或已切源）
                val cur = _uiState.value
                if (cur.keyword.trim() == keyword && cur.source == source) {
                    _uiState.value = cur.copy(tips = tips)
                }
            } catch (_: Throwable) {
                // 建议失败静默（对齐 Lumio fetchTipList catch → 空列表）
                val cur = _uiState.value
                if (cur.keyword.trim() == keyword && cur.source == source) {
                    _uiState.value = cur.copy(tips = emptyList())
                }
            }
        }
    }

    // ── 热门搜索 ────────────────────────────────────────────────

    private fun loadHotList(source: String) {
        hotCache[source]?.let {
            _uiState.value = _uiState.value.copy(hotList = it)
            return
        }
        if (!graph.jsController.isReady()) return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    parseStringList(graph.jsController.hotSearch(source))
                }
                hotCache[source] = list
                if (_uiState.value.source == source) {
                    _uiState.value = _uiState.value.copy(hotList = list)
                }
            } catch (_: Throwable) {
                if (_uiState.value.source == source) {
                    _uiState.value = _uiState.value.copy(hotList = emptyList())
                }
            }
        }
    }

    // ── 音源切换（对齐 lx handleSourceChange：持久化 + 重新搜索/刷新建议）─────

    fun setSource(source: String) {
        if (source == _uiState.value.source) return
        prefs.edit().putString(KEY_SOURCE, source).apply()
        _uiState.value = _uiState.value.copy(source = source)
        loadHotList(source)
        // 有输入 → 用当前输入刷新建议（对齐 Lumio handleSelectSource）
        val kw = _uiState.value.keyword.trim()
        if (kw.isNotEmpty()) {
            tipJob?.cancel()
            tipJob = viewModelScope.launch {
                delay(TIP_DEBOUNCE_MS)
                fetchTips(kw)
            }
        }
        // 已有搜索结果 → 重新搜索（对齐 lx handleSourceChange → loadList(searchText, ...)）
        if (_uiState.value.searchText.isNotEmpty()) {
            doSearch(_uiState.value.searchText)
        }
    }

    // ── 搜索 ────────────────────────────────────────────────────

    /** 提交当前输入框内容 */
    fun search() = searchWord(_uiState.value.keyword)

    /** 点击建议词 / 热搜词 / 历史词 / 直接提交：填入并搜索 */
    fun searchWord(word: String) {
        val keyword = word.trim()
        if (keyword.isEmpty()) return
        tipJob?.cancel()
        _uiState.value = _uiState.value.copy(keyword = keyword, tips = emptyList())
        addHistory(keyword)
        doSearch(keyword)
    }

    private fun doSearch(keyword: String) {
        if (!graph.jsController.isReady()) {
            _uiState.value = _uiState.value.copy(error = "音源引擎启动中，请稍候")
            return
        }
        _uiState.value = _uiState.value.copy(
            searchText = keyword,
            loading = true,
            error = null,
            page = 1,
            results = emptyList(),
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val json = graph.jsController.search(
                        _uiState.value.source,
                        keyword,
                        1,
                        PAGE_SIZE,
                    )
                    SearchResult.parse(json, _uiState.value.source)
                }
                // 过期结果丢弃（期间已切词/切源）
                if (_uiState.value.searchText != keyword) return@launch
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    results = result.list,
                    total = result.total,
                    page = 1,
                )
            } catch (e: Throwable) {
                if (_uiState.value.searchText != keyword) return@launch
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "搜索失败",
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.loadingMore) return
        if (state.results.size >= state.total && state.total > 0) return
        val nextPage = state.page + 1
        _uiState.value = state.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val json = graph.jsController.search(
                        state.source,
                        state.searchText.trim(),
                        nextPage,
                        PAGE_SIZE,
                    )
                    SearchResult.parse(json, state.source)
                }
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    results = _uiState.value.results + result.list,
                    page = nextPage,
                )
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    fun playAll(index: Int) {
        val list = _uiState.value.results
        if (list.isEmpty()) return
        graph.playerRepository.playList(list, index)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun parseStringList(json: String): List<String> {
        val out = mutableListOf<String>()
        val arr: JSONArray = runCatching {
            JSONObject(json).optJSONArray("list") ?: JSONArray(json)
        }.getOrElse { return emptyList() }
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)?.toString() ?: continue
            if (v.isNotBlank()) out.add(v)
        }
        return out
    }

    companion object {
        const val PAGE_SIZE = 30
        private const val PREFS_NAME = "fm_search"
        private const val KEY_HISTORY = "history"
        private const val KEY_SOURCE = "source"
        const val MAX_HISTORY = 15
        const val TIP_DEBOUNCE_MS = 250L

        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication
                SearchViewModel(app)
            }
        }
    }
}
