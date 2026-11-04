package com.music.fm.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.Sources
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.component.LumioSongRow
import com.music.fm.ui.component.LumioTab
import com.music.fm.ui.component.LumioTagPill
import com.music.fm.ui.component.LxIconButton
import com.music.fm.ui.component.SongRowActions
import com.music.fm.ui.component.lxFocusable
import com.music.fm.ui.theme.LumioBorder
import com.music.fm.ui.theme.LumioFont
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioPrimary

/**
 * 搜索页（按设备分流）：
 *
 *  - 手机 / 平板：对齐 lx-music 搜索页 —— 顶栏（左音源 Tab + 右搜索输入框），
 *    无关键词时展示「热门搜索 + 搜索历史」（流式胶囊，历史长按删除），
 *    输入中浮层展示搜索建议（点击即搜），搜索后为分页结果列表；
 *  - TV：对齐 Lumio TVSearchPanel 三栏布局 —— 左栏键盘（字母数字 6 列 +
 *    删除/清空 + 搜索/取消），中栏三态（发现：热搜|历史 / 输入：建议|历史 /
 *    结果：隐藏），右栏搜索结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchWord: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAppendChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearInput: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    actions: SongRowActions,
    playingKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    if (adaptive.isTv) {
        SearchScreenTv(
            state = state,
            onSearch = onSearch,
            onSearchWord = onSearchWord,
            onSourceChange = onSourceChange,
            onPlay = onPlay,
            onLoadMore = onLoadMore,
            onClearHistory = onClearHistory,
            onAppendChar = onAppendChar,
            onBackspace = onBackspace,
            onClearInput = onClearInput,
            actions = actions,
            playingKey = playingKey,
            modifier = modifier,
        )
    } else {
        SearchScreenMobile(
            state = state,
            onKeywordChange = onKeywordChange,
            onSearch = onSearch,
            onSearchWord = onSearchWord,
            onClearSearch = onClearSearch,
            onSourceChange = onSourceChange,
            onPlay = onPlay,
            onLoadMore = onLoadMore,
            onRemoveHistory = onRemoveHistory,
            onClearHistory = onClearHistory,
            onAddToList = onAddToList,
            actions = actions,
            playingKey = playingKey,
            modifier = modifier,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 手机 / 平板：对齐 lx-music Search（HeaderBar + BlankView / TipList / 结果列表）
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchScreenMobile(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchWord: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    actions: SongRowActions,
    playingKey: String?,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val focusManager: FocusManager = LocalFocusManager.current
    var inputFocused by remember { mutableStateOf(false) }
    val showTips = inputFocused && state.keyword.isNotBlank() && state.tips.isNotEmpty()

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.results.size) {
        if (shouldLoadMore && state.results.isNotEmpty()) onLoadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── HeaderBar（对齐 lx HeaderBar）──────────────────────────────────────
        // 手机：窄屏放不下「Tab + 输入框」同一行（可横滚的 Tab 会吃掉全部宽度，
        //       把输入框挤成细长条），因此上下两行：输入框在上、音源 Tab 在下；
        // 平板：同一行，Tab 占剩余宽度（可横滚），输入框固定宽度靠右。
        val sourceTabs: @Composable (Modifier) -> Unit = { tabModifier ->
            Row(
                modifier = tabModifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.enabledSources.forEach { source ->
                    LumioTab(
                        label = Sources.label(source),
                        selected = state.source == source,
                        onClick = { onSourceChange(source) },
                    )
                }
            }
        }
        val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = state.keyword,
                onValueChange = onKeywordChange,
                modifier = fieldModifier.onFocusChanged { inputFocused = it.isFocused },
                placeholder = { Text("搜索歌曲、歌手", fontSize = 14.sp, maxLines = 1) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        LxIconButton(
                            onClick = {
                                onKeywordChange("")
                                // 清空输入 = 退出结果视图（对齐 lx handleClearText）
                                onClearSearch()
                            },
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空",
                            compact = true,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onSearch()
                }),
            )
        }

        if (adaptive.isPhone) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = adaptive.pagePadding, end = adaptive.pagePadding, top = 4.dp, bottom = 2.dp),
            ) {
                searchField(Modifier.fillMaxWidth())
                sourceTabs(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = adaptive.pagePadding, end = adaptive.pagePadding, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                sourceTabs(Modifier.weight(1f))
                searchField(Modifier.width(320.dp))
            }
        }

        // ── 内容区 ─────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                // 搜索建议浮层（对齐 lx TipList：覆盖内容区，点击即搜）
                showTips -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.tips.forEach { tip ->
                        Text(
                            text = tip,
                            fontSize = 15.sp,
                            color = LumioFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    onSearchWord(tip)
                                }
                                .padding(horizontal = adaptive.pagePadding, vertical = 9.dp),
                        )
                    }
                }

                // 空白视图：热搜 + 搜索历史（对齐 lx BlankView）
                state.searchText.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = adaptive.pagePadding),
                ) {
                    if (state.hotList.isNotEmpty()) {
                        Text(
                            text = "热门搜索",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        TagFlowRow(
                            words = state.hotList,
                            onSearch = { focusManager.clearFocus(); onSearchWord(it) },
                        )
                    }
                    if (state.history.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "搜索历史",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(10.dp))
                            LxIconButton(
                                onClick = onClearHistory,
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清空历史",
                                compact = true,
                            )
                        }
                        TagFlowRow(
                            words = state.history,
                            onSearch = { focusManager.clearFocus(); onSearchWord(it) },
                            onRemove = onRemoveHistory,
                        )
                    }
                    if (state.hotList.isEmpty() && state.history.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("搜索你想听的歌曲", fontSize = 20.sp, color = LumioFontLabel)
                        }
                    }
                }

                // 结果列表
                else -> SearchResultList(
                    state = state,
                    onPlay = onPlay,
                    onLoadMore = onLoadMore,
                    actions = actions,
                    playingKey = playingKey,
                    listState = listState,
                )
            }
        }
    }
}

/** 流式词胶囊（热搜 / 历史共用，对齐 lx 样式；历史长按删除） */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TagFlowRow(
    words: List<String>,
    onSearch: (String) -> Unit,
    onRemove: ((String) -> Unit)? = null,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        words.forEach { word ->
            LumioTagPill(
                text = word,
                onClick = { onSearch(word) },
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .combinedClickable(
                        onClick = { onSearch(word) },
                        onLongClick = onRemove?.let { remove -> { remove(word) } },
                    ),
            )
        }
    }
}

/** 结果列表：手机单列 / 平板双列（对齐全局列表规范） */
@Composable
private fun SearchResultList(
    state: SearchUiState,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    actions: SongRowActions,
    playingKey: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val adaptive = LocalAdaptive.current
    when {
        state.loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.results.isEmpty() && state.error == null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("未找到相关歌曲", color = LumioFontLabel) }

        state.results.isEmpty() && state.error != null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

        adaptive.twoPane -> {
            val gridState = rememberLazyGridState()
            val shouldLoadMoreGrid by remember {
                derivedStateOf {
                    val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= gridState.layoutInfo.totalItemsCount - 3
                }
            }
            LaunchedEffect(shouldLoadMoreGrid, state.results.size) {
                if (shouldLoadMoreGrid && state.results.isNotEmpty()) onLoadMore()
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                gridItemsIndexed(state.results, key = { _, it -> it.uniqueKey }) { index, info ->
                    LumioSongRow(
                        info = info,
                        actions = actions,
                        index = index + 1,
                        isActive = playingKey == info.uniqueKey,
                        showSourceBadge = false,
                        onClick = { onPlay(index) },
                        rowHeight = adaptive.rowHeight,
                    )
                }
                if (state.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }

        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.results, key = { _, it -> it.uniqueKey }) { index, info ->
                LumioSongRow(
                    info = info,
                    actions = actions,
                    index = index + 1,
                    isActive = playingKey == info.uniqueKey,
                    showSourceBadge = false,
                    onClick = { onPlay(index) },
                    rowHeight = adaptive.rowHeight,
                )
            }
            if (state.loadingMore) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// TV：对齐 Lumio TVSearchPanel 三栏布局
// ═════════════════════════════════════════════════════════════════════════════

/** 中栏三态（对齐 Lumio stage）：discover 未搜索 / typing 输入中 / result 已搜索 */
private enum class TvStage { DISCOVER, TYPING, RESULT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreenTv(
    state: SearchUiState,
    onSearch: () -> Unit,
    onSearchWord: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onClearHistory: () -> Unit,
    onAppendChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearInput: () -> Unit,
    actions: SongRowActions,
    playingKey: String?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.results.size) {
        if (shouldLoadMore && state.results.isNotEmpty()) onLoadMore()
    }

    // stage 派生（对齐 Lumio 三态）：
    //   输入为空 → discover；已提交且输入未再改动 → result；其余（输入中）→ typing
    val stage = when {
        state.keyword.isBlank() -> TvStage.DISCOVER
        state.searchText.isNotEmpty() && state.keyword == state.searchText -> TvStage.RESULT
        else -> TvStage.TYPING
    }

    // 搜索完成后焦点自动移到第一个结果（对齐 Lumio focusFirstItem）
    val firstResultFocus = remember { FocusRequester() }
    LaunchedEffect(state.results.isNotEmpty()) {
        if (stage == TvStage.RESULT && state.results.isNotEmpty()) {
            runCatching { firstResultFocus.requestFocus() }
        }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // ══ 左栏：搜索框 + 音源 Tab + 屏上键盘 ══
        Column(
            modifier = Modifier
                .width(460.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 输入框展示区（TV 通过屏上键盘输入）
            SearchInputBox(
                keyword = state.keyword,
                onClear = onClearInput,
            )

            // 音源 Tab（下划线式，单行可横向滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.enabledSources.forEach { source ->
                    LumioTab(
                        label = Sources.label(source),
                        selected = state.source == source,
                        onClick = { onSourceChange(source) },
                    )
                }
            }

            TvKeyboard(
                keyword = state.keyword,
                onAppendChar = onAppendChar,
                onBackspace = onBackspace,
                onClear = onClearInput,
                onSearch = onSearch,
            )
        }

        // ══ 中栏：discover / typing 两列；result 隐藏 ══
        if (stage != TvStage.RESULT) {
            Row(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
            ) {
                // 左列：输入中 → 搜索建议；未搜索 → 热门搜索
                TagColumn(
                    title = if (stage == TvStage.TYPING) "搜索建议" else "热门搜索",
                    emptyHint = if (stage == TvStage.TYPING) "输入字母获取建议" else "暂无热门搜索",
                    words = if (stage == TvStage.TYPING) state.tips else state.hotList,
                    onSelect = onSearchWord,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                // 右列：搜索历史（discover / typing 都显示）
                TagColumn(
                    title = "搜索历史",
                    emptyHint = "暂无搜索历史",
                    words = state.history,
                    onSelect = onSearchWord,
                    onClear = onClearHistory,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // ══ 右栏：搜索结果（result 阶段独占剩余空间）══
        Box(
            modifier = if (stage == TvStage.RESULT) {
                Modifier.weight(1.15f).fillMaxHeight()
            } else {
                Modifier.width(0.dp).fillMaxHeight()
            },
        ) {
            if (stage == TvStage.RESULT) {
                SearchResultBodyTv(
                    state = state,
                    listState = listState,
                    onPlay = onPlay,
                    actions = actions,
                    playingKey = playingKey,
                    firstResultFocus = firstResultFocus,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── 搜索框展示区（TV）────────────────────────────────────────────────────────

@Composable
private fun SearchInputBox(
    keyword: String,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, LumioBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = LumioFontLabel,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = keyword.ifEmpty { "请用遥控器按字母输入..." },
            fontSize = 16.sp,
            color = if (keyword.isEmpty()) LumioFontLabel else LumioFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (keyword.isNotEmpty()) {
            Text(
                text = "清空",
                fontSize = 13.sp,
                color = LumioFontLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// ── 中栏词列（热搜 / 历史 / 建议共用）───────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagColumn(
    title: String,
    emptyHint: String,
    words: List<String>,
    onSelect: (String) -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = LumioFontLabel,
                modifier = Modifier.weight(1f),
            )
            if (onClear != null && words.isNotEmpty()) {
                Text(
                    text = "清空",
                    fontSize = 12.sp,
                    color = LumioFontLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (words.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(emptyHint, fontSize = 13.sp, color = LumioFontLabel)
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                words.forEach { word ->
                    LumioTagPill(
                        text = word,
                        onClick = { onSelect(word) },
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                }
            }
        }
    }
}

// ── 搜索结果（TV）────────────────────────────────────────────────────────────

@Composable
private fun SearchResultBodyTv(
    state: SearchUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (Int) -> Unit,
    actions: SongRowActions,
    playingKey: String?,
    firstResultFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    Box(modifier = modifier) {
        when {
            state.loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.results.isEmpty() && state.error == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("未找到相关歌曲", color = LumioFontLabel) }

            state.results.isEmpty() && state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.results, key = { _, it -> it.uniqueKey }) { index, info ->
                    Box(modifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier) {
                        LumioSongRow(
                            info = info,
                            actions = actions,
                            index = index + 1,
                            isActive = playingKey == info.uniqueKey,
                            showSourceBadge = false,
                            onClick = { onPlay(index) },
                            rowHeight = adaptive.rowHeight,
                        )
                    }
                }
                if (state.loadingMore) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

// ── TV 屏上键盘（Lumio 风格：6 列圆角按键）───────────────────────────────────

/** TV 屏上键盘：26 字母 + 数字（6 列）+ 删除/清空 + 搜索/取消（对齐 Lumio） */
@Composable
private fun TvKeyboard(
    keyword: String,
    onAppendChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val letters = ('a'..'z').map { it.toString() } + (0..9).map { it.toString() }
    val rows = letters.chunked(6)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { ch ->
                    KeyboardKey(
                        label = ch.uppercase(),
                        onClick = { onAppendChar(ch) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyboardKey(
                label = "删除",
                onClick = onBackspace,
                modifier = Modifier.weight(1f),
                outlined = true,
            )
            KeyboardKey(
                label = "清空",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                outlined = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyboardKey(
                label = "搜索",
                onClick = onSearch,
                modifier = Modifier.weight(1f),
                accent = true,
            )
            KeyboardKey(
                label = "取消",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                outlined = true,
            )
        }
    }
}

@Composable
private fun KeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    accent: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        accent -> LumioPrimary
        outlined -> LumioBorder
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                if (borderColor == androidx.compose.ui.graphics.Color.Transparent) 0.dp
                else if (accent) 1.5.dp else 1.dp,
                borderColor,
                shape,
            )
            .lxFocusable(shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (accent) LumioPrimary else LumioFont,
        )
    }
}
