package cn.toside.music.compose.ui.mylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.MyListMeta
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources
import cn.toside.music.compose.ui.adaptive.AdaptiveInfo
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable
import kotlinx.coroutines.launch

/**
 * “收藏”页（对齐 mobile 版 Mylist）：
 * 歌单列表（试听列表 / 我的收藏 / 自建歌单，支持新建 / 重命名 / 删除）+ 当前歌单歌曲列表。
 *
 * 三端形态：
 *  - 手机  ：歌单列表放 [ModalNavigationDrawer] 抽屉，顶栏按钮打开；
 *  - 平板 / TV：歌单列表常驻为左侧栏，右侧为歌曲列表，无抽屉。
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MyListMeta?>(null) }
    var removeTarget by remember { mutableStateOf<MyListMeta?>(null) }

    val activeList = state.lists.firstOrNull { it.id == state.activeListId }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            onToastConsumed()
        }
    }

    if (adaptive.isPhone) {
        // ── 手机：抽屉式歌单列表 ───────────────────────────────────
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    PlaylistPane(
                        lists = state.lists,
                        activeListId = state.activeListId,
                        rowHeight = adaptive.rowHeight,
                        onSelect = {
                            onSelectList(it)
                            scope.launch { drawerState.close() }
                        },
                        onCreate = { showCreateDialog = true },
                        onRename = { renameTarget = it },
                        onRemove = { removeTarget = it },
                    )
                }
            },
        ) {
            SongListPane(
                activeName = activeList?.name ?: "收藏",
                songs = state.songs,
                adaptive = adaptive,
                showMenuButton = true,
                onOpenMenu = { scope.launch { drawerState.open() } },
                onPlay = onPlay,
                onPlayAll = onPlayAll,
                onRemoveSong = onRemoveSong,
                onMoveSong = onMoveSong,
                onClearList = onClearList,
                snackbarHostState = snackbarHostState,
            )
        }
    } else {
        // ── 平板 / TV：常驻侧栏 + 右侧内容 ────────────────────────
        Row(modifier = modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (adaptive.isTv) 340.dp else 280.dp),
                tonalElevation = 1.dp,
            ) {
                PlaylistPane(
                    lists = state.lists,
                    activeListId = state.activeListId,
                    rowHeight = adaptive.rowHeight,
                    onSelect = onSelectList,
                    onCreate = { showCreateDialog = true },
                    onRename = { renameTarget = it },
                    onRemove = { removeTarget = it },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SongListPane(
                    activeName = activeList?.name ?: "收藏",
                    songs = state.songs,
                    adaptive = adaptive,
                    showMenuButton = false,
                    onOpenMenu = {},
                    onPlay = onPlay,
                    onPlayAll = onPlayAll,
                    onRemoveSong = onRemoveSong,
                    onMoveSong = onMoveSong,
                    onClearList = onClearList,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    // ── 弹窗 ──────────────────────────────────────────────────────
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

/** 歌单列表（手机抽屉内容与平板 / TV 常驻侧栏共用） */
@Composable
private fun PlaylistPane(
    lists: List<MyListMeta>,
    activeListId: String,
    rowHeight: Dp,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (MyListMeta) -> Unit,
    onRemove: (MyListMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            "我的歌单",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(lists, key = { it.id }) { list ->
                PlaylistRow(
                    list = list,
                    active = list.id == activeListId,
                    rowHeight = rowHeight,
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
                .padding(16.dp)
                .lxFocusable(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text("新建歌单")
        }
    }
}

/** 单条歌单：点击切换、⋮ 菜单可重命名 / 删除（TV 上整行与菜单按钮均可聚焦） */
@Composable
private fun PlaylistRow(
    list: MyListMeta,
    active: Boolean,
    rowHeight: Dp,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .clickable(onClick = onSelect)
            .lxFocusable()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (list.builtin) Icons.Default.Favorite else Icons.Default.QueueMusic,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            list.name,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
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
    HorizontalDivider()
}

/** 右侧歌曲列表区域（手机 / 平板 / TV 共用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongListPane(
    activeName: String,
    songs: List<MusicInfo>,
    adaptive: AdaptiveInfo,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onClearList: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeName, maxLines = 1)
                        Text(
                            "${songs.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (showMenuButton) {
                        LxIconButton(
                            onClick = onOpenMenu,
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "歌单列表",
                        )
                    }
                },
                actions = {
                    if (songs.isNotEmpty()) {
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (songs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "歌单暂无歌曲，去搜索页收藏吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = adaptive.pagePadding),
            ) {
                itemsIndexed(songs, key = { _, it -> it.uniqueKey }) { index, song ->
                    SongRow(
                        index = index,
                        info = song,
                        rowHeight = adaptive.rowHeight,
                        onClick = { onPlay(index) },
                        onRemove = { onRemoveSong(index) },
                        onMoveUp = { onMoveSong(index, -1) },
                        onMoveDown = { onMoveSong(index, +1) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SongRow(
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
        headlineContent = { Text(info.name, maxLines = 1) },
        supportingContent = {
            Text(
                buildString {
                    append(info.singer)
                    append(" · ")
                    append(Sources.label(info.source))
                    if (!info.albumName.isNullOrEmpty()) append(" - ").append(info.albumName)
                },
                maxLines = 1,
            )
        },
        leadingContent = {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
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
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .clickable(onClick = onClick)
            .lxFocusable(),
    )
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
