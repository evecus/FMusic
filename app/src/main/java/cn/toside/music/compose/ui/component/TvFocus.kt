package cn.toside.music.compose.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.ui.adaptive.LocalAdaptive

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
 * 手机 / 平板保持原生尺寸与手感。
 */
@Composable
fun LxIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
) {
    val adaptive = LocalAdaptive.current
    val iconSize = if (adaptive.isTv) adaptive.iconButtonSize * 0.6f else 24.dp
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (adaptive.isTv) Modifier.size(adaptive.iconButtonSize) else Modifier)
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
