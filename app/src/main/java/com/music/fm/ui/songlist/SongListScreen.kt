package com.music.fm.ui.songlist

import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.music.fm.ui.component.requestFocusWhenReady
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.music.fm.data.model.MusicInfo
import com.music.fm.data.model.SongListItem
import com.music.fm.data.model.SongListSort
import com.music.fm.data.model.SongListTag
import com.music.fm.data.model.Sources
import com.music.fm.ui.adaptive.AdaptiveInfo
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.component.LumioSongRow
import com.music.fm.ui.component.LumioTab
import com.music.fm.ui.component.LumioTagPill
import com.music.fm.ui.component.LxIconButton
import com.music.fm.ui.component.lxFocusable
import com.music.fm.ui.theme.LumioBorder
import com.music.fm.ui.theme.LumioDivider
import com.music.fm.ui.theme.LumioFont
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioFontPlaceholder
import com.music.fm.ui.theme.LumioKeyBackground
import com.music.fm.ui.theme.LumioPrimaryDark100
import com.music.fm.ui.theme.LumioPrimaryLight600

/**
 * 歌单页 — 对齐 Lumio TV 歌单面板（TVSongListPanel / FilterModal）：
 *
 *  - 顶部栏：音源 Tab（下划线式）+ 排序胶囊 + 「筛选」胶囊（打开居中筛选弹窗）
 *  - 内容：歌单封面网格（TV 5 列 / 平板 3~4 列 / 手机 2 列）
 *  - 详情：左侧歌单信息（含封面）+ 右侧统一 Lumio 歌曲行；手机单栏
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongListScreen(
    state: SongListUiState,
    onSourceChange: (String) -> Unit,
    onSortChange: (SongListSort) -> Unit,
    onTagChange: (SongListTag?) -> Unit,
    onOpenDetail: (SongListItem) -> Unit,
    onOpenDetailById: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onPlay: (Int) -> Unit,
    onCollectPlaylist: () -> Unit,
    onLoadMoreLists: () -> Unit,
    onLoadMoreDetail: () -> Unit,
    onAddToList: (com.music.fm.data.model.MusicInfo) -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String? = null,
    /** 左上角汉堡按钮回调（手机/平板歌曲独立页传入；null = 不显示汉堡） */
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    // 是否进过歌单详情：只有“从详情返回”时才把焦点交给第一张歌单卡片，
    // 通过侧栏进入本页时不抢焦点
    var hadDetail by remember { mutableStateOf(false) }
    LaunchedEffect(detail != null) { if (detail != null) hadDetail = true }
    if (detail != null) {
        val adaptive = LocalAdaptive.current
        if (adaptive.isTv) {
            // TV：独立双列分页歌曲页（每页 10 首，返回键回歌单网格）
            SongListTvPagedDetail(
                detail = detail,
                onBack = onCloseDetail,
                onPlay = onPlay,
                onLoadMore = onLoadMoreDetail,
                onCollect = onCollectPlaylist,
                actions = actions,
                playingKey = playingKey,
                modifier = modifier,
            )
        } else {
            SongListDetailContent(
                state = state,
                detail = detail,
                onBack = onCloseDetail,
                onPlay = onPlay,
                onLoadMore = onLoadMoreDetail,
                onCollect = onCollectPlaylist,
                actions = actions,
                playingKey = playingKey,
                onOpenDrawer = onOpenDrawer,
                modifier = modifier,
            )
        }
    } else {
        SongListGridContent(
            state = state,
            onSourceChange = onSourceChange,
            onSortChange = onSortChange,
            onTagChange = onTagChange,
            onOpenDetail = onOpenDetail,
            onOpenDetailById = onOpenDetailById,
            onLoadMore = onLoadMoreLists,
            autoFocusFirst = hadDetail,
            onOpenDrawer = onOpenDrawer,
            modifier = modifier,
        )
    }
}

// ── 主视图：顶部筛选栏 + 歌单网格 ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongListGridContent(
    state: SongListUiState,
    onSourceChange: (String) -> Unit,
    onSortChange: (SongListSort) -> Unit,
    onTagChange: (SongListTag?) -> Unit,
    onOpenDetail: (SongListItem) -> Unit,
    onOpenDetailById: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    autoFocusFirst: Boolean = false,
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

    var showFilterDialog by remember { mutableStateOf(false) }
    var showOpenDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // ══ 顶部栏：音源 Tab（TV 一行放下；手机两行）══
        if (adaptive.twoPane) {
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
                Spacer(Modifier.weight(1f))
                // 排序胶囊
                state.sortList.forEach { sort ->
                    LumioTagPill(
                        text = sort.name,
                        selected = state.sortId == sort.id,
                        onClick = { onSortChange(sort) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                // 打开：输入歌单链接 / ID 直接进详情（对齐 mobile 版「打开」）
                LumioTagPill(
                    text = "打开",
                    onClick = { showOpenDialog = true },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                // 标签筛选
                LumioTagPill(
                    text = if (state.tagId.isNotEmpty()) state.tagName else "筛选",
                    selected = state.tagId.isNotEmpty(),
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.padding(start = 4.dp),
                )
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
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.sortList.forEach { sort ->
                    LumioTagPill(
                        text = sort.name,
                        selected = state.sortId == sort.id,
                        onClick = { onSortChange(sort) },
                    )
                }
                Spacer(Modifier.weight(1f))
                LumioTagPill(
                    text = "打开",
                    onClick = { showOpenDialog = true },
                )
                LumioTagPill(
                    text = if (state.tagId.isNotEmpty()) state.tagName else "筛选",
                    selected = state.tagId.isNotEmpty(),
                    onClick = { showFilterDialog = true },
                )
            }
        }

        // ══ 歌单网格 ══
        SongListGridBody(
            adaptive = adaptive,
            state = state,
            gridState = gridState,
            onOpenDetail = onOpenDetail,
            autoFocusFirst = autoFocusFirst,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }

    // ══ 居中筛选弹窗（对齐 Lumio FilterModal）══
    if (showFilterDialog) {
        FilterDialog(
            state = state,
            onSelect = { tag ->
                showFilterDialog = false
                onTagChange(tag)
            },
            onClose = { showFilterDialog = false },
        )
    }

    // ══ 打开歌单弹窗：输入链接 / ID 后直接进详情（对齐 mobile 版 OpenList Modal）══
    if (showOpenDialog) {
        OpenListDialog(
            onOpen = { id ->
                showOpenDialog = false
                onOpenDetailById(id)
            },
            onClose = { showOpenDialog = false },
        )
    }
}

// ── 打开歌单弹窗 ─────────────────────────────────────────────────────────────

/** 提示文案（对齐 mobile 版 songlist_open_input_tip） */
private const val OPEN_LIST_TIP =
    "1. 不支持跨源打开歌单，请确认要打开的歌单与当前选择的歌单来源是否对应\n" +
        "2. 若遇到无法打开的歌单链接，欢迎反馈\n" +
        "3. 酷狗源歌单不支持用歌单 ID 或概念版链接打开，但支持用普通版链接或酷狗码打开"

@Composable
private fun OpenListDialog(
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val adaptive = LocalAdaptive.current
        val cardShape = RoundedCornerShape(14.dp)
        var text by remember { mutableStateOf("") }
        val inputFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { inputFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .then(
                    if (adaptive.isTv) Modifier.widthIn(max = 560.dp)
                    else Modifier.fillMaxWidth(0.85f)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, cardShape)
                .clip(cardShape)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                singleLine = true,
                placeholder = { Text("输入歌单链接或歌单 ID", color = LumioFontPlaceholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocus),
            )
            Text(
                text = OPEN_LIST_TIP,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = LumioFontLabel,
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OpenDialogButton(
                    label = "取消",
                    enabled = true,
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                )
                OpenDialogButton(
                    label = "确认",
                    enabled = text.isNotBlank(),
                    onClick = { onOpen(text) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 打开歌单弹窗底部大按钮（对齐图二 取消/确认 双按钮样式） */
@Composable
private fun OpenDialogButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (enabled) LumioPrimaryLight600 else LumioKeyBackground)
            .lxFocusable(shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) LumioPrimaryDark100 else LumioFontPlaceholder,
        )
    }
}

// ── 居中筛选弹窗 ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(
    state: SongListUiState,
    onSelect: (SongListTag?) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val cardShape = RoundedCornerShape(14.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, cardShape)
                .clip(cardShape)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "筛选歌单",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LumioFont,
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
            ) {
                // 全部歌单
                LumioTagPill(
                    text = "全部歌单",
                    selected = state.tagId.isEmpty(),
                    onClick = { onSelect(null) },
                )
                if (state.hotTags.isNotEmpty()) {
                    TagGroup(
                        name = "热门标签",
                        tags = state.hotTags,
                        selectedId = state.tagId,
                        onSelect = onSelect,
                    )
                }
                state.tagGroups.forEach { group ->
                    TagGroup(
                        name = group.name,
                        tags = group.list,
                        selectedId = state.tagId,
                        onSelect = onSelect,
                    )
                }
            }
            // 底部分割线 + 取消按钮（右下）
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(1.dp)
                    .background(LumioDivider),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                LumioTagPill(
                    text = "取消",
                    onClick = onClose,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(
    name: String,
    tags: List<SongListTag>,
    selectedId: String,
    onSelect: (SongListTag?) -> Unit,
) {
    if (name.isNotEmpty()) {
        Text(
            text = name,
            fontSize = 13.sp,
            color = LumioFontLabel,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            LumioTagPill(
                text = tag.name,
                selected = tag.id == selectedId,
                onClick = { onSelect(tag) },
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

// ── 歌单封面网格 ─────────────────────────────────────────────────────────────

@Composable
private fun SongListGridBody(
    adaptive: AdaptiveInfo,
    state: SongListUiState,
    gridState: LazyGridState,
    onOpenDetail: (SongListItem) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusFirst: Boolean = false,
) {
    val itemSpacing = if (adaptive.isTv) 16.dp else 12.dp
    // 从详情返回时把焦点交给第一张卡片（遥控器方向键立即可用）。
    // 不能给整个网格容器套 focusable()：容器会成为“看不见的焦点目标”，
    // 从侧栏 / 顶部 Tab 用方向键进入时焦点落在容器上，进不了卡片。
    val firstCardFocus = remember { FocusRequester() }
    if (autoFocusFirst) {
        LaunchedEffect(state.lists.isNotEmpty()) {
            if (state.lists.isNotEmpty()) firstCardFocus.requestFocusWhenReady()
        }
    }
    Box(modifier) {
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalArrangement = Arrangement.spacedBy(itemSpacing + 4.dp),
            ) {
                itemsIndexed(state.lists, key = { _, it -> "${it.source}-${it.id}" }) { index, item ->
                    PlaylistCard(
                        item = item,
                        onClick = { onOpenDetail(item) },
                        incomingFocus = if (index == 0) firstCardFocus else null,
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
    }
}

/** 歌单卡片：圆角 8 封面 + 播放量角标 + 两行标题 + 来源/作者 */
@Composable
private fun PlaylistCard(
    item: SongListItem,
    onClick: () -> Unit,
    incomingFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (incomingFocus != null) Modifier.focusRequester(incomingFocus) else Modifier)
            .lxFocusable(shape = shape)
            .clickable(onClick = onClick)
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
                    .clip(shape),
            )
            if (!item.playCount.isNullOrEmpty()) {
                Text(
                    text = "▶ ${item.playCount}",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                )
            }
        }
        Text(
            text = item.name,
            fontSize = 14.sp,
            color = LumioFont,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = buildString {
                append(Sources.label(item.source))
                if (!item.author.isNullOrEmpty()) append(" · ").append(item.author)
            },
            fontSize = 12.sp,
            color = LumioFontLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── 详情视图：歌单信息 + 统一歌曲行 ─────────────────────────────────────────

/** TV 详情：独立双列分页歌曲页（每页 10 首，返回键回歌单网格） */
@Composable
private fun SongListTvPagedDetail(
    detail: SongListDetailState,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onCollect: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String?,
    modifier: Modifier = Modifier,
) {
    val pageSize = 10
    var page by remember(detail.id) { mutableStateOf(0) }
    val start = page * pageSize
    com.music.fm.ui.component.LumioPagedSongPage(
        title = detail.info?.name ?: "歌单详情",
        subTitle = if (detail.total > 0) "共 ${detail.total} 首" else null,
        songs = detail.songs,
        page = page,
        hasNextPage = start + pageSize < maxOf(detail.total, detail.songs.size),
        onPageChange = { p ->
            page = p
            if ((p + 1) * pageSize > detail.songs.size) onLoadMore()
        },
        onPlay = onPlay,
        actions = actions,
        playingKey = playingKey,
        onBack = onBack,
        topBarActions = {
            // 收藏歌单：后台拉取歌单全部歌曲后弹目标歌单选择（对齐 mobile 版 handleCollect）
            if (detail.songs.isNotEmpty()) {
                if (detail.collectPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp).padding(2.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    LxIconButton(
                        onClick = onCollect,
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "收藏歌单",
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SongListDetailContent(
    state: SongListUiState,
    detail: SongListDetailState,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onCollect: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String?,
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val listState = rememberLazyListState()
    // 详情是内部状态而非导航目的地：遥控器返回键直接退出详情（TV 无界面返回按钮）
    androidx.activity.compose.BackHandler { onBack() }
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
        // 平板 / TV：左侧歌单信息（含返回），右侧统一歌曲列表
        Row(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(if (adaptive.isTv) 320.dp else 260.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                if (!adaptive.isTv) {
                    LxIconButton(
                        onClick = onBack,
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                    // 收藏歌单（平板两栏布局）：入口放歌单信息列
                    if (detail.songs.isNotEmpty()) {
                        if (detail.collectPreparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(top = 6.dp).size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            LumioTagPill(
                                text = "收藏歌单",
                                onClick = onCollect,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LumioFont,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.info?.author.isNullOrEmpty()) {
                    Text(
                        text = detail.info?.author ?: "",
                        fontSize = 12.sp,
                        color = LumioFontLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (detail.total > 0) {
                    Text(
                        text = "共 ${detail.total} 首",
                        fontSize = 12.sp,
                        color = LumioFontLabel,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(LumioDivider),
            )
            SongListDetailBody(
                adaptive = adaptive,
                detail = detail,
                listState = listState,
                onPlay = onPlay,
                onLoadMore = onLoadMore,
                actions = actions,
                playingKey = playingKey,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LumioFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 收藏歌单：后台拉取歌单全部歌曲后弹目标歌单选择（对齐 mobile 版 handleCollect）
                if (detail.songs.isNotEmpty()) {
                    if (detail.collectPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 14.dp).size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        LxIconButton(
                            onClick = onCollect,
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "收藏歌单",
                        )
                    }
                }
                if (detail.total > 0) {
                    Text(
                        text = "共 ${detail.total} 首",
                        fontSize = 12.sp,
                        color = LumioFontLabel,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            SongListDetailBody(
                adaptive = adaptive,
                detail = detail,
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

@Composable
private fun SongListDetailBody(
    adaptive: AdaptiveInfo,
    detail: SongListDetailState,
    listState: LazyListState,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            detail.loading && detail.songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            detail.songs.isEmpty() && detail.loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            // 平板：双列不分页；手机：单列。行内不显示音源标签
            adaptive.twoPane -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMoreGrid by remember {
                    derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= gridState.layoutInfo.totalItemsCount - 3
                    }
                }
                LaunchedEffect(shouldLoadMoreGrid, detail.songs.size) {
                    if (shouldLoadMoreGrid && detail.songs.isNotEmpty()) onLoadMore()
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(detail.songs, key = { _, it -> it.uniqueKey }) { index, info ->
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
                    if (detail.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                    if (detail.loadedAll) {
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
                itemsIndexed(detail.songs, key = { _, it -> it.uniqueKey }) { index, info ->
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
                            Text("已经到底啦", fontSize = 13.sp, color = LumioFontLabel)
                        }
                    }
                }
            }
        }
    }
}
