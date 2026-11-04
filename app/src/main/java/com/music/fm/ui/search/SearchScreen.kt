package com.music.fm.ui.search

import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.music.fm.ui.component.lxFocusable
import com.music.fm.ui.component.lxRowFocusNav
import com.music.fm.ui.theme.LumioBorder
import com.music.fm.ui.theme.LumioFont
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioPrimary

/**
 * 搜索页（按设备分流）：
 *
 *  - TV   ：Lumio 三栏布局 —— 左栏搜索框 + 音源 Tab + 屏上键盘，
 *           中栏搜索历史（流式胶囊），右栏统一 Lumio 歌曲行结果列表；
 *  - 手机 / 平板：保持原版布局 —— 顶部输入框（系统输入法）+ 音源 Chip + 结果列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onSelectHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    if (adaptive.isTv) {
        SearchScreenTv(
            state = state,
            onKeywordChange = onKeywordChange,
            onSearch = onSearch,
            onSourceChange = onSourceChange,
            onPlay = onPlay,
            onLoadMore = onLoadMore,
            onSelectHistory = onSelectHistory,
            onClearHistory = onClearHistory,
            actions = actions,
            playingKey = playingKey,
            modifier = modifier,
        )
    } else {
        SearchScreenClassic(
            state = state,
            onKeywordChange = onKeywordChange,
            onSearch = onSearch,
            onSourceChange = onSourceChange,
            onPlay = onPlay,
            onLoadMore = onLoadMore,
            actions = actions,
            modifier = modifier,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// TV：Lumio 三栏布局（TVSearchPanel）
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchScreenTv(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onSelectHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
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

    Row(modifier = modifier.fillMaxSize()) {
        // ══ 左栏：搜索框 + 音源 + 键盘 ══
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
                onClear = { onKeywordChange("") },
            )

            // 音源 Tab（下划线式）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                state.enabledSources.forEach { source ->
                    LumioTab(
                        label = Sources.label(source),
                        selected = state.source == source,
                        onClick = { onSourceChange(source) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            TvKeyboard(
                keyword = state.keyword,
                onKeywordChange = onKeywordChange,
                onSearch = onSearch,
            )
        }

        // ══ 中栏：搜索历史 ══
        HistoryColumn(
            history = state.history,
            onSelect = onSelectHistory,
            onClear = onClearHistory,
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight(),
        )

        // ══ 右栏：搜索结果 ══
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight(),
        ) {
            SearchResultBody(
                state = state,
                listState = listState,
                onPlay = onPlay,
                actions = actions,
                playingKey = playingKey,
                modifier = Modifier.fillMaxSize(),
            )
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

// ── 搜索历史中栏 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryColumn(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
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
                text = "搜索历史",
                fontSize = 13.sp,
                color = LumioFontLabel,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
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
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无搜索历史", fontSize = 13.sp, color = LumioFontLabel)
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
                history.forEach { word ->
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

// ── 搜索结果 ─────────────────────────────────────────────────────────────────

@Composable
private fun SearchResultBody(
    state: SearchUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (Int) -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String?,
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
            ) { Text("输入关键词开始搜索", color = LumioFontLabel) }

            state.results.isEmpty() && state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.results, key = { _, it -> it.uniqueKey }) { index, info ->
                    LumioSongRow(
                        info = info,
                        actions = actions,
                        index = index + 1,
                        isActive = playingKey == info.uniqueKey,
                        onClick = { onPlay(index) },
                        rowHeight = adaptive.rowHeight,
                    )
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

/** TV 屏上键盘：26 字母 + 数字（6 列流式）+ 删除/清空/搜索 */
@Composable
private fun TvKeyboard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
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
                        onClick = { onKeywordChange(keyword + ch) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyboardKey(
                label = "删除",
                onClick = { onKeywordChange(keyword.dropLast(1)) },
                modifier = Modifier.weight(1f),
                outlined = true,
            )
            KeyboardKey(
                label = "清空",
                onClick = { onKeywordChange("") },
                modifier = Modifier.weight(1f),
                outlined = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyboardKey(
                label = "搜索",
                onClick = onSearch,
                modifier = Modifier.weight(2f),
                accent = true,
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

// ═════════════════════════════════════════════════════════════════════════════
// 手机 / 平板：原版布局（输入框 + 音源 Chip + 结果列表）
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreenClassic(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
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
        OutlinedTextField(
            value = state.keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = adaptive.pagePadding, vertical = 8.dp),
            placeholder = { Text("搜索歌曲 / 歌手") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.keyword.isNotEmpty()) {
                    LxIconButton(
                        onClick = { onKeywordChange("") },
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = adaptive.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.enabledSources.forEach { source ->
                FilterChip(
                    selected = state.source == source,
                    onClick = { onSourceChange(source) },
                    label = { Text(Sources.label(source)) },
                    modifier = Modifier.lxFocusable(shape = RoundedCornerShape(8.dp)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.results.isEmpty() && state.error == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                state.results.isEmpty() && state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.uniqueKey }) { info ->
                        MusicRow(
                            info = info,
                            onClick = { onPlay(state.results.indexOf(info)) },
                            actions = actions,
                        )
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
}

/** 原版结果行：ListItem + 来源 AssistChip + 菜单按钮 */
@Composable
private fun MusicRow(
    info: MusicInfo,
    onClick: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
) {
    // 行矩形与行尾按钮几何重叠，方向查找不可达，用显式导航（右→菜单按钮，左→回行）
    val rowFocus = remember { FocusRequester() }
    val moreFocus = remember { FocusRequester() }
    ListItem(
        headlineContent = { Text(info.name, maxLines = 1) },
        supportingContent = {
            Text(
                buildString {
                    append(info.singer)
                    if (!info.albumName.isNullOrEmpty()) append(" - ").append(info.albumName)
                    if (!info.interval.isNullOrEmpty()) append(" · ").append(info.interval)
                },
                maxLines = 1,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 仅作来源标签：不参与遥控器焦点（否则只会出现灰色状态层的假焦点）
                AssistChip(
                    onClick = {},
                    label = { Text(Sources.label(info.source)) },
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                com.music.fm.ui.component.SongMoreButton(
                    info = info,
                    actions = actions,
                    focusRequester = moreFocus,
                    onBackFocus = { runCatching { rowFocus.requestFocus() } },
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(rowFocus)
            .lxRowFocusNav(moreFocus)
            .lxFocusable()
            .clickable(onClick = onClick),
    )
}
