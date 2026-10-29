package cn.toside.music.compose.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.player.PlayerUiState
import kotlinx.coroutines.launch

/**
 * 播放页（对齐 mobile 版播放页交互）：
 * 中部为可横滑双页——第 1 页封面，第 2 页歌词逐行高亮自动滚动（左滑看歌词、右滑回封面）；
 * 底部为进度条 + 三键控制 + 功能行（歌词切换 / 收藏 / 播放模式 / 播放队列）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    state: PlayerUiState,
    extra: PlayExtraState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleMode: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    onPlayQueueAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = state.current
    if (info == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无播放", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var showQueueSheet by remember { mutableStateOf(false) }

    var seeking by remember(info.uniqueKey) { mutableFloatStateOf(0f) }
    val positionSec = (state.positionMs / 1000).toInt()
    val durationSec = (state.durationMs / 1000).toInt()

    Column(modifier = modifier.fillMaxSize()) {
        // ── 顶栏：歌名 + 歌手（对齐 mobile 顶栏布局） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { /* Tab 页面，无返回动作 */ }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    info.singer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // ── 中部：双页横滑（封面 ↔ 歌词） ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            if (page == 0) {
                CoverPage(info = info, coverUrl = extra.coverUrl)
            } else {
                LyricsPage(state = state, extra = extra)
            }
        }

        // ── 进度条 ──
        Slider(
            value = if (seeking > 0f) seeking else state.positionMs.toFloat(),
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                onSeek(seeking.toLong())
                seeking = 0f
            },
            valueRange = 0f..(state.durationMs.coerceAtLeast(1)).toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(positionSec), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(durationSec), style = MaterialTheme.typography.labelSmall)
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 4.dp),
                strokeWidth = 2.dp,
            )
        }
        state.error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }

        // ── 三键控制：上一首 / 播放暂停 / 下一首 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(64.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // ── 底部功能行：LRC / 收藏 / 播放模式 / 队列 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(if (pagerState.currentPage == 1) 0 else 1)
                }
            }) {
                Icon(
                    Icons.Default.Subtitles,
                    contentDescription = "歌词",
                    tint = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAddToList(info) }) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = "收藏到歌单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCycleMode) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "播放模式",
                    tint = if (state.playMode == cn.toside.music.compose.player.PlayMode.SHUFFLE)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showQueueSheet = true }) {
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = "播放队列",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ── 播放队列弹层 ──
    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Text(
                "播放队列（${state.queue.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(state.queue, key = { _, it -> it.uniqueKey }) { index, song ->
                    val active = index == state.currentIndex
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showQueueSheet = false
                                onPlayQueueAt(index)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(
                            song.name,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (active) FontWeight.Bold else null,
                            maxLines = 1,
                        )
                        Text(
                            song.singer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 第 1 页：封面 */
@Composable
private fun CoverPage(info: MusicInfo, coverUrl: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
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
    }
}

/** 第 2 页：歌词逐行显示，当前行高亮并自动滚动到屏幕中部 */
@Composable
private fun LyricsPage(state: PlayerUiState, extra: PlayExtraState) {
    val lines = remember(extra.lyric) {
        extra.lyric?.let { parseLyric(it.lyric) } ?: emptyList()
    }
    val listState = rememberLazyListState()

    // 当前行（时间轴定位）；播放进度每 500ms 轮询驱动重组
    val currentIndex = if (lines.isEmpty()) -1
    else lines.indexOfLast { it.timeMs <= state.positionMs }

    // 歌词变化时回到顶部
    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) listState.scrollToItem(0)
    }
    // 当前行变化自动滚动，让当前行停在偏上位置
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    when {
        extra.lyricLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        lines.isNotEmpty() -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(40.dp)) }
            itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
                val current = index == currentIndex
                Text(
                    line.text.ifEmpty { " " },
                    fontSize = if (current) 18.sp else 16.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
        extra.lyric != null -> PlainLyricText(extra.lyric.lyric)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlainLyricText(raw: String) {
    val scrollState = androidx.compose.foundation.rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Text(
            raw,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 24.dp),
        )
    }
}

/** LRC 歌词行 */
private data class LyricLine(val timeMs: Long, val text: String)

private val lrcTagRegex = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")

/** 解析 LRC 文本为时间轴行；无时间标签的行忽略 */
private fun parseLyric(raw: String): List<LyricLine> {
    val out = mutableListOf<LyricLine>()
    for (line in raw.lines()) {
        val tags = lrcTagRegex.findAll(line).toList()
        if (tags.isEmpty()) continue
        val text = line.replace(lrcTagRegex, "").trim()
        for (m in tags) {
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = when (val f = m.groupValues[3]) {
                "" -> 0L
                else -> {
                    val padded = (f + "000").take(3)
                    padded.toLong()
                }
            }
            out += LyricLine(min * 60_000 + sec * 1000 + frac, text)
        }
    }
    return out.sortedBy { it.timeMs }
}

private fun formatTime(seconds: Int): String {
    val s = if (seconds < 0) 0 else seconds
    return "%d:%02d".format(s / 60, s % 60)
}
