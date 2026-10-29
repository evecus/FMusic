package cn.toside.music.compose.ui.leaderboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.toside.music.compose.data.model.BoardItem
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources

/**
 * 排行榜页（对齐 mobile 版 Leaderboard Vertical 布局）：
 * 顶部源切换 + 当前榜单标题（点击弹出榜单选择）+ 榜内歌曲列表（带排名，点击播放）。
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
    modifier: Modifier = Modifier,
) {
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

    var showBoardSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // 平台源切换（kw/kg/tx/wy/mg）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.enabledSources.forEach { source ->
                FilterChip(
                    selected = state.source == source,
                    onClick = { onSourceChange(source) },
                    label = { Text(Sources.label(source)) },
                )
            }
        }

        // 当前榜单标题（点击弹出榜单列表）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showBoardSheet = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.currentBoard?.name ?: "选择榜单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "切换榜单")
            Spacer(Modifier.weight(1f))
            if (state.total > 0) {
                Text(
                    text = "共 ${state.total} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
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

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                        RankedMusicRow(
                            rank = index + 1,
                            info = info,
                            onClick = { onPlay(index) },
                            onAddToList = { onAddToList(info) },
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
                    val active = board.id == state.currentBoard?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showBoardSheet = false
                                onBoardSelect(board)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = board.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Bold else null,
                            maxLines = 1,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RankedMusicRow(rank: Int, info: MusicInfo, onClick: () -> Unit, onAddToList: () -> Unit) {
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
                AssistChip(onClick = {}, label = { Text(Sources.label(info.source)) })
                IconButton(onClick = onAddToList) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "收藏到歌单")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
