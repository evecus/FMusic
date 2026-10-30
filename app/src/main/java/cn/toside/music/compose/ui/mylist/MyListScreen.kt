package cn.toside.music.compose.ui.mylist

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.MyListMeta
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources
import cn.toside.music.compose.ui.adaptive.AdaptiveInfo
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable

/**
 * “收藏”页（布局对齐排行榜页）：
 *  - 手机  ：单栏 —— 顶部当前歌单标题（点击弹出歌单选择弹层）+ 歌曲紧凑列表
 *  - 平板 / TV：左右分栏 —— 左侧常驻歌单列表（含新建入口），右侧歌曲紧凑列表
 *
 * 歌曲行样式与排行榜一致：序号 + 歌名/歌手 + 来源标签 + 操作按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onToastConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MyListMeta?>(null) }
    var removeTarget by remember { mutableStateOf<MyListMeta?>(null) }
    // 手机端：歌单选择底部弹层
    var showListSheet by remember { mutableStateOf(false) }

    val activeList = state.lists.firstOrNull { it.id == state.activeListId }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            onToastConsumed()
        }
    }

    if (adaptive.twoPane) {
        // ── 平板 / TV：左侧歌单栏 + 右侧歌曲列表，铺满全部空间 ──
        Row(modifier = modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxHeight(),
                tonalElevation = 1.dp,
            ) {
                PlaylistPane(
                    lists = state.lists,
                    activeListId = state.activeListId,
                    isTv = adaptive.isTv,
                    onSelect = onSelectList,
                    onCreate = { showCreateDialog = true },
                    onRename = { renameTarget = it },
                    onRemove = { removeTarget = it },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                MyListHeader(
                    name = activeList?.name ?: "收藏",
                    count = state.songs.size,
                    onOpenLists = null,
                    onPlayAll = onPlayAll,
                    onClearList = onClearList,
                    hasSongs = state.songs.isNotEmpty(),
                    adaptive = adaptive,
                )
                MyListSongList(
                    songs = state.songs,
                    adaptive = adaptive,
                    onPlay = onPlay,
                    onRemoveSong = onRemoveSong,
                    onMoveSong = onMoveSong,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    } else {
        // ── 手机：单栏，歌单经底部弹层选择 ──
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                MyListHeader(
                    name = activeList?.name ?: "收藏",
                    count = state.songs.size,
                    onOpenLists = { showListSheet = true },
                    onPlayAll = onPlayAll,
                    onClearList = onClearList,
                    hasSongs = state.songs.isNotEmpty(),
                    adaptive = adaptive,
                )
                MyListSongList(
                    songs = state.songs,
                    adaptive = adaptive,
                    onPlay = onPlay,
                    onRemoveSong = onRemoveSong,
                    onMoveSong = onMoveSong,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // ── 手机：歌单选择弹层（与排行榜的榜单弹层同款交互） ──
    if (showListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showListSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = "我的歌单",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.lists, key = { it.id }) { list ->
                    PlaylistRow(
                        list = list,
                        active = list.id == state.activeListId,
                        compact = false,
                        onSelect = {
                            showListSheet = false
                            onSelectList(list.id)
                        },
                        onRename = { renameTarget = list },
                        onRemove = { removeTarget = list },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = {
                            showListSheet = false
                            showCreateDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .lxFocusable(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("新建歌单")
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
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

/** 标题行：当前歌单名 + 数量 + 播放全部 / 清空。手机端点击可弹出歌单选择。 */
@Composable
private fun MyListHeader(
    name: String,
    count: Int,
    onOpenLists: (() -> Unit)?,
    onPlayAll: () -> Unit,
    onClearList: () -> Unit,
    hasSongs: Boolean,
    adaptive: AdaptiveInfo,
    modifier: Modifier = Modifier,
) {
    val clickableModifier = if (onOpenLists != null) {
        Modifier
            .clickable(onClick = onOpenLists)
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
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onOpenLists != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "切换歌单")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hasSongs) {
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
}

/** 歌曲紧凑列表（样式对齐排行榜歌曲行） */
@Composable
private fun MyListSongList(
    songs: List<MusicInfo>,
    adaptive: AdaptiveInfo,
    onPlay: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (songs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "歌单暂无歌曲，去搜索页收藏吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = adaptive.pagePadding, vertical = 4.dp),
            ) {
                itemsIndexed(songs, key = { _, it -> it.uniqueKey }) { index, song ->
                    MyListSongRow(
                        index = index,
                        info = song,
                        rowHeight = adaptive.rowHeight,
                        onClick = { onPlay(index) },
                        onRemove = { onRemoveSong(index) },
                        onMoveUp = { onMoveSong(index, -1) },
                        onMoveDown = { onMoveSong(index, +1) },
                    )
                }
            }
        }
    }
}

/** 单首歌曲行：序号 + 歌名/歌手/专辑 + 来源标签 + 菜单（上移 / 下移 / 移除） */
@Composable
private fun MyListSongRow(
    index: Int,
    info: MusicInfo,
    rowHeight: Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleSmall,
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
                Box {
                    LxIconButton(
                        onClick = { menuOpen = true },
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "歌曲菜单",
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("上移") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMoveUp()
                            },
                            modifier = Modifier.lxFocusable(),
                        )
                        DropdownMenuItem(
                            text = { Text("下移") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onMoveDown()
                            },
                            modifier = Modifier.lxFocusable(),
                        )
                        DropdownMenuItem(
                            text = { Text("移除出歌单") },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            },
                            modifier = Modifier.lxFocusable(),
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .clickable(onClick = onClick)
            .lxFocusable(),
    )
}

/**
 * 平板 / TV 左侧常驻歌单栏（紧凑样式对齐排行榜榜单列表）。
 */
@Composable
private fun PlaylistPane(
    lists: List<MyListMeta>,
    activeListId: String,
    isTv: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (MyListMeta) -> Unit,
    onRemove: (MyListMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.width(if (isTv) 320.dp else 260.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                text = "我的歌单",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    PlaylistRow(
                        list = list,
                        active = list.id == activeListId,
                        compact = true,
                        onSelect = { onSelect(list.id) },
                        onRename = { onRename(list) },
                        onRemove = { onRemove(list) },
                    )
                }
            }
            HorizontalDivider()
            OutlinedButton(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .lxFocusable(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新建歌单")
            }
        }
    }
}

/**
 * 单条歌单（左侧栏 / 手机弹层共用；样式对齐排行榜榜单行）。
 * 非内置歌单行尾有 ⋮ 菜单可重命名 / 删除。
 */
@Composable
private fun PlaylistRow(
    list: MyListMeta,
    active: Boolean,
    compact: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .lxFocusable()
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (list.builtin) Icons.Default.Favorite else Icons.Default.QueueMusic,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = list.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!list.builtin) {
            Box {
                LxIconButton(
                    onClick = { menuOpen = true },
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "歌单菜单",
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                        modifier = Modifier.lxFocusable(),
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                        modifier = Modifier.lxFocusable(),
                    )
                }
            }
        }
    }
}

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
                modifier = Modifier.lxFocusable(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.lxFocusable()) { Text("取消") }
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
            TextButton(onClick = onConfirm, modifier = Modifier.lxFocusable()) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.lxFocusable()) { Text("取消") }
        },
    )
}
