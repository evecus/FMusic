package cn.toside.music.compose.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.MyListMeta

/**
 * “收藏到歌单”选择弹窗：列出全部歌单（内置 + 自建），点击即收藏。
 */
@Composable
fun AddToListDialog(
    lists: List<MyListMeta>,
    songName: String?,
    onDismiss: () -> Unit,
    onSelect: (MyListMeta) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("收藏到歌单") },
        text = {
            Column {
                if (songName != null) {
                    Text(
                        songName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (lists.isEmpty()) {
                    Text("暂无歌单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    ) {
                        items(lists, key = { it.id }) { list ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(list) },
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    list.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp, horizontal = 4.dp),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            Text(
                "取消",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(16.dp),
            )
        },
    )
}
