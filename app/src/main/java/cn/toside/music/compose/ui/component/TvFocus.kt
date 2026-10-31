package cn.toside.music.compose.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import kotlinx.coroutines.delay

/**
 * 统一的可聚焦修饰。
 *
 * - TV：保证可被遥控器聚焦，聚焦时放大并描边，让用户看清当前焦点位置；
 *      方向键由系统按控件空间位置自动查找下一个焦点目标。
 * - 手机 / 平板：只补充可获焦能力（蓝牙键盘 / 无障碍 / D-pad），不改变外观。
 *
 * 说明：Compose 的 `clickable/selectable/toggleable` 本身已支持“回车 / 遥控器确认键”
 * 触发点击，因此这里只负责“焦点可见”，不重复接管确认键，避免一次按键触发两次。
 */
fun Modifier.lxFocusable(
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    showBorder: Boolean = true,
): Modifier = composed {
    val isTv = LocalAdaptive.current.isTv
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.04f else 1f,
        label = "lxFocusScale",
    )
    var result = this
        .onFocusChanged { focused = it.hasFocus }
        .focusable()
    if (isTv) {
        result = result.scale(scale)
        if (showBorder && focused) {
            result = result.border(2.dp, MaterialTheme.colorScheme.primary, shape)
        }
    }
    if (focusRequester != null) result.focusRequester(focusRequester) else result
}

/**
 * TV 友好的图标按钮：TV 端放大到 [LocalAdaptive.iconButtonSize] 并显示焦点描边，
 * 手机 / 平板保持原生尺寸与手感。[compact] 用于播放页等需要更紧凑控件的场景。
 */
@Composable
fun LxIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val adaptive = LocalAdaptive.current
    val size = when {
        compact && adaptive.isTv -> 48.dp
        compact && adaptive.isTablet -> 40.dp
        compact -> 36.dp
        adaptive.isTv -> adaptive.iconButtonSize
        else -> null // 非 TV 非 compact：保持 IconButton 原生尺寸
    }
    val iconSize = when {
        compact -> 20.dp
        adaptive.isTv -> adaptive.iconButtonSize * 0.6f
        else -> 24.dp
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .lxFocusable(shape = CircleShape),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ── DropdownMenu TV 支持 ─────────────────────────────────────────

/** 菜单第一项的 FocusRequester（由 [LxDropdownMenu] 提供，弹出时自动聚焦） */
private val LocalMenuFirstFocus = compositionLocalOf<FocusRequester?> { null }

/**
 * TV 友好的下拉菜单：弹出后自动把焦点移到标记了 [lxMenuItem]（first = true）的第一项，
 * 避免遥控器用户看不到菜单内焦点。
 */
@Composable
fun LxDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        CompositionLocalProvider(LocalMenuFirstFocus provides firstFocus) {
            content()
        }
        if (expanded) {
            LaunchedEffect(expanded) {
                delay(60) // 等 Popup 完成首次布局后再请求焦点
                runCatching { firstFocus.requestFocus() }
            }
        }
    }
}

/**
 * 菜单项通用修饰：TV 下聚焦时高亮背景；[first] = true 的项在菜单弹出时自动获得焦点。
 * 注意：DropdownMenuItem 自带 clickable（可聚焦、可确认），这里只负责高亮与初始焦点，
 * 不要再叠加 [lxFocusable]，避免出现双层焦点目标。
 */
fun Modifier.lxMenuItem(first: Boolean = false): Modifier = composed {
    val isTv = LocalAdaptive.current.isTv
    var focused by remember { mutableStateOf(false) }
    var m = this.onFocusChanged { focused = it.hasFocus }
    if (first && isTv) {
        val requester = LocalMenuFirstFocus.current
        if (requester != null) m = m.focusRequester(requester)
    }
    m.background(
        if (focused && isTv) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        RoundedCornerShape(8.dp),
    )
}

/**
 * TV 列表行焦点导航：行与行尾按钮矩形几何重叠，系统方向查找找不到行尾按钮；
 * 行聚焦时按「右」键显式把焦点移到行尾的更多按钮（[moreFocus]）。
 * 非 TV 不生效。
 */
fun Modifier.lxRowFocusNav(moreFocus: FocusRequester): Modifier = composed {
    val isTv = LocalAdaptive.current.isTv
    if (!isTv) {
        this
    } else {
        this.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                runCatching { moreFocus.requestFocus() }
                true
            } else false
        }
    }
}
