package cn.toside.music.compose.ui.download

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.download.DownloadStatus
import cn.toside.music.compose.download.DownloadTask
import cn.toside.music.compose.download.DownloadTaskType
import cn.toside.music.compose.download.sizeFormate
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable

private enum class DownloadTab(val label: String) {
    SONG("歌曲"),
    MV("MV"),
}

/**
 * 下载管理页（对齐 mobile DownloadManager）：歌曲 / MV 两个 Tab，
 * 行内显示状态与进度，操作：暂停 / 继续 / 重试 / 删除。
 */
@Composable
fun DownloadScreen(
    tasks: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    var tab by remember { mutableStateOf(DownloadTab.SONG) }
    val filtered = tasks.filter {
        if (tab == DownloadTab.SONG) it.type == DownloadTaskType.SONG
        else it.type == DownloadTaskType.MV
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = adaptive.pagePadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadTab.entries.forEach { t ->
                AssistChip(
                    onClick = { tab = t },
                    label = { Text(t.label) },
                    modifier = Modifier.lxFocusable(shape = RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.weight(1f))
            if (tasks.any { it.status == DownloadStatus.COMPLETED }) {
                TextButton(onClick = onClearCompleted) { Text("清除已完成") }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (tab == DownloadTab.SONG) "暂无歌曲下载任务" else "暂无 MV 下载任务",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { task ->
                    DownloadTaskRow(
                        task = task,
                        onPause = onPause,
                        onResume = onResume,
                        onRetry = onRetry,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(    task: DownloadTask,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val adaptive = LocalAdaptive.current
    androidx.compose.material3.ListItem(
        leadingContent = {
            Icon(
                if (task.type == DownloadTaskType.MV) Icons.Default.Movie else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(task.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    when (task.status) {
                        DownloadStatus.WAITING -> "等待中…"
                        DownloadStatus.DOWNLOADING -> buildString {
                            append((task.percent * 100).toInt()).append("%")
                            if (task.speed.isNotEmpty()) append(" · ").append(task.speed)
                            if (task.total > 0) {
                                append(" · ").append(sizeFormate(task.downloaded))
                                append("/").append(sizeFormate(task.total))
                            }
                        }
                        DownloadStatus.PAUSED -> "已暂停"
                        DownloadStatus.COMPLETED ->
                            if (task.type == DownloadTaskType.SONG && task.quality.isNotEmpty())
                                "已完成 · ${task.quality}"
                            else "已完成"
                        DownloadStatus.ERROR -> "失败：${task.errorMsg}"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (task.status == DownloadStatus.ERROR) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task.status == DownloadStatus.DOWNLOADING && task.total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { task.percent },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> LxIconButton(
                        onClick = { onPause(task.id) },
                        imageVector = Icons.Default.Pause,
                        contentDescription = "暂停",
                    )
                    DownloadStatus.PAUSED -> LxIconButton(
                        onClick = { onResume(task.id) },
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "继续",
                    )
                    DownloadStatus.ERROR -> LxIconButton(
                        onClick = { onRetry(task.id) },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重试",
                    )
                    else -> {}
                }
                LxIconButton(
                    onClick = { onRemove(task.id) },
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = adaptive.rowHeight)
            .clickable(onClick = {})
            .lxFocusable(),
    )
}

/** 下载音质选择弹窗（对齐 mobile MusicDownloadModal） */
@Composable
fun DownloadQualityDialog(
    info: cn.toside.music.compose.data.model.MusicInfo,
    defaultQuality: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var quality by remember { mutableStateOf(defaultQuality) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载歌曲") },
        text = {
            Column {
                Text(
                    "${info.name} - ${info.singer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                cn.toside.music.compose.data.model.Qualitys.all.forEach { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { quality = q }
                            .lxFocusable(shape = RoundedCornerShape(10.dp))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = quality == q,
                            onClick = { quality = q },
                        )
                        Text(cn.toside.music.compose.data.model.Qualitys.label(q))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(quality) }) { Text("下载") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
