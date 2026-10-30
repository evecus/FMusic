package cn.toside.music.compose.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import cn.toside.music.compose.data.model.MusicInfo

/**
 * 歌曲行操作集合（下载 / MV / 收藏），由 MainScreen 统一提供实现。
 */
class SongRowActions(
    val onDownload: (MusicInfo) -> Unit = {},
    val onPlayMv: (MusicInfo) -> Unit = {},
    val onDownloadMv: (MusicInfo) -> Unit = {},
    val onAddToList: (MusicInfo) -> Unit = {},
) {
    /** 该歌曲是否有可用的 MV（mobile 同款判断：仅 wy/kg 且带 mv id；wy 的 "0" 表示没有 MV） */
    fun hasMv(info: MusicInfo): Boolean =
        (info.source == "wy" || info.source == "kg") &&
            !info.mv.isNullOrEmpty() &&
            !(info.source == "wy" && info.mv == "0")
}

/**
 * 歌曲行「⋮ 更多操作」菜单：下载 / 播放MV / 下载MV / 收藏到歌单。
 * 对齐 mobile 版在线歌曲列表菜单。
 */
@Composable
fun SongMoreButton(
    info: MusicInfo,
    actions: SongRowActions,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        LxIconButton(
            onClick = { expanded = true },
            imageVector = Icons.Default.MoreVert,
            contentDescription = "更多操作",
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SongMenuItem(Icons.Default.Download, "下载歌曲") {
                expanded = false
                actions.onDownload(info)
            }
            if (actions.hasMv(info)) {
                SongMenuItem(Icons.Default.Movie, "播放MV") {
                    expanded = false
                    actions.onPlayMv(info)
                }
                SongMenuItem(Icons.Default.VideoFile, "下载MV") {
                    expanded = false
                    actions.onDownloadMv(info)
                }
            }
            SongMenuItem(Icons.Default.PlaylistAdd, "收藏到歌单") {
                expanded = false
                actions.onAddToList(info)
            }
        }
    }
}

@Composable
private fun SongMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
