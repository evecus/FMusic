package cn.toside.music.compose.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.toside.music.compose.data.model.BoardItem
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources
import cn.toside.music.compose.ui.adaptive.AdaptiveInfo
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable

/**
 * 排行榜页（对齐 mobile 版 Leaderboard Vertical 布局）：
 *  - 手机  ：单栏 —— 顶部源切换 + 榜单标题（点击弹出榜单选择）+ 榜内歌曲列表
 *  - 平板 / TV：左右分栏 —— 左侧常驻音源 + 榜单列表（遥控器直接上下选择），右侧榜内歌曲列表
 *
 * 两种形态下所有可点击元素均挂在 [lxFocusable] / [LxIconButton] 上，保证 TV 遥控器可达。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onSourceChange: (String) -> Unit,
    onBoardSelect: (BoardItem) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    actions: cn.toside.music.compose.ui.component.SongRowActions,
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

    // 手机端：榜单通过底部弹层选择（弹层内条目同样可聚焦）
    var showBoardSheet by remember { mutableStateOf(false) }

    if (adaptive.twoPane) {
        // 不做 contentMaxWidth 居中限制，铺满导航栏右侧全部空间
        Row(modifier = modifier.fillMaxSize()) {
            LeaderboardSidePane(
                adaptive = adaptive,
                state = state,
                onSourceChange = onSourceChange,
                onBoardSelect = onBoardSelect,
                modifier = Modifier.fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                LeaderboardBoardHeader(
                    adaptive = adaptive,
                    state = state,
                    onOpenBoards = null,
                )
                LeaderboardSongList(
                    adaptive = adaptive,
                    state = state,
                    listState = listState,
                    onPlay = onPlay,
                    actions = actions,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            SourceChipRow(
                sources = state.enabledSources,
                selected = state.source,
                onSelect = onSourceChange,
                modifier = Modifier.padding(horizontal = adaptive.pagePadding, vertical = 4.dp),
            )
            LeaderboardBoardHeader(
                adaptive = adaptive,
                state = state,
                onOpenBoards = { showBoardSheet = true },
            )
            LeaderboardSongList(
                adaptive = adaptive,
                state = state,
                listState = listState,
                onPlay = onPlay,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    if (showBoardSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBoardSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = "${Sources.label(state.source)} · 榜单",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.boards, key = { it.id }) { board ->
                    BoardRow(
                        board = board,
                        active = board.id == state.currentBoard?.id,
                        onClick = {
                            showBoardSheet = false
                            onBoardSelect(board)
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ── 公共片段 ────────────────────────────────────────────────────

/** 平台源切换（横向可滚动，避免 TV 端溢出；每一项可聚焦） */
@Composable
private fun SourceChipRow(
    sources: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEach { source ->
            FilterChip(
                selected = selected == source,
                onClick = { onSelect(source) },
                label = { Text(Sources.label(source)) },
                modifier = Modifier.lxFocusable(),
            )
        }
    }
}

/**
 * 当前榜单标题行。
 * [onOpenBoards] 为 null 时表示榜单列表已常驻在左侧（平板 / TV），标题不再需要点击展开。
 */
@Composable
private fun LeaderboardBoardHeader(
    adaptive: AdaptiveInfo,
    state: LeaderboardUiState,
    onOpenBoards: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val clickableModifier = if (onOpenBoards != null) {
        Modifier
            .clickable(onClick = onOpenBoards)
            .lxFocusable()
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = adaptive.pagePadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.currentBoard?.name ?: "选择榜单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onOpenBoards != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "切换榜单")
            }
        }
        if (state.total > 0) {
            Text(
                text = "共 ${state.total} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 平板 / TV 左侧常驻栏：音源 + 榜单列表 */
@Composable
private fun LeaderboardSidePane(
    adaptive: AdaptiveInfo,
    state: LeaderboardUiState,
    onSourceChange: (String) -> Unit,
    onBoardSelect: (BoardItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(if (adaptive.isTv) 320.dp else 260.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        SourceChipRow(
            sources = state.enabledSources,
            selected = state.source,
            onSelect = onSourceChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${Sources.label(state.source)} · 榜单",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loadingBoards && state.boards.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.boards.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无榜单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.boards, key = { it.id }) { board ->
                        BoardRow(
                            board = board,
                            active = board.id == state.currentBoard?.id,
                            onClick = { onBoardSelect(board) },
                        )
                    }
                }
            }
        }
    }
}

/** 单条榜单（侧栏与底部弹层共用） */
@Composable
private fun BoardRow(
    board: BoardItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .lxFocusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = board.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 榜内歌曲列表（三端共用） */
@Composable
private fun LeaderboardSongList(
    adaptive: AdaptiveInfo,
    state: LeaderboardUiState,
    listState: LazyListState,
    onPlay: (Int) -> Unit,
    actions: cn.toside.music.compose.ui.component.SongRowActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.loadingBoards || (state.loadingSongs && state.songs.isEmpty()) -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.songs.isEmpty() && state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

            state.songs.isEmpty() && state.boards.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无榜单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = adaptive.pagePadding, vertical = 4.dp),
            ) {
                itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                    RankedMusicRow(
                        rank = index + 1,
                        info = info,
                        onClick = { onPlay(index) },
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedMusicRow(
    rank: Int,
    info: MusicInfo,
    onClick: () -> Unit,
    actions: cn.toside.music.compose.ui.component.SongRowActions,
) {
    val adaptive = LocalAdaptive.current
    androidx.compose.material3.ListItem(
        leadingContent = {
            Text(
                text = rank.toString(),
                fontSize = 16.sp,
                fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
                color = when (rank) {
                    1 -> MaterialTheme.colorScheme.error
                    2 -> MaterialTheme.colorScheme.tertiary
                    3 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
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
                AssistChip(
                    onClick = {},
                    label = { Text(Sources.label(info.source)) },
                    modifier = Modifier.lxFocusable(),
                )
                cn.toside.music.compose.ui.component.SongMoreButton(info = info, actions = actions)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = adaptive.rowHeight)
            .clickable(onClick = onClick)
            .lxFocusable(),
    )
}
