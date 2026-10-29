package cn.toside.music.compose.ui.mylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.MyListMeta
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources
import kotlinx.coroutines.launch

/**
 * “收藏”页（对齐 mobile 版 Mylist）：
 * 左侧抽屉为歌单列表（试听列表 / 我的收藏 / 自建歌单，支持新建 / 重命名 / 删除），
 * 右侧为当前歌单的歌曲列表（点击播放、收藏到其他歌单、移除、上下移动）。
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
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
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

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "我的歌单",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.lists, key = { it.id }) { list ->
                        DrawerListItem(
                            list = list,
                            active = list.id == state.activeListId,
                            onSelect = {
                                onSelectList(list.id)
                                scope.launch { drawerState.close() }
                            },
                            onRename = { renameTarget = list },
                            onRemove = { removeTarget = list },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("新建歌单")
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(activeList?.name ?: "收藏", maxLines = 1)
                            Text(
                                "${state.songs.size} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = "歌单列表")
                        }
                    },
                    actions = {
                        if (state.songs.isNotEmpty()) {
                            IconButton(onClick = onPlayAll) {
                                Icon(Icons.Default.PlayCircle, contentDescription = "播放全部")
                            }
                            IconButton(onClick = onClearList) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            if (state.songs.isEmpty()) {
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
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    itemsIndexed(state.songs, key = { _, it -> it.uniqueKey }) { index, song ->
                        SongRow(
                            index = index,
                            info = song,
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
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("删除歌单") },
            text = { Text("确定删除「${target.name}」吗？歌单内歌曲将一并清除。") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveList(target.id)
                    removeTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DrawerListItem(
    list: MyListMeta,
    active: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
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
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "歌单菜单")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SongRow(
    index: Int,
    info: MusicInfo,
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
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "歌曲菜单")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("上移") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("移除出歌单") },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("歌单名称") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
