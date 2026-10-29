package cn.toside.music.compose.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.player.PlayerUiState
import cn.toside.music.compose.ui.adaptive.LocalAdaptive

/** 迷你播放条：显示当前曲目与播放控制，点击进入播放页（TV 上整体可聚焦、控件放大） */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = state.current ?: return
    val adaptive = LocalAdaptive.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (adaptive.isTv) adaptive.rowHeight else 56.dp)
                    .clickable(onClick = onClick)
                    .lxFocusable()
                    .padding(
                        horizontal = if (adaptive.isTv) 24.dp else 16.dp,
                        vertical = if (adaptive.isTv) 12.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        info.singer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (adaptive.isTv) 40.dp else 24.dp),
                    )
                } else {
                    LxIconButton(
                        onClick = onToggle,
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                    )
                }
            }
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = {
                        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (adaptive.isTv) 4.dp else 2.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
