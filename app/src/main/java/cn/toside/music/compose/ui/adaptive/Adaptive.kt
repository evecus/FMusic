package cn.toside.music.compose.ui.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 设备形态：手机 / 平板 / 电视 */
enum class DeviceType { PHONE, TABLET, TV }

/** 窗口宽度等级（Material 3 自适应规范） */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 自适应布局信息：在根布局计算一次，全应用经 [LocalAdaptive] 共享。
 *
 * 三端策略：
 *  - PHONE ：单栏列表 / 2 列网格，抽屉与榜单用底部弹层
 *  - TABLET：左右分栏，网格 3~4 列，列表常驻为侧栏
 *  - TV    ：左右分栏、更大留白与控件、字号放大，全部控件可被遥控器聚焦
 */
data class AdaptiveInfo(
    val deviceType: DeviceType,
    val widthClass: WidthClass,
) {
    val isPhone: Boolean get() = deviceType == DeviceType.PHONE
    val isTablet: Boolean get() = deviceType == DeviceType.TABLET
    val isTv: Boolean get() = deviceType == DeviceType.TV

    /** 平板 / TV 使用左右分栏，手机为单栏 */
    val twoPane: Boolean get() = !isPhone

    /** 封面网格列数 */
    val gridColumns: Int
        get() = when (deviceType) {
            DeviceType.PHONE -> 2
            DeviceType.TABLET -> if (widthClass == WidthClass.EXPANDED) 4 else 3
            DeviceType.TV -> 5
        }

    /** 页面左右留白 */
    val pagePadding: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> 8.dp
            DeviceType.TABLET -> 20.dp
            DeviceType.TV -> 40.dp
        }

    /** 列表单行高度 */
    val rowHeight: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> 64.dp
            DeviceType.TABLET -> 72.dp
            DeviceType.TV -> 88.dp
        }

    /** 图标按钮等控件尺寸 */
    val iconButtonSize: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> 40.dp
            DeviceType.TABLET -> 48.dp
            DeviceType.TV -> 64.dp
        }

    /** 内容最大宽度（手机不限制宽度） */
    val contentMaxWidth: Dp
        get() = when (deviceType) {
            DeviceType.PHONE -> Dp.Unspecified
            DeviceType.TABLET -> 780.dp
            DeviceType.TV -> 1300.dp
        }

    /** 字号缩放：TV 远距离观看需放大 */
    val fontScale: Float
        get() = when (deviceType) {
            DeviceType.PHONE -> 1f
            DeviceType.TABLET -> 1.05f
            DeviceType.TV -> 1.25f
        }
}

/** 全局自适应信息；默认 PHONE/COMPACT（编辑预览安全值） */
val LocalAdaptive = compositionLocalOf { AdaptiveInfo(DeviceType.PHONE, WidthClass.COMPACT) }

/**
 * 根布局注入：提供 [LocalAdaptive]，并在 TV 端统一放大字号（仅字体缩放，不改 dp，避免布局错乱）。
 *
 * @param displayMode 用户设置的显示模式；AUTO 按设备自动检测，否则强制按指定形态渲染
 */
@Composable
fun ProvideAdaptive(
    displayMode: cn.toside.music.compose.data.DisplayMode =
        cn.toside.music.compose.data.DisplayMode.AUTO,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val info = remember(
        context,
        configuration.screenWidthDp,
        configuration.smallestScreenWidthDp,
        displayMode,
    ) {
        val detected = detectDeviceType(context, configuration)
        val deviceType = when (displayMode) {
            cn.toside.music.compose.data.DisplayMode.AUTO -> detected
            cn.toside.music.compose.data.DisplayMode.PHONE -> DeviceType.PHONE
            cn.toside.music.compose.data.DisplayMode.TABLET -> DeviceType.TABLET
            cn.toside.music.compose.data.DisplayMode.TV -> DeviceType.TV
        }
        AdaptiveInfo(
            deviceType = deviceType,
            widthClass = when {
                configuration.screenWidthDp < 600 -> WidthClass.COMPACT
                configuration.screenWidthDp < 840 -> WidthClass.MEDIUM
                else -> WidthClass.EXPANDED
            },
        )
    }
    val density = LocalDensity.current
    val scaled = remember(density, info) { Density(density.density, density.fontScale * info.fontScale) }
    CompositionLocalProvider(
        LocalAdaptive provides info,
        LocalDensity provides scaled,
        content = content,
    )
}

private fun detectDeviceType(context: Context, configuration: Configuration): DeviceType {
    val pm = context.packageManager
    val leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val tvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (leanback || tvUiMode) return DeviceType.TV
    return if (configuration.smallestScreenWidthDp >= 600) DeviceType.TABLET else DeviceType.PHONE
}
