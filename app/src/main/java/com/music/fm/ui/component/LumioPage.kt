package com.music.fm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.fm.data.model.MusicInfo
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.theme.LumioDivider
import com.music.fm.ui.theme.LumioFont
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioPrimary

/**
 * Lumio TV 独立歌曲列表页（歌单详情 / 排行榜 / 收藏 共用）：
 *
 *  - 顶栏：居中标题「【名称】｜副标题」+ 右侧操作图标；
 *    TV 不显示界面返回按钮（遥控器自带返回键），进入页面时焦点落到第一首歌；
 *  - 歌曲双列显示，每页 10 首（5 行 × 2 列），行样式为统一 [LumioSongRow]；
 *  - 底部分页条：「‹ 页码 ›」，遥控器左右键聚焦翻页按钮；
 *  - 遥控器返回键回到原歌单 / 排行榜 / 收藏页面。
 */
@Composable
fun LumioPagedSongPage(
    title: String,
    songs: List<MusicInfo>,
    page: Int,
    hasNextPage: Boolean,
    onPageChange: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    actions: SongRowActions,
    playingKey: String?,
    modifier: Modifier = Modifier,
    subTitle: String? = null,
    pageSize: Int = 10,
    onBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    rankColor: ((Int) -> Color?)? = null,
    rowExtraItems: ((Int) -> List<SongExtraMenuItem>)? = null,
) {
    // 遥控器返回键：回到原页面（TV 无界面返回按钮）
    if (onBack != null) {
        androidx.activity.compose.BackHandler { onBack() }
    }
    val adaptive = LocalAdaptive.current
    // TV 遥控器自带物理返回键，顶栏不再显示返回按钮；其它端保留界面返回键
    val showBackButton = !adaptive.isTv

    val start = page * pageSize
    val pageSongs = songs.drop(start).take(pageSize)

    // 进入页面给遥控器一个焦点起点：有界面返回键时落在返回键；
    // TV 隐藏返回键后落在第一首歌（歌曲异步加载，等首行出现再逐帧请求焦点）。
    val backFocus = remember { FocusRequester() }
    val firstSongFocus = remember { FocusRequester() }
    LaunchedEffect(showBackButton, page, pageSongs.isNotEmpty()) {
        if (showBackButton) {
            runCatching { backFocus.requestFocus() }
        } else if (pageSongs.isNotEmpty()) {
            firstSongFocus.requestFocusWhenReady()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // ══ 顶栏 ══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
        ) {
            if (onBack != null && showBackButton) {
                LxIconButton(
                    onClick = onBack,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .focusRequester(backFocus),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "【$title】",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LumioFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subTitle.isNullOrEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = subTitle,
                        fontSize = 14.sp,
                        color = LumioFontLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                content = topBarActions,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LumioDivider),
        )

        // ══ 歌曲：双列，每页 10 首 ══
        if (pageSongs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无歌曲", color = LumioFontLabel)
            }
        } else {
            // 行高按可用高度均分（每页 pageSize/2 行），避免歌曲只占上半屏、底部大片空白；
            // 上限 112dp，防止行过高显得稀疏。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val rows = ((pageSize + 1) / 2).coerceAtLeast(1)
                val fitRowHeight = ((maxHeight - 16.dp) / rows).coerceIn(76.dp, 112.dp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    itemsIndexed(pageSongs, key = { _, it -> it.uniqueKey }) { i, info ->
                        val globalIndex = start + i
                        LumioSongRow(
                            info = info,
                            actions = actions,
                            index = globalIndex + 1,
                            isActive = playingKey == info.uniqueKey,
                            rankColor = rankColor?.invoke(globalIndex),
                            onClick = { onPlay(globalIndex) },
                            rowHeight = fitRowHeight,
                            extraItems = rowExtraItems?.invoke(globalIndex) ?: emptyList(),
                            focusRequester = if (i == 0) firstSongFocus else null,
                        )
                    }
                }
            }
        }

        // ══ 底部分页条：‹ 页码 › ══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageButton(
                enabled = page > 0,
                onClick = { onPageChange(page - 1) },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一页",
                    tint = if (page > 0) LumioPrimary else LumioFontLabel,
                )
            }
            Spacer(Modifier.size(20.dp))
            Text(
                text = "${page + 1}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LumioFont,
            )
            Spacer(Modifier.size(20.dp))
            PageButton(
                enabled = hasNextPage,
                onClick = { onPageChange(page + 1) },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一页",
                    tint = if (hasNextPage) LumioPrimary else LumioFontLabel,
                )
            }
        }
    }
}

/** 分页翻页按钮：40dp 圆角方块，聚焦时显示焦点框（主色描边 + 浅主色底） */
@Composable
private fun PageButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .lxFocusable(shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
