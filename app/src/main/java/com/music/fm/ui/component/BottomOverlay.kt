package com.music.fm.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底部悬浮迷你播放条占用的高度（无迷你条 / TV 时为 0）。
 * 页面内容铺满到底部、从迷你条下方滚过，列表在末尾通过它预留滚动余量，
 * 避免内容在迷你条上方被硬切、露出一层底色。
 */
val LocalBottomOverlay = compositionLocalOf { 0.dp }

/** 列表 contentPadding：在原有四边内边距基础上，底部追加迷你条余量 */
@Composable
fun overlayPadding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): PaddingValues =
    PaddingValues(
        start = horizontal,
        end = horizontal,
        top = vertical,
        bottom = vertical + LocalBottomOverlay.current,
    )
