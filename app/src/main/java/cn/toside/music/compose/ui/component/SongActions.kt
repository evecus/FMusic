package cn.toside.music.compose.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.ui.adaptive.LocalAdaptive

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

/** 歌曲菜单扩展项（如收藏页的上移 / 下移 / 移除） */
class SongExtraMenuItem(
    val icon: ImageVector?,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * 歌曲行「⋮ 更多操作」按钮：下载 / 播放MV / 下载MV / 收藏到歌单（+ [extraItems] 扩展项）。
 * 对齐 mobile 版在线歌曲列表菜单。
 *
 * TV 焦点导航：
 *  - [focusRequester]：外部（歌曲行）按右键时把焦点引到本按钮；
 *  - [onBackFocus]：按钮聚焦时按左键把焦点还给歌曲行。
 */
@Composable
fun SongMoreButton(
    info: MusicInfo,
    actions: SongRowActions,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onBackFocus: (() -> Unit)? = null,
    extraItems: List<SongExtraMenuItem> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }
    val adaptive = LocalAdaptive.current
    androidx.compose.foundation.layout.Box(modifier) {
        LxIconButton(
            onClick = { expanded = true },
            imageVector = Icons.Default.MoreVert,
            contentDescription = "更多操作",
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (onBackFocus != null && adaptive.isTv) {
                        Modifier.onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionLeft
                            ) {
                                onBackFocus()
                                true
                            } else false
                        }
                    } else Modifier
                ),
        )
        LxDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SongMenuItem(Icons.Default.Download, "下载歌曲", first = true) {
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
            extraItems.forEach { item ->
                SongMenuItem(item.icon, item.label) {
                    expanded = false
                    item.onClick()
                }
            }
        }
    }
}

@Composable
private fun SongMenuItem(
    icon: ImageVector?,
    label: String,
    first: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        onClick = onClick,
        modifier = Modifier.lxMenuItem(first = first),
    )
}
