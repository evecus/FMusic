package com.music.fm.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.fm.data.model.MusicInfo
import com.music.fm.ui.theme.LumioBorder
import com.music.fm.ui.theme.LumioFont
import com.music.fm.ui.theme.LumioFontLabel
import com.music.fm.ui.theme.LumioGreen
import com.music.fm.ui.theme.LumioPrimary

/**
 * Lumio TV 风格通用组件：
 *  - [LumioBadge]      音源 / 品质小徽标
 *  - [LumioTab]        顶部音源 Tab（选中项底部 3dp 主色下划线）
 *  - [LumioTagPill]    胶囊标签按钮（热门词 / 排序 / 标签共用）
 *  - [LumioMoreIcon]   行尾「⋯」白色圆圈图标
 *  - [LumioSongRow]    统一歌曲行：序号 + 主色歌名 + 灰色副信息 + 行尾更多按钮
 */

// ── 徽标 ─────────────────────────────────────────────────────────────────────

/** 小徽标：浅绿底 + 绿字，用于音源标签（对齐 Lumio Badge secondary） */
@Composable
fun LumioBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LumioGreen.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = Color(0xFF3D9B77),
            maxLines = 1,
        )
    }
}

// ── Tab ──────────────────────────────────────────────────────────────────────

/**
 * 顶部 Tab：选中时文字主色 + 底部 3dp 主色下划线（对齐 Lumio sourceTab）。
 */
@Composable
fun LumioTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .lxFocusable(shape = shape, focusRequester = focusRequester)
            .clickable(onClick = onClick)
            .drawBottomBorder(if (selected) LumioPrimary else Color.Transparent, width = 3.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) LumioPrimary else LumioFont,
            maxLines = 1,
        )
    }
}

private fun Modifier.drawBottomBorder(color: Color, width: Dp): Modifier = drawBehind {
    if (width > 0.dp) {
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - width.toPx()),
            size = Size(size.width, width.toPx()),
        )
    }
}

// ── 胶囊标签 ─────────────────────────────────────────────────────────────────

/**
 * 胶囊标签按钮（对齐 Lumio tag）：圆角 16、细边框；
 * [selected] 时主色边框 + 主色文字。
 */
@Composable
fun LumioTagPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    maxWidth: Dp = Dp.Unspecified,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = if (selected) LumioPrimary else LumioBorder
    Box(
        modifier = modifier
            .then(if (maxWidth != Dp.Unspecified) Modifier.width(maxWidth) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, border, shape)
            .lxFocusable(shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (selected) LumioPrimary else LumioFont,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 更多按钮图标 ─────────────────────────────────────────────────────────────

/** 黑色圆圈 + 三横点（对齐 Lumio MoreDotIcon）：白底细黑边圆 + 三个黑点 */
@Composable
fun LumioMoreIcon(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.5.dp, Color(0xFF1A1A1A), CircleShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A)),
            )
        }
    }
}

// ── 统一歌曲行 ───────────────────────────────────────────────────────────────

/**
 * Lumio TV 风格统一歌曲行（搜索 / 排行榜 / 歌单详情 / 收藏 共用）：
 *
 * ```
 * [  1  ] 歌名（主色）                 [⋯]
 *         歌手 · 专辑 · 时长  [来源]
 * ```
 *
 *  - 整行是一个主按钮，行尾独立「⋯」按钮（TV：右键进「⋯」，左键回行）；
 *  - [index] 为 null 时不显示序号列；
 *  - [isActive] 显示播放图标替代序号，歌名保持主色；
 *  - [rankColor] 用于排行榜前三名序号着色。
 */
@Composable
fun LumioSongRow(
    info: MusicInfo,
    actions: SongRowActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null,
    rowHeight: Dp = Dp.Unspecified,
    isActive: Boolean = false,
    rankColor: Color? = null,
    showSourceBadge: Boolean = true,
    extraItems: List<SongExtraMenuItem> = emptyList(),
    /** 外部焦点请求器：进入页面时可直接把焦点落到本行；null 时使用内部默认 */
    focusRequester: FocusRequester? = null,
) {
    val adaptive = com.music.fm.ui.adaptive.LocalAdaptive.current
    val height = if (rowHeight != Dp.Unspecified) rowHeight else adaptive.rowHeight
    // TV：行矩形与行尾按钮几何重叠，方向查找不可达，用显式导航（右→菜单按钮，左→回行）
    val internalRowFocus = remember { FocusRequester() }
    val rowFocus = focusRequester ?: internalRowFocus
    val moreFocus = remember { FocusRequester() }
    val mainShape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 主按钮：序号 / 播放图标 + 标题区
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(mainShape)
                .focusRequester(rowFocus)
                .lxRowFocusNav(moreFocus)
                .lxFocusable(shape = mainShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isActive -> Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "正在播放",
                        tint = LumioPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    else -> Text(
                        text = (index ?: 0).let { if (it > 0) it.toString() else "" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = rankColor ?: LumioFont,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
            ) {
                Text(
                    text = info.name,
                    fontSize = 15.sp,
                    color = LumioPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (showSourceBadge) LumioBadge(com.music.fm.data.model.Sources.label(info.source))
                    Text(
                        text = buildString {
                            append(info.singer)
                            if (!info.albumName.isNullOrEmpty()) append(" · ").append(info.albumName)
                            if (!info.interval.isNullOrEmpty()) append(" · ").append(info.interval)
                        },
                        fontSize = 12.sp,
                        color = LumioFontLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 行尾「⋯」更多按钮
        SongMoreButton(
            info = info,
            actions = actions,
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(horizontal = 12.dp),
            focusRequester = moreFocus,
            onBackFocus = { runCatching { rowFocus.requestFocus() } },
            extraItems = extraItems,
            icon = { LumioMoreIcon() },
        )
    }
}
