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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val keyword: String = "",
    val source: String = Sources.KW,
    val enabledSources: List<String> = listOf(Sources.KW, Sources.KG, Sources.TX, Sources.WY, Sources.MG),
    val results: List<MusicInfo> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** 搜索历史（最近在前，最多 [MAX_HISTORY] 条） */
    val history: List<String> = emptyList(),
)

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph
    private val settingsRepository: SettingsRepository = graph.settingsRepository

    private val prefs = app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val sources = settings.searchSources.toList()
            _uiState.value = _uiState.value.copy(
                enabledSources = sources,
                source = sources.firstOrNull() ?: Sources.KW,
                history = loadHistory(),
            )
        }
    }

    // ── 搜索历史（本地持久化，Lumio TV 搜索页中间栏）──────────────────────────

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
    fun selectHistory(word: String) {
        _uiState.value = _uiState.value.copy(keyword = word)
        search()
    }

    fun clearHistory() {
        saveHistory(emptyList())
        _uiState.value = _uiState.value.copy(history = emptyList())
    }

    fun setKeyword(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
    }

    fun setSource(source: String) {
        _uiState.value = _uiState.value.copy(source = source)
    }

    fun search() {
        val state = _uiState.value
        if (state.keyword.isBlank()) return
        if (!graph.jsController.isReady()) {
            _uiState.value = state.copy(error = "音源引擎启动中，请稍候")
            return
        }
        _uiState.value = state.copy(loading = true, error = null, page = 1, results = emptyList())
        addHistory(state.keyword)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val json = graph.jsController.search(
                        _uiState.value.source,
                        _uiState.value.keyword.trim(),
                        1,
                        PAGE_SIZE,
                    )
                    SearchResult.parse(json, _uiState.value.source)
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    results = result.list,
                    total = result.total,
                    page = 1,
                )
            } catch (e: Throwable) {
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
                        state.keyword.trim(),
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

    companion object {
        const val PAGE_SIZE = 30
        private const val PREFS_NAME = "fm_search"
        private const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 12

        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication
                SearchViewModel(app)
            }
        }
    }
}
