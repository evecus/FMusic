package com.music.fm.ui.songlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.music.fm.MusicApplication
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.SongListDetailInfo
import com.music.fm.data.model.SongListDetailResult
import com.music.fm.data.model.SongListItem
import com.music.fm.data.model.SongListPageResult
import com.music.fm.data.model.SongListSort
import com.music.fm.data.model.SongListTag
import com.music.fm.data.model.SongListTagGroup
import com.music.fm.data.model.SongListTagsResult
import com.music.fm.data.model.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 歌单详情（已打开的歌单） */
data class SongListDetailState(
    val id: String = "",
    val info: SongListDetailInfo? = null,
    val songs: List<MusicInfo> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    /** 「收藏歌单」正在后台拉取全部分页 */
    val collectPreparing: Boolean = false,
) {
    val loadedAll: Boolean get() = total in 1..songs.size
}

/**
 * 歌单页（对齐 mobile 版 src/screens/Home/Views/SongList）：
 * 五平台源切换 → 排序（取各源 sortList 第一个为默认）+ 标签筛选 →
 * 歌单封面网格（分页）→ 歌单详情歌曲列表（分页）→ 点击播放。
 * 标签按源缓存；排序/标签切换均从第一页重新加载。
 */
data class SongListUiState(
    val source: String = Sources.KW,
    val enabledSources: List<String> = SongListViewModel.SONG_LIST_SOURCES,
    val sortList: List<SongListSort> = emptyList(),
    val sortId: String = "",
    val tagId: String = "",
    val tagName: String = "",
    val hotTags: List<SongListTag> = emptyList(),
    val tagGroups: List<SongListTagGroup> = emptyList(),
    val lists: List<SongListItem> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val loadingHeader: Boolean = false,
    val loadingLists: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val detail: SongListDetailState? = null,
) {
    val loadedAll: Boolean get() = total in 1..lists.size
}

class SongListViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph

    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    /** 标签缓存：source → SongListTagsResult（静态，一次加载） */
    private val tagsCache = HashMap<String, SongListTagsResult>()

    /** 排序缓存：source → sortList（静态，一次加载） */
    private val sortsCache = HashMap<String, List<SongListSort>>()

    /** 歌单列表请求序号：切源/切排序/切标签后丢弃过期响应 */
    private var listSeq = 0

    /** 歌单详情请求序号 */
    private var detailSeq = 0

    init {
        loadSourceData(_uiState.value.source)
    }

    // ── 源 / 排序 / 标签切换 ─────────────────────────────────────

    fun setSource(source: String) {
        if (source == _uiState.value.source) return
        _uiState.value = _uiState.value.copy(source = source)
        loadSourceData(source)
    }

    fun setSort(sort: SongListSort) {
        if (sort.id == _uiState.value.sortId) return
        _uiState.value = _uiState.value.copy(sortId = sort.id)
        loadLists()
    }

    fun setTag(tag: SongListTag?) {
        if (tag?.id == _uiState.value.tagId) return
        _uiState.value = _uiState.value.copy(
            tagId = tag?.id ?: "",
            tagName = tag?.name ?: "",
        )
        loadLists()
    }

    // ── 歌单列表 ────────────────────────────────────────────────

    fun loadMoreLists() {
        val state = _uiState.value
        if (state.loadingLists || state.loadingMore || state.loadedAll) return
        if (state.lists.isEmpty()) return
        val seq = ++listSeq
        _uiState.value = state.copy(loadingMore = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SongListPageResult.parse(
                        graph.jsController.songList(
                            state.source, state.sortId, state.tagId, state.page + 1,
                        ),
                        state.source,
                    )
                }
                if (seq != listSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingMore = false,
                    lists = dedupItems(_uiState.value.lists + result.list),
                    page = _uiState.value.page + 1,
                    total = if (result.total > 0) result.total else _uiState.value.total,
                )
            } catch (e: Throwable) {
                if (seq != listSeq) return@launch
                _uiState.value = _uiState.value.copy(loadingMore = false, error = e.message ?: "加载失败")
            }
        }
    }

    // ── 歌单详情 ────────────────────────────────────────────────

    fun openDetail(item: SongListItem) {
        detailSeq++
        currentDetailId = item.id
        _uiState.value = _uiState.value.copy(
            detail = SongListDetailState(
                id = item.id,
                info = SongListDetailInfo(item.name, item.img, item.author, null),
                loading = true,
            ),
        )
        loadDetail(item.id)
    }

    /**
     * 通过歌单链接或 ID 打开歌单详情（对齐 mobile 版 OpenList「打开」）：
     * 输入文本原样作为歌单 id 传给当前源的 getSonglist，由音源脚本自行解析链接/ID，
     * 详情头信息（名称/封面）加载成功后由脚本返回的 info 补全。
     */
    fun openDetailByText(text: String) {
        val id = text.trim()
        if (id.isEmpty()) return
        detailSeq++
        currentDetailId = id
        _uiState.value = _uiState.value.copy(
            detail = SongListDetailState(
                id = id,
                info = null,
                loading = true,
            ),
        )
        loadDetail(id)
    }

    fun closeDetail() {
        detailSeq++
        currentDetailId = null
        _uiState.value = _uiState.value.copy(detail = null)
    }

    fun loadMoreDetail() {
        val detail = _uiState.value.detail ?: return
        if (detail.loading || detail.loadingMore || detail.loadedAll || detail.songs.isEmpty()) return
        val id = detail.id
        val seq = ++detailSeq
        _uiState.value = _uiState.value.copy(detail = detail.copy(loadingMore = true))
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SongListDetailResult.parse(
                        graph.jsController.songListDetail(_uiState.value.source, id, detail.page + 1),
                        _uiState.value.source,
                    )
                }
                if (seq != detailSeq) return@launch
                appendDetail(result)
            } catch (e: Throwable) {
                if (seq != detailSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    detail = _uiState.value.detail?.copy(loadingMore = false),
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    /** 播放歌单详情列表中第 index 首 */
    fun playAt(index: Int) {
        val list = _uiState.value.detail?.songs ?: return
        if (index !in list.indices) return
        graph.playerRepository.playList(list, index)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ── 收藏歌单（对齐 mobile 版 handleCollect + getListDetailAll + createList）──

    /**
     * 「收藏歌单」：后台循环拉取歌单全部分页歌曲（对齐 mobile 版 getListDetailAll：
     * 首页拿 total/limit 算页数，逐页拉完去重），然后整单创建到「收藏」页我的列表
     * （与内置列表同级，歌单名同原歌单，对齐 mobile 版 createList）。
     * 已收藏过同源歌单时全量覆盖同步（对齐 syncSourceList）。
     */
    fun prepareCollectPlaylist() {
        val detail = _uiState.value.detail ?: return
        if (detail.collectPreparing) return
        val source = _uiState.value.source
        _uiState.value = _uiState.value.copy(detail = detail.copy(collectPreparing = true))
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { fetchAllDetailSongs(source, detail.id) }
            }
            val cur = _uiState.value.detail
            if (cur == null || cur.id != detail.id) return@launch
            result.fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            detail = cur.copy(collectPreparing = false),
                            error = "获取歌单歌曲失败",
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(detail = cur.copy(collectPreparing = false))
                        val name = cur.info?.name?.takeIf { it.isNotEmpty() } ?: "歌单 ${detail.id}"
                        val existed = graph.myListRepository.upsertCollectedList(
                            name, "${source}__${detail.id}", songs,
                        )
                        toast(
                            if (existed) "「$name」已在我的列表，已同步最新歌曲"
                            else "已收藏到我的列表「$name」",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        detail = cur.copy(collectPreparing = false),
                        error = e.message ?: "获取歌单歌曲失败",
                    )
                },
            )
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_LONG).show()
    }

    /**
     * 拉取歌单内全部歌曲：按首页 total/limit 计算最大页数，逐页串行拉取后
     * 按 uniqueKey 去重（对齐 mobile 版 getListDetailAll 的 deduplicationList）。
     */
    private suspend fun fetchAllDetailSongs(source: String, id: String): List<MusicInfo> {
        val first = SongListDetailResult.parse(
            graph.jsController.songListDetail(source, id, 1),
            source,
        )
        val all = first.list.toMutableList()
        val limit = if (first.limit > 0) first.limit else 30
        val maxPage = if (first.total > 0) {
            kotlin.math.ceil(first.total.toDouble() / limit).toInt().coerceAtLeast(1)
        } else {
            1
        }
        for (page in 2..maxPage) {
            val r = SongListDetailResult.parse(
                graph.jsController.songListDetail(source, id, page),
                source,
            )
            if (r.list.isEmpty()) break
            all += r.list
        }
        val seen = HashSet<String>()
        return all.filter { seen.add(it.uniqueKey) }
    }

    // ── 内部 ────────────────────────────────────────────────────

    @Volatile
    private var currentDetailId: String? = null

    /**
     * 切源（对齐 mobile 版 Content.tsx onSourceChange）：
     * 取该源 sortList 第一个排序为默认，tagId 清空，随后拉第一页歌单；标签并行加载。
     */
    private fun loadSourceData(source: String) {
        listSeq++
        detailSeq++
        currentDetailId = null
        val cachedSorts = sortsCache[source]
        val cachedTags = tagsCache[source]
        _uiState.value = _uiState.value.copy(
            lists = emptyList(),
            page = 1,
            total = 0,
            sortId = "",
            tagId = "",
            tagName = "",
            detail = null,
            loadingHeader = cachedTags == null,
            loadingLists = true,
            error = null,
        )

        // 排序（静态数据）：缓存命中直接用，否则拉取后选中第一个再加载列表
        if (cachedSorts != null) {
            applySorts(source, cachedSorts)
        } else {
            viewModelScope.launch {
                try {
                    val sorts = withContext(Dispatchers.IO) {
                        parseSorts(graph.jsController.songListSorts(source))
                    }
                    sortsCache[source] = sorts
                    applySorts(source, sorts)
                } catch (e: Throwable) {
                    // 排序获取失败不阻塞：以空排序加载列表
                    if (_uiState.value.source == source) loadLists()
                }
            }
        }

        // 标签（静态数据）
        if (cachedTags != null) {
            applyTags(source, cachedTags)
        } else {
            viewModelScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        SongListTagsResult.parse(graph.jsController.songListTags(source), source)
                    }
                    tagsCache[source] = result
                    applyTags(source, result)
                } catch (e: Throwable) {
                    // 标签失败仅提示，不影响列表浏览
                    if (_uiState.value.source == source) {
                        _uiState.value = _uiState.value.copy(
                            loadingHeader = false,
                            error = e.message ?: "获取标签失败",
                        )
                    }
                }
            }
        }
    }

    private fun applySorts(source: String, sorts: List<SongListSort>) {
        if (_uiState.value.source != source) return
        _uiState.value = _uiState.value.copy(
            sortList = sorts,
            sortId = sorts.firstOrNull()?.id ?: "",
        )
        loadLists()
    }

    private fun applyTags(source: String, result: SongListTagsResult) {
        if (_uiState.value.source != source) return
        _uiState.value = _uiState.value.copy(
            hotTags = result.hotTags,
            tagGroups = result.groups,
            loadingHeader = false,
        )
    }

    private fun parseSorts(json: String): List<SongListSort> = runCatching {
        val el = kotlinx.serialization.json.Json.parseToJsonElement(json)
        (el as? kotlinx.serialization.json.JsonArray)?.mapNotNull { SongListSort.parse(it) }
    }.getOrNull() ?: emptyList()

    private fun loadLists() {
        val state = _uiState.value
        val seq = ++listSeq
        _uiState.value = state.copy(
            loadingLists = true,
            lists = emptyList(),
            page = 1,
            total = 0,
            error = null,
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SongListPageResult.parse(
                        graph.jsController.songList(
                            _uiState.value.source,
                            _uiState.value.sortId,
                            _uiState.value.tagId,
                            1,
                        ),
                        _uiState.value.source,
                    )
                }
                if (seq != listSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingLists = false,
                    lists = result.list,
                    page = 1,
                    total = result.total,
                )
            } catch (e: Throwable) {
                if (seq != listSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingLists = false,
                    error = e.message ?: "获取歌单失败",
                )
            }
        }
    }

    private fun loadDetail(id: String) {
        val seq = ++detailSeq
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SongListDetailResult.parse(
                        graph.jsController.songListDetail(_uiState.value.source, id, 1),
                        _uiState.value.source,
                    )
                }
                if (seq != detailSeq) return@launch
                val cur = _uiState.value.detail
                val info = result.info ?: cur?.info
                _uiState.value = _uiState.value.copy(
                    detail = SongListDetailState(
                        id = id,
                        info = info,
                        songs = result.list,
                        page = 1,
                        total = result.total,
                        loading = false,
                    ),
                )
            } catch (e: Throwable) {
                if (seq != detailSeq) return@launch
                _uiState.value = _uiState.value.copy(
                    detail = _uiState.value.detail?.copy(loading = false),
                    error = e.message ?: "获取歌单详情失败",
                )
            }
        }
    }

    private fun appendDetail(result: SongListDetailResult) {
        val cur = _uiState.value.detail ?: return
        val seen = HashSet<String>()
        val merged = (cur.songs + result.list).filter { seen.add(it.uniqueKey) }
        _uiState.value = _uiState.value.copy(
            detail = cur.copy(
                songs = merged,
                page = cur.page + 1,
                total = if (result.total > 0) result.total else cur.total,
                loadingMore = false,
            ),
        )
    }

    private fun dedupItems(list: List<SongListItem>): List<SongListItem> {
        val seen = HashSet<String>()
        return list.filter { seen.add("${it.source}-${it.id}") }
    }

    companion object {
        /** 支持歌单的五个平台（对齐 mobile 版 songlist state.sources） */
        val SONG_LIST_SOURCES = listOf(Sources.KW, Sources.KG, Sources.TX, Sources.WY, Sources.MG)

        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication
                SongListViewModel(app)
            }
        }
    }
}
