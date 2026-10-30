package cn.toside.music.compose.ui.songlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.toside.music.compose.data.model.Sources
import cn.toside.music.compose.data.model.SongListSort
import cn.toside.music.compose.data.model.SongListTag
import cn.toside.music.compose.ui.adaptive.AdaptiveInfo
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable

/**
 * 歌单页（对齐 mobile 版 SongList）：
 * 五平台源切换 + 排序/标签筛选 + 歌单封面网格；点击歌单进入详情歌曲列表（可返回）。
 *
 *  - 手机     ：单栏，标签筛选走底部弹层，网格 2 列
 *  - 平板 / TV：左右分栏，音源 / 排序 / 标签常驻左侧栏，网格 3~5 列
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
    val adaptive = LocalAdaptive.current
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

    if (adaptive.twoPane) {
        // 不做 contentMaxWidth 居中限制，铺满导航栏右侧全部空间
        Row(modifier = modifier.fillMaxSize()) {
            SongListFilterPane(
                adaptive = adaptive,
                state = state,
                onSourceChange = onSourceChange,
                onSortChange = onSortChange,
                onTagChange = onTagChange,
                modifier = Modifier.fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SongListGridHeader(
                    adaptive = adaptive,
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                )
                SongListGridBody(
                    adaptive = adaptive,
                    state = state,
                    gridState = gridState,
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    } else {
        // 手机：单栏 + 标签底部弹层
        var showTagSheet by remember { mutableStateOf(false) }
        Column(modifier = modifier.fillMaxSize()) {
            SourceChipRow(
                sources = state.enabledSources,
                selected = state.source,
                onSelect = onSourceChange,
                modifier = Modifier.padding(horizontal = adaptive.pagePadding, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = adaptive.pagePadding, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.sortList.forEach { sort ->
                    FilterChip(
                        selected = state.sortId == sort.id,
                        onClick = { onSortChange(sort) },
                        label = { Text(sort.name) },
                        modifier = Modifier.lxFocusable(),
                    )
                }
                FilterChip(
                    selected = state.tagId.isNotEmpty(),
                    onClick = { showTagSheet = true },
                    label = {
                        Text(if (state.tagId.isNotEmpty()) state.tagName else "标签")
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择标签")
                    },
                    modifier = Modifier.lxFocusable(),
                )
            }
            SongListGridBody(
                adaptive = adaptive,
                state = state,
                gridState = gridState,
                onOpenDetail = onOpenDetail,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
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
}

/** 平板 / TV 右侧内容区顶部：当前筛选条件说明 */
@Composable
private fun SongListGridHeader(
    adaptive: AdaptiveInfo,
    state: SongListUiState,
    modifier: Modifier = Modifier,
) {
    val sortName = state.sortList.firstOrNull { it.id == state.sortId }?.name ?: ""
    Row(
        modifier = modifier.padding(horizontal = adaptive.pagePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.tagId.isNotEmpty()) state.tagName else "全部歌单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(Sources.label(state.source))
                    if (sortName.isNotEmpty()) append(" · ").append(sortName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 平板 / TV 左侧筛选栏：音源 + 排序 + 标签（可滚动、全部可聚焦） */
@Composable
private fun SongListFilterPane(
    adaptive: AdaptiveInfo,
    state: SongListUiState,
    onSourceChange: (String) -> Unit,
    onSortChange: (SongListSort) -> Unit,
    onTagChange: (SongListTag?) -> Unit,
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
            text = "排序",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.sortList.forEach { sort ->
                FilterChip(
                    selected = state.sortId == sort.id,
                    onClick = { onSortChange(sort) },
                    label = { Text(sort.name) },
                    modifier = Modifier.lxFocusable(),
                )
            }
        }
        Text(
            text = "标签",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            FilterChip(
                selected = state.tagId.isEmpty(),
                onClick = { onTagChange(null) },
                label = { Text("全部歌单") },
                modifier = Modifier
                    .fillMaxWidth()
                    .lxFocusable(),
            )
            if (state.hotTags.isNotEmpty()) {
                Text(
                    "热门标签",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                TagWrapRow(
                    tags = state.hotTags,
                    selectedId = state.tagId,
                    onSelect = onTagChange,
                    columns = 2,
                )
            }
            state.tagGroups.forEach { group ->
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
                TagWrapRow(
                    tags = group.list,
                    selectedId = state.tagId,
                    onSelect = onTagChange,
                    columns = 2,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

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

/** 歌单封面网格（三端共用；列数跟随 [AdaptiveInfo.gridColumns]） */
@Composable
private fun SongListGridBody(
    adaptive: AdaptiveInfo,
    state: SongListUiState,
    gridState: LazyGridState,
    onOpenDetail: (cn.toside.music.compose.data.model.SongListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemSpacing = if (adaptive.isTv) 20.dp else 12.dp
    Box(modifier = modifier) {
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
                columns = GridCells.Fixed(adaptive.gridColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = adaptive.pagePadding, vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                items(state.lists, key = { "${it.source}-${it.id}" }) { item ->
                    PlaylistCard(item = item, onClick = { onOpenDetail(item) })
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
private fun TagSheetContent(
    state: SongListUiState,
    onSelect: (SongListTag?) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(null) }
                    .lxFocusable()
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

/**
 * 标签换行布局：FlowRow 需要实验 API，这里用 [columns] 列网格近似。
 * 平板 / TV 侧栏较窄，传 2 列；手机弹层保持 4 列。
 */
@Composable
private fun TagWrapRow(
    tags: List<SongListTag>,
    selectedId: String,
    onSelect: (SongListTag?) -> Unit,
    columns: Int = 4,
) {
    val rows = tags.chunked(columns)
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
                            .weight(1f)
                            .lxFocusable(),
                    )
                }
                repeat(columns - rowTags.size) {
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
            .clickable(onClick = onClick)
            .lxFocusable()
            .padding(4.dp),
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
    val adaptive = LocalAdaptive.current
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

    if (adaptive.twoPane) {
        // 平板 / TV：左侧歌单信息（含返回按钮），右侧歌曲列表；铺满全部空间
        Row(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(if (adaptive.isTv) 320.dp else 260.dp)
                    .fillMaxHeight()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                    LxIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!detail.info?.img.isNullOrEmpty()) {
                        AsyncImage(
                            model = detail.info?.img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        text = detail.info?.name ?: "歌单详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!detail.info?.author.isNullOrEmpty()) {
                        Text(
                            text = detail.info?.author ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (detail.total > 0) {
                        Text(
                            text = "共 ${detail.total} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SongListDetailBody(
                        adaptive = adaptive,
                        state = state,
                        detail = detail,
                        listState = listState,
                        onPlay = onPlay,
                        onAddToList = onAddToList,
                        showHeader = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            // 顶栏：返回 + 歌单名 + 数量
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxIconButton(
                    onClick = onBack,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
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
            SongListDetailBody(
                adaptive = adaptive,
                state = state,
                detail = detail,
                listState = listState,
                onPlay = onPlay,
                onAddToList = onAddToList,
                showHeader = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SongListDetailBody(
    adaptive: AdaptiveInfo,
    state: SongListUiState,
    detail: SongListDetailState,
    listState: LazyListState,
    onPlay: (Int) -> Unit,
    onAddToList: (cn.toside.music.compose.data.model.MusicInfo) -> Unit,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            detail.loading && detail.songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            detail.songs.isEmpty() && state.error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = adaptive.pagePadding, vertical = 8.dp),
            ) {
                if (showHeader) {
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

@Composable
private fun SongRow(
    index: Int,
    info: cn.toside.music.compose.data.model.MusicInfo,
    onClick: () -> Unit,
    onAddToList: () -> Unit,
) {
    val adaptive = LocalAdaptive.current
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
                AssistChip(
                    onClick = {},
                    label = { Text(Sources.label(info.source)) },
                    modifier = Modifier.lxFocusable(),
                )
                LxIconButton(
                    onClick = onAddToList,
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "收藏到歌单",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = adaptive.rowHeight)
            .clickable(onClick = onClick)
            .lxFocusable(),
    )
}
