package com.music.fm.ui.leaderboard

import com.music.fm.ui.component.requestFocusWhenReady
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.fm.data.model.BoardItem
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.Sources
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.component.LumioPagedSongPage
import com.music.fm.ui.component.LumioSongRow
import com.music.fm.ui.component.LumioTab
import com.music.fm.ui.component.LxIconButton
import com.music.fm.ui.component.lxFocusable
import com.music.fm.ui.theme.LumioBoardCard
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioPrimary

/**
 * 排行榜页 — 对齐 Lumio TV 排行榜面板（TVLeaderboardGridPanel）：
 *
 *  - 顶部：音源 Tab（下划线式，底部细分割线）
 *  - 榜单选择：方块 Grid（TV 5 列 / 平板 4 列 / 手机 2 列，
 *    宽高比 1.2、圆角 8、浅色底 + 主色粗体居中文字）
 *  - 榜内歌曲：统一 Lumio 歌曲行（前三名序号着色：红 / 橙 / 主色蓝）
 *
 * 点击榜单卡片进入榜内歌曲列表，顶部出现返回按钮；切换音源回到榜单 Grid。
 */
@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onSourceChange: (String) -> Unit,
    onBoardSelect: (BoardItem) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String? = null,
    /** 是否显示榜单方块 Grid（false = 独立歌曲页全屏，TV 下同时隐藏左侧导航） */
    tvShowBoardGrid: Boolean = true,
    onTvShowBoardGridChange: (Boolean) -> Unit = {},
    /** 左上角汉堡按钮回调（手机/平板歌曲独立页传入；null = 不显示汉堡） */
    onOpenDrawer: (() -> Unit)? = null,
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
    LaunchedEffect(shouldLoadMore, state.songs.size) {
        if (shouldLoadMore && state.songs.isNotEmpty()) onLoadMore()
    }

    // 榜单 Grid ↔ 榜内歌曲 的视图状态由外部持有（TV 全屏歌曲页需隐藏左侧导航栏）
    val showBoardGrid = tvShowBoardGrid
    val setShowBoardGrid = onTvShowBoardGridChange
    // 是否进过榜内歌曲页：只有“从歌曲页返回”时才把焦点交给第一张榜单卡片，
    // 通过侧栏 / 顶部 Tab 进入本页时不抢焦点
    var hadDetail by remember { mutableStateOf(false) }
    LaunchedEffect(showBoardGrid) { if (!showBoardGrid) hadDetail = true }
    var lastSource by remember { mutableStateOf(state.source) }
    if (state.source != lastSource) {
        lastSource = state.source
        setShowBoardGrid(true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // TV 详情视图是独立歌曲页（自带顶栏与返回键），不显示音源 Tab 行
        val showTopBar = showBoardGrid || !adaptive.isTv

        // ══ 顶部栏 ══
        // 榜单 Grid 页：音源 Tab（切换音源回 Grid 选榜）
        // 榜内歌曲页（手机/平板）：返回 + 排行榜名称（不显示音源平台名）
        if (showTopBar) {
            if (!showBoardGrid) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LxIconButton(
                        onClick = { setShowBoardGrid(true) },
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回榜单",
                    )
                    Text(
                        text = state.currentBoard?.name ?: "排行榜",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    )
                    if (state.total > 0) {
                        Text(
                            text = "${state.total} 首",
                            fontSize = 13.sp,
                            color = LumioFontLabel,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onOpenDrawer != null) {
                        LxIconButton(
                            onClick = onOpenDrawer,
                            imageVector = Icons.Default.Menu,
                            contentDescription = "打开导航",
                        )
                    }
                    state.enabledSources.forEach { source ->
                        LumioTab(
                            label = Sources.label(source),
                            selected = state.source == source,
                            onClick = { onSourceChange(source) },
                        )
                    }
                }
            }
        }

        // ══ 内容区 ══
        if (showBoardGrid) {
            BoardGrid(
                state = state,
                autoFocusFirst = hadDetail,
                onBoardSelect = { board ->
                    setShowBoardGrid(false)
                    onBoardSelect(board)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else if (adaptive.isTv) {
            // TV：独立双列分页歌曲页（每页 10 首，返回键回榜单 Grid）
            val pageSize = 10
            var page by remember(state.currentBoard?.id) { mutableStateOf(0) }
            val start = page * pageSize
            LumioPagedSongPage(
                title = state.currentBoard?.name ?: "排行榜",
                subTitle = if (state.total > 0) "共 ${state.total} 首" else null,
                songs = state.songs,
                page = page,
                hasNextPage = start + pageSize < maxOf(state.total, state.songs.size),
                onPageChange = { p ->
                    page = p
                    if ((p + 1) * pageSize > state.songs.size) onLoadMore()
                },
                onPlay = onPlay,
                actions = actions,
                playingKey = playingKey,
                onBack = { setShowBoardGrid(true) },
                rankColor = { idx ->
                    when (idx + 1) {
                        1 -> androidx.compose.ui.graphics.Color(0xFFE53935)
                        2 -> androidx.compose.ui.graphics.Color(0xFFFB8C00)
                        3 -> LumioPrimary
                        else -> null
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            LeaderboardSongList(
                adaptive = adaptive,
                state = state,
                listState = listState,
                onPlay = onPlay,
                onLoadMore = onLoadMore,
                actions = actions,
                playingKey = playingKey,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

// ── 榜单方块 Grid ────────────────────────────────────────────────────────────

/** 榜单卡片与焦点框的间距，让焦点框完整显示 */
private const val CARD_PADDING_DP = 4

@Composable
private fun BoardGrid(
    state: LeaderboardUiState,
    onBoardSelect: (BoardItem) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusFirst: Boolean = false,
) {
    val adaptive = LocalAdaptive.current
    // 从独立歌曲页返回时把焦点交给第一张卡片（遥控器方向键立即可用）。
    // 注意：不能再给整个网格容器套 focusable()——容器本身会成为一个“看不见的焦点目标”，
    // 从侧栏 / 顶部 Tab 用方向键进入时焦点会落在容器上，进不了卡片。
    val firstCardFocus = remember { FocusRequester() }
    if (autoFocusFirst) {
        LaunchedEffect(state.loadingBoards, state.boards.isNotEmpty()) {
            if (!state.loadingBoards && state.boards.isNotEmpty()) {
                firstCardFocus.requestFocusWhenReady()
            }
        }
    }
    Box(modifier) {
        when {
            state.loadingBoards -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.boards.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("暂无榜单", color = LumioFontLabel) }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(adaptive.gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(state.boards, key = { _, it -> it.id }) { index, board ->
                    BoardCard(
                        board = board,
                        onClick = { onBoardSelect(board) },
                        incomingFocus = if (index == 0) firstCardFocus else null,
                    )
                }
            }
        }
    }
}

/** 榜单方块卡片（对齐 Lumio BoardCard）：宽高比 1.2、圆角 8、主色粗体居中 */
@Composable
private fun BoardCard(
    board: BoardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    incomingFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(CARD_PADDING_DP.dp)
            .clip(shape)
            .background(LumioBoardCard)
            .then(if (incomingFocus != null) Modifier.focusRequester(incomingFocus) else Modifier)
            .lxFocusable(shape = shape)
            .clickable(onClick = onClick)
            .aspectRatio(1.2f)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = board.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = LumioPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 榜内歌曲列表 ─────────────────────────────────────────────────────────────

@Composable
private fun LeaderboardSongList(
    adaptive: com.music.fm.ui.adaptive.AdaptiveInfo,
    state: LeaderboardUiState,
    listState: LazyListState,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.loadingSongs && state.songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.songs.isEmpty() && state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

            // 平板：双列不分页；手机：单列。行内不显示音源标签
            adaptive.twoPane -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMoreGrid by remember {
                    derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= gridState.layoutInfo.totalItemsCount - 3
                    }
                }
                LaunchedEffect(shouldLoadMoreGrid, state.songs.size) {
                    if (shouldLoadMoreGrid && state.songs.isNotEmpty()) onLoadMore()
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                        LumioSongRow(
                            info = info,
                            actions = actions,
                            index = index + 1,
                            isActive = playingKey == info.uniqueKey,
                            rankColor = when (index + 1) {
                                1 -> androidx.compose.ui.graphics.Color(0xFFE53935)
                                2 -> androidx.compose.ui.graphics.Color(0xFFFB8C00)
                                3 -> LumioPrimary
                                else -> null
                            },
                            showSourceBadge = false,
                            onClick = { onPlay(index) },
                            rowHeight = adaptive.rowHeight,
                        )
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (state.loadedAll) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("已经到底啦", fontSize = 13.sp, color = LumioFontLabel)
                            }
                        }
                    }
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                    LumioSongRow(
                        info = info,
                        actions = actions,
                        index = index + 1,
                        isActive = playingKey == info.uniqueKey,
                        rankColor = when (index + 1) {
                            1 -> androidx.compose.ui.graphics.Color(0xFFE53935)
                            2 -> androidx.compose.ui.graphics.Color(0xFFFB8C00)
                            3 -> LumioPrimary
                            else -> null
                        },
                        showSourceBadge = false,
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
                if (state.loadedAll) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "已经到底啦",
                                fontSize = 13.sp,
                                color = LumioFontLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}
