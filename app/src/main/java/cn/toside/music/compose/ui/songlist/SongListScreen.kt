package cn.toside.music.compose.ui.songlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.toside.music.compose.data.model.SongListSort
import cn.toside.music.compose.data.model.SongListTag
import cn.toside.music.compose.data.model.Sources

/**
 * 歌单页（对齐 mobile 版 SongList）：
 * 五平台源切换 + 排序/标签筛选 + 歌单封面网格；点击歌单进入详情歌曲列表（可返回）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    state: SongListUiState,
    onSourceChange: (String) -> Unit,
    onSortChange: (SongListSort) -> Unit,
    onTagChange: (SongListTag?) -> Unit,
    onOpenDetail: (cn.toside.music.compose.data.model.SongListItem) -> Unit,
    onCloseDetail: () -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMoreLists: () -> Unit,
    onLoadMoreDetail: () -> Unit,
    onAddToList: (cn.toside.music.compose.data.model.MusicInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    if (detail != null) {
        SongListDetailContent(
            state = state,
            detail = detail,
            onBack = onCloseDetail,
            onPlay = onPlay,
            onAddToList = onAddToList,
            onLoadMore = onLoadMoreDetail,
            modifier = modifier,
        )
    } else {
        SongListGridContent(
            state = state,
            onSourceChange = onSourceChange,
            onSortChange = onSortChange,
            onTagChange = onTagChange,
            onOpenDetail = onOpenDetail,
            onLoadMore = onLoadMoreLists,
            modifier = modifier,
        )
    }
}

// ── 主视图：歌单网格 ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongListGridContent(
    state: SongListUiState,
    onSourceChange: (String) -> Unit,
    onSortChange: (SongListSort) -> Unit,
    onTagChange: (SongListTag?) -> Unit,
    onOpenDetail: (cn.toside.music.compose.data.model.SongListItem) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.lists.size) {
        if (shouldLoadMore && state.lists.isNotEmpty()) onLoadMore()
    }

    var showTagSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // 平台源切换
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

        // 排序 + 标签选择
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.sortList.forEach { sort ->
                FilterChip(
                    selected = state.sortId == sort.id,
                    onClick = { onSortChange(sort) },
                    label = { Text(sort.name) },
                )
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = state.tagId.isNotEmpty(),
                onClick = { showTagSheet = true },
                label = {
                    Text(if (state.tagId.isNotEmpty()) state.tagName else "标签")
                },
                trailingIcon = {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择标签")
                },
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loadingLists && state.lists.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.lists.isEmpty() && state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.lists, key = { "${it.source}-${it.id}" }) { item ->
                        PlaylistCard(item = item, onClick = { onOpenDetail(item) })
                    }
                    if (state.loadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (state.loadedAll) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
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

    if (showTagSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTagSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            TagSheetContent(
                state = state,
                onSelect = { tag ->
                    showTagSheet = false
                    onTagChange(tag)
                },
            )
        }
    }
}

@Composable
private fun TagSheetContent(
    state: SongListUiState,
    onSelect: (SongListTag?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "全部歌单",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.tagId.isEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (state.hotTags.isNotEmpty()) {
            item {
                Text(
                    "热门标签",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item {
                TagWrapRow(
                    tags = state.hotTags,
                    selectedId = state.tagId,
                    onSelect = onSelect,
                )
            }
        }
        state.tagGroups.forEach { group ->
            item(key = "group_${group.name}") {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item(key = "tags_${group.name}") {
                TagWrapRow(
                    tags = group.list,
                    selectedId = state.tagId,
                    onSelect = onSelect,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TagWrapRow(
    tags: List<SongListTag>,
    selectedId: String,
    onSelect: (SongListTag?) -> Unit,
) {
    // FlowRow 需要实验 API；这里用多列网格近似换行布局
    val rows = tags.chunked(4)
    Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        rows.forEach { rowTags ->
            Row {
                rowTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id == selectedId,
                        onClick = { onSelect(tag) },
                        label = {
                            Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier
                            .padding(2.dp)
                            .weight(1f),
                    )
                }
                repeat(4 - rowTags.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    item: cn.toside.music.compose.data.model.SongListItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = item.img,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (!item.playCount.isNullOrEmpty()) {
                Text(
                    text = "▶ ${item.playCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = buildString {
                append(Sources.label(item.source))
                if (!item.author.isNullOrEmpty()) append(" · ").append(item.author)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── 详情视图：歌单内歌曲 ─────────────────────────────────────────

@Composable
private fun SongListDetailContent(
    state: SongListUiState,
    detail: SongListDetailState,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onAddToList: (cn.toside.music.compose.data.model.MusicInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, detail.songs.size) {
        if (shouldLoadMore && detail.songs.isNotEmpty()) onLoadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏：返回 + 歌单名 + 数量
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = detail.info?.name ?: "歌单详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (detail.total > 0) {
                Text(
                    text = "共 ${detail.total} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                detail.loading && detail.songs.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                detail.songs.isEmpty() && state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!detail.info?.img.isNullOrEmpty()) {
                                AsyncImage(
                                    model = detail.info?.img,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(64.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    text = detail.info?.name ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!detail.info?.author.isNullOrEmpty()) {
                                    Text(
                                        text = detail.info?.author ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(detail.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                        SongRow(
                            index = index + 1,
                            info = info,
                            onClick = { onPlay(index) },
                            onAddToList = { onAddToList(info) },
                        )
                    }
                    if (detail.loadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (detail.loadedAll) {
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
}

@Composable
private fun SongRow(
    index: Int,
    info: cn.toside.music.compose.data.model.MusicInfo,
    onClick: () -> Unit,
    onAddToList: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        leadingContent = {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
