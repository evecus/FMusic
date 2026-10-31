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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.player.PlayerUiState
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable
import kotlinx.coroutines.launch

/**
 * 播放页（三端自适应）：
 *  - 手机  ：中部双页横滑（第 1 页封面，第 2 页歌词逐行高亮自动滚动），支持方向键左右切页
 *  - 平板 / TV：左右分栏同时显示封面与歌词，无需滑动
 * 底部为进度条 + 三键控制 + 功能行（歌词切换 / 收藏 / 播放模式 / 播放队列）。
 * 遥控器场景：所有控件可聚焦，进入页面自动把焦点落到播放/暂停键。
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
    onBack: () -> Unit,
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

    val adaptive = LocalAdaptive.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var showQueueSheet by remember { mutableStateOf(false) }

    var seeking by remember(info.uniqueKey) { mutableFloatStateOf(0f) }
    val positionSec = (state.positionMs / 1000).toInt()
    val durationSec = (state.durationMs / 1000).toInt()

    // TV：进入播放页自动落焦点到播放/暂停键，避免遥控器没有起点
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (adaptive.isTv) runCatching { playFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── 顶栏：歌名 + 歌手（对齐 mobile 顶栏布局） ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LxIconButton(
                onClick = onBack,
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        // ── 中部：手机双页横滑；平板 / TV 左右分栏 ──
        if (adaptive.twoPane) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverSurface(
                        info = info,
                        coverUrl = extra.coverUrl,
                        fraction = if (adaptive.isTv) 0.55f else 0.65f,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    LyricsPage(state = state, extra = extra)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // 方向键左右切页（外接键盘 / 遥控器）
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                if (pagerState.currentPage < 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    if (page == 0) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CoverSurface(info = info, coverUrl = extra.coverUrl, fraction = 0.78f)
                        }
                    } else {
                        LyricsPage(state = state, extra = extra)
                    }
                }
            }
        }

        // ── 进度条（TV 端用按钮快进快退，避免依赖拖拽） ──
        if (adaptive.isTv) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = adaptive.pagePadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LxIconButton(
                    onClick = { onSeek((state.positionMs - 10_000).coerceAtLeast(0)) },
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "快退 10 秒",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
                LinearProgressIndicator(
                    progress = {
                        if (state.durationMs > 0) {
                            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
                LxIconButton(
                    onClick = {
                        if (state.durationMs > 0) {
                            onSeek((state.positionMs + 10_000).coerceAtMost(state.durationMs))
                        }
                    },
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "快进 10 秒",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                )
            }
        } else {
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
        }
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
            LxIconButton(
                onClick = onPrevious,
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
                compact = adaptive.twoPane,
            )
            IconButton(
                onClick = onToggle,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .size(if (adaptive.isTv) 68.dp else if (adaptive.isTablet) 60.dp else 72.dp)
                    .lxFocusable(focusRequester = playFocus, shape = CircleShape),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(if (adaptive.isTv) 44.dp else if (adaptive.isTablet) 40.dp else 56.dp),
                )
            }
            LxIconButton(
                onClick = onNext,
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
                compact = adaptive.twoPane,
            )
        }

        // ── 底部功能行：LRC / 收藏 / 播放模式 / 队列 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面 / 歌词切换：TV 为分栏，隐藏该按钮
            if (!adaptive.twoPane) {
                LxIconButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(if (pagerState.currentPage == 1) 0 else 1)
                        }
                    },
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "歌词",
                    tint = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LxIconButton(
                onClick = { onAddToList(info) },
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "收藏到歌单",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LxIconButton(
                onClick = onCycleMode,
                imageVector = Icons.Default.Shuffle,
                contentDescription = "播放模式",
                tint = if (state.playMode == cn.toside.music.compose.player.PlayMode.SHUFFLE)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LxIconButton(
                onClick = { showQueueSheet = true },
                imageVector = Icons.Default.QueueMusic,
                contentDescription = "播放队列",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                            .lxFocusable()
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

/** 封面卡片：封面图，无图时显示占位图标 */
@Composable
private fun CoverSurface(info: MusicInfo, coverUrl: String?, fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
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

/** 歌词：逐行显示，当前行高亮并自动滚动到偏上位置 */
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
