package cn.toside.music.compose.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.player.PlayMode
import cn.toside.music.compose.player.PlayerUiState

@Composable
fun PlayScreen(
    state: PlayerUiState,
    extra: PlayExtraState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = state.current
    if (info == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无播放", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var sliderValue by remember(info.uniqueKey) {
        mutableFloatStateOf(0f)
    }
    var seeking by remember(info.uniqueKey) { mutableFloatStateOf(0f) }
    val positionSec = (state.positionMs / 1000).toInt()
    val durationSec = (state.durationMs / 1000).toInt()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (extra.coverUrl != null) {
                AsyncImage(
                    model = extra.coverUrl,
                    contentDescription = info.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.4f),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            info.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append(info.singer)
                if (!info.albumName.isNullOrEmpty()) append(" · ").append(info.albumName)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // 进度条
        Slider(
            value = if (seeking > 0f) seeking else state.positionMs.toFloat(),
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                onSeek(seeking.toLong())
                seeking = 0f
            },
            valueRange = 0f..(state.durationMs.coerceAtLeast(1)).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(positionSec), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(durationSec), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp))
            Spacer(Modifier.height(8.dp))
        }

        state.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        // 控制栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCycleMode) {
                Icon(
                    imageVector = when (state.playMode) {
                        PlayMode.LIST_LOOP -> Icons.Default.Repeat
                        PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                    },
                    contentDescription = "播放模式",
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.height(64.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
            // 占位保持对称
            Box(Modifier.height(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        // 歌词
        when {
            extra.lyricLoading -> CircularProgressIndicator()
            extra.lyric != null -> Text(
                extra.lyric.lyric,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Text(
                "暂无歌词",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val s = if (seconds < 0) 0 else seconds
    return "%d:%02d".format(s / 60, s % 60)
}
