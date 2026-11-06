package com.music.fm.ui.mylist

import com.music.fm.ui.component.overlayPadding
import com.music.fm.ui.component.requestFocusWhenReady
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.fm.data.MyListMeta
import com.music.fm.ui.adaptive.AdaptiveInfo
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.component.LumioPagedSongPage
import com.music.fm.ui.component.LumioSongRow
import com.music.fm.ui.component.LxIconButton
import com.music.fm.ui.component.lxFocusable
import com.music.fm.ui.theme.LumioBoardCard
import com.music.fm.ui.theme.LumioDivider
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioPrimary

/**
 * “收藏”页 — 对齐 Lumio 收藏面板（TVMyListGridPanel），全平台统一：
 *
 *  - 歌单选择：方块 Grid（手机 2 列 / 平板 3~4 列 / TV 5 列，
 *    宽高比 1.2、圆角 8、浅底 + 主色粗体居中），
 *    长按卡片打开居中操作弹窗（新建 / 重命名 / 删除 + 取消；
 *    TV 为遥控器焦点落在卡片上长按确认键）；
 *  - 点击卡片进入独立歌曲列表页：双列、每页 10 首、底部分页条，
 *    顶栏含播放全部 / 清空 / 刷新MV，返回键回歌单 Grid。
 */
@Composable
fun MyListScreen(
    state: MyListUiState,
    onSelectList: (String) -> Unit,
    onCreateList: (String) -> Unit,
    onRenameList: (String, String) -> Unit,
    onRemoveList: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onClearList: () -> Unit,
    onRefreshMv: () -> Unit,
    onToastConsumed: () -> Unit,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String? = null,
    /** 是否显示歌单方块 Grid（false = 独立歌曲页全屏，TV 下同时隐藏左侧导航） */
    tvShowGrid: Boolean = true,
    onTvShowGridChange: (Boolean) -> Unit = {},
    /** 左上角汉堡按钮回调（手机/平板歌曲独立页传入；null = 不显示汉堡） */
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MyListMeta?>(null) }
    var removeTarget by remember { mutableStateOf<MyListMeta?>(null) }

    // 是否存在 mv 字段缺失的网易/酷狗歌曲（mobile listMvRefresh 同款判断）
    val hasMvMissing = state.songs.any { song ->
        (song.source == "wy" || song.source == "kg") &&
            (song.mv.isNullOrEmpty() || (song.source == "wy" && song.mv == "0"))
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            onToastConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MyListContent(
            state = state,
            adaptive = adaptive,
            onSelectList = onSelectList,
            onPlay = onPlay,
            onPlayAll = onPlayAll,
            onRemoveSong = onRemoveSong,
            onMoveSong = onMoveSong,
            onClearList = onClearList,
            onRefreshMv = if (hasMvMissing) onRefreshMv else null,
            actions = actions,
            playingKey = playingKey,
            showGrid = tvShowGrid,
            onShowGridChange = onTvShowGridChange,
            onOpenDrawer = onOpenDrawer,
            onCreate = { showCreateDialog = true },
            onRename = { renameTarget = it },
            onRemove = { removeTarget = it },
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ── 弹窗 ──
    if (showCreateDialog) {
        NameInputDialog(
            title = "新建歌单",
            initial = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                onCreateList(it)
                showCreateDialog = false
            },
        )
    }
    renameTarget?.let { target ->
        NameInputDialog(
            title = "重命名歌单",
            initial = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenameList(target.id, it)
                renameTarget = null
            },
        )
    }
    removeTarget?.let { target ->
        RemoveListDialog(
            name = target.name,
            onDismiss = { removeTarget = null },
            onConfirm = {
                onRemoveList(target.id)
                removeTarget = null
            },
        )
    }
}

// ── 歌单方块 Grid ↔ 独立分页歌曲页 ───────────────────────────────────────────

@Composable
private fun MyListContent(
    state: MyListUiState,
    adaptive: AdaptiveInfo,
    onSelectList: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onClearList: () -> Unit,
    onRefreshMv: (() -> Unit)?,
    actions: com.music.fm.ui.component.SongRowActions,
    playingKey: String?,
    showGrid: Boolean,
    onShowGridChange: (Boolean) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    onCreate: () -> Unit,
    onRename: (MyListMeta) -> Unit,
    onRemove: (MyListMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuTarget by remember { mutableStateOf<MyListMeta?>(null) }
    val activeList = state.lists.firstOrNull { it.id == state.activeListId }
    // 只有“从歌曲页返回”时才把焦点交给第一张卡片，通过侧栏进入本页时不抢焦点
    var hadDetail by remember { mutableStateOf(false) }
    LaunchedEffect(showGrid) { if (!showGrid) hadDetail = true }

    if (showGrid) {
        Column(modifier = modifier.fillMaxSize()) {
            // 顶部固定标题 + 新建入口（Lumio topBar：细分割线 + 粗体标题）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOpenDrawer != null) {
                    LxIconButton(
                        onClick = onOpenDrawer,
                        imageVector = Icons.Default.Menu,
                        contentDescription = "打开导航",
                    )
                }
                Text(
                    text = "收藏",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                )
                LxIconButton(
                    onClick = onCreate,
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建歌单",
                )
            }
            HorizontalDivider(color = LumioDivider)
            MyListGrid(
                lists = state.lists,
                columns = adaptive.gridColumns,
                onSelect = { list ->
                    onShowGridChange(false)
                    onSelectList(list.id)
                },
                onMenu = { menuTarget = it },
                autoFocusFirst = hadDetail,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    } else if (adaptive.isTv) {
        // TV 独立歌曲列表页：双列、每页 10 首、底部分页、返回键回 Grid（全屏无侧栏）
        val pageSize = 10
        var page by remember(state.activeListId) { mutableStateOf(0) }
        val start = page * pageSize
        LumioPagedSongPage(
            title = activeList?.name ?: "收藏",
            subTitle = "${state.songs.size} 首",
            songs = state.songs,
            page = page,
            hasNextPage = start + pageSize < state.songs.size,
            onPageChange = { page = it },
            onPlay = onPlay,
            actions = actions,
            playingKey = playingKey,
            onBack = { onShowGridChange(true) },
            topBarActions = {
                if (state.songs.isNotEmpty()) {
                    if (onRefreshMv != null) {
                        LxIconButton(
                            onClick = onRefreshMv,
                            imageVector = Icons.Default.Sync,
                            contentDescription = "重新导入MV信息",
                        )
                    }
                    LxIconButton(
                        onClick = onPlayAll,
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "播放全部",
                    )
                    LxIconButton(
                        onClick = onClearList,
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空",
                    )
                }
            },
            rowExtraItems = { idx ->
                listOf(
                    com.music.fm.ui.component.SongExtraMenuItem(
                        Icons.Default.ArrowUpward, "上移", { onMoveSong(idx, -1) },
                    ),
                    com.music.fm.ui.component.SongExtraMenuItem(
                        Icons.Default.ArrowDownward, "下移", { onMoveSong(idx, +1) },
                    ),
                    com.music.fm.ui.component.SongExtraMenuItem(
                        Icons.Default.Close, "移除出歌单", { onRemoveSong(idx) },
                    ),
                )
            },
            modifier = modifier,
        )
    } else {
        // 手机 / 平板歌曲列表页：普通滚动列表不分页（手机单列 / 平板双列），
        // 行内不显示音源标签；底部导航栏 / 左侧导航栏与悬浮迷你播放条保持显示
        androidx.activity.compose.BackHandler { onShowGridChange(true) }
        val extraItems: (Int) -> List<com.music.fm.ui.component.SongExtraMenuItem> = { idx ->
            listOf(
                com.music.fm.ui.component.SongExtraMenuItem(
                    Icons.Default.ArrowUpward, "上移", { onMoveSong(idx, -1) },
                ),
                com.music.fm.ui.component.SongExtraMenuItem(
                    Icons.Default.ArrowDownward, "下移", { onMoveSong(idx, +1) },
                ),
                com.music.fm.ui.component.SongExtraMenuItem(
                    Icons.Default.Close, "移除出歌单", { onRemoveSong(idx) },
                ),
            )
        }
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // 顶栏：返回 + 歌单名 + 数量 + 操作（与 TV 版一致）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LxIconButton(
                    onClick = { onShowGridChange(true) },
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
                Text(
                    text = activeList?.name ?: "收藏",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.songs.isNotEmpty()) {
                    Text(
                        text = "${state.songs.size} 首",
                        fontSize = 12.sp,
                        color = LumioFontLabel,
                    )
                    if (onRefreshMv != null) {
                        LxIconButton(
                            onClick = onRefreshMv,
                            imageVector = Icons.Default.Sync,
                            contentDescription = "重新导入MV信息",
                        )
                    }
                    LxIconButton(
                        onClick = onPlayAll,
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "播放全部",
                    )
                    LxIconButton(
                        onClick = onClearList,
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空",
                    )
                }
            }
            HorizontalDivider(color = LumioDivider)
            if (adaptive.twoPane) {
                // 平板：双列
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = overlayPadding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                        LumioSongRow(
                            info = info,
                            actions = actions,
                            index = index + 1,
                            isActive = playingKey == info.uniqueKey,
                            showSourceBadge = false,
                            onClick = { onPlay(index) },
                            extraItems = extraItems(index),
                        )
                    }
                }
            } else {
                // 手机：单列
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // 手机：行拉满宽度，不留左右空白
                contentPadding = overlayPadding(horizontal = 0.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, info ->
                        LumioSongRow(
                            info = info,
                            actions = actions,
                            index = index + 1,
                            isActive = playingKey == info.uniqueKey,
                            showSourceBadge = false,
                            onClick = { onPlay(index) },
                            extraItems = extraItems(index),
                        )
                    }
                }
            }
        }
    }

    // 卡片「⋯」操作弹窗（对齐 Lumio ActionDialog：居中卡片 + 全宽按钮 + 右下取消）
    menuTarget?.let { target ->
        ListActionDialog(
            list = target,
            onDismiss = { menuTarget = null },
            onCreate = {
                menuTarget = null
                onCreate()
            },
            onRename = {
                menuTarget = null
                onRename(target)
            },
            onRemove = {
                menuTarget = null
                onRemove(target)
            },
        )
    }
}

/** 歌单方块 Grid（对齐 Lumio TVMyListGridPanel：宽高比 1.2、圆角 8） */
@Composable
private fun MyListGrid(
    lists: List<MyListMeta>,
    columns: Int,
    onSelect: (MyListMeta) -> Unit,
    onMenu: (MyListMeta) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusFirst: Boolean = false,
) {
    // 从歌曲页返回时把焦点落到第一张卡片（遥控器方向键立即可用）
    val firstCardFocus = remember { FocusRequester() }
    if (autoFocusFirst) {
        LaunchedEffect(lists.isNotEmpty()) {
            if (lists.isNotEmpty()) firstCardFocus.requestFocusWhenReady()
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = overlayPadding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(lists, key = { _, it -> it.id }) { index, list ->
            MyListCard(
                list = list,
                incomingFocus = if (index == 0) firstCardFocus else null,
                onClick = { onSelect(list) },
                onMenu = { onMenu(list) },
            )
        }
    }
}

/**
 * 歌单方块卡片（对齐 Lumio CardItem）：浅底 + 主色粗体居中。
 * 操作弹窗改为长按唤起（原右上角「⋯」已移除）：
 * 手机 / 平板长按卡片，TV 遥控器焦点落在卡片上长按确认键。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MyListCard(
    list: MyListMeta,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    incomingFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val cardFocus = incomingFocus ?: remember { FocusRequester() }
    // TV 确认键：短按 = 进入歌单，长按（≥500ms）= 弹操作菜单。
    // 用 onPreviewKeyEvent 接管全部确认键事件，不再交给 combinedClickable 的键盘处理
    // （其按键重复会与自定义计时互相干扰）：
    //  - 首次 KeyDown 记录时刻；按住期间的重复 KeyDown 到时即触发菜单（只触发一次）；
    //  - KeyUp：未触发过长按则视为短按进入；若遥控器不发重复事件，按住 ≥500ms 的 KeyUp 也按长按处理；
    //  - 没有对应 KeyDown 的 KeyUp（如关闭弹窗时确认键抬起）直接吞掉，避免误进入歌单。
    var centerDownAt by remember { mutableStateOf(0L) }
    var longFired by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(shape)
            .background(LumioBoardCard)
            .onPreviewKeyEvent { event ->
                val isCenter = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (!isCenter) return@onPreviewKeyEvent false
                val now = System.currentTimeMillis()
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.repeatCount == 0 || centerDownAt == 0L) {
                            centerDownAt = now
                            longFired = false
                        } else if (!longFired && now - centerDownAt >= 500) {
                            longFired = true
                            onMenu()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val downAt = centerDownAt
                        val fired = longFired
                        centerDownAt = 0L
                        longFired = false
                        when {
                            downAt == 0L || fired -> Unit
                            now - downAt >= 500 -> onMenu()
                            else -> onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(cardFocus)
            .lxFocusable(shape = shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMenu,
            )
            .aspectRatio(1.2f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = list.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = LumioPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** 居中操作弹窗（对齐 Lumio ActionDialog）：全宽操作按钮 + 分割线 + 右下「取消」 */
@Composable
private fun ListActionDialog(
    list: MyListMeta,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            val firstFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { firstFocus.requestFocus() }
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActionItem(
                        label = "新建歌单",
                        focusRequester = firstFocus,
                        onClick = onCreate,
                    )
                    ActionItem(
                        label = "重命名",
                        disabled = list.builtin,
                        onClick = onRename,
                    )
                    ActionItem(
                        label = "删除歌单",
                        danger = true,
                        disabled = list.builtin,
                        onClick = onRemove,
                    )
                }
                HorizontalDivider(
                    color = LumioDivider,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .lxFocusable(shape = RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 14.sp,
                            color = LumioPrimary,
                        )
                    }
                }
            }
        }
    }
}

/** 操作弹窗内全宽按钮（高 48、圆角 6、浅底；danger 用主色文字，disabled 置灰） */
@Composable
private fun ActionItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    disabled: Boolean = false,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    val textColor = when {
        disabled -> LumioFontLabel
        danger -> LumioPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .lxFocusable(shape = shape)
            .clickable(enabled = !disabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 15.sp, color = textColor)
    }
}

// ── 输入 / 确认弹窗 ──────────────────────────────────────────────────────────

/** 弹窗通用修饰：TV 上加宽，并保证弹窗自身可被遥控器聚焦（焦点不跑到下层页面） */
@Composable
private fun dialogModifier(
    focusRequester: FocusRequester? = null,
): Modifier {
    val adaptive = LocalAdaptive.current
    return Modifier
        .lxFocusable(focusRequester = focusRequester, showBorder = false)
        .then(if (adaptive.isTv) Modifier.widthIn(min = 420.dp, max = 560.dp) else Modifier)
}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = dialogModifier(),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("歌单名称") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.lxFocusable(shape = RoundedCornerShape(20.dp)),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.lxFocusable(shape = RoundedCornerShape(20.dp)),
            ) { Text("取消") }
        },
    )
}

@Composable
private fun RemoveListDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = dialogModifier(focusRequester = focusRequester),
        title = { Text("删除歌单") },
        text = { Text("确定删除「$name」吗？歌单内歌曲将一并清除。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.lxFocusable(shape = RoundedCornerShape(20.dp)),
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.lxFocusable(shape = RoundedCornerShape(20.dp)),
            ) { Text("取消") }
        },
    )
}
