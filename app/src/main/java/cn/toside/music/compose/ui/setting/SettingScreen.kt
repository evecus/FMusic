package cn.toside.music.compose.ui.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.ThemeMode
import cn.toside.music.compose.data.model.Qualitys
import cn.toside.music.compose.data.model.Sources
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable

/** 设置页：自定义音源脚本、默认音质、搜索平台、主题（三端自适应，TV 端全部可聚焦） */
@Composable
fun SettingScreen(
    state: SettingUiState,
    onImport: (android.net.Uri) -> Unit,
    onSelectScript: (String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onSetQuality: (String) -> Unit,
    onToggleSearchSource: (String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val snackbarHostState = remember { SnackbarHostState() }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onConsumeMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 平板 / TV 上限制内容宽度并居中，避免文字横向拉得太长
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (adaptive.contentMaxWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                        Modifier.widthIn(max = adaptive.contentMaxWidth)
                    } else Modifier
                )
                .align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(adaptive.pagePadding),
            ) {
                Text("自定义音源", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "导入 LX Music 自定义音源脚本（.js），脚本在独立 QuickJS 沙箱中运行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { launcher.launch(arrayOf("text/javascript", "application/javascript", "*/*")) },
                    enabled = !state.importing,
                    modifier = Modifier.lxFocusable(),
                ) {
                    if (state.importing) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.height(4.dp))
                        Text("导入脚本")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (state.scripts.isEmpty()) {
                    Text(
                        "尚未导入任何音源脚本。未导入时无法获取播放地址（内置取 URL 接口已移除）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    state.scripts.forEach { script ->
                        ListItem(
                            headlineContent = { Text(script.name) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        script.version.takeIf { it.isNotEmpty() },
                                        script.author.takeIf { it.isNotEmpty() },
                                    ).joinToString(" · ").ifEmpty { script.id.take(8) }
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = state.settings.apiScriptId == script.id,
                                    onClick = { onSelectScript(script.id) },
                                )
                            },
                            trailingContent = {
                                LxIconButton(
                                    onClick = { onDeleteScript(script.id) },
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                )
                            },
                            modifier = Modifier.lxFocusable(),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("默认音质", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Qualitys.all.forEach { q ->
                        FilterChip(
                            selected = state.settings.defaultQuality == q,
                            onClick = { onSetQuality(q) },
                            label = { Text(Qualitys.label(q)) },
                            modifier = Modifier.lxFocusable(shape = CircleShape),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("搜索平台", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Sources.all.forEach { s ->
                        FilterChip(
                            selected = s in state.settings.searchSources,
                            onClick = { onToggleSearchSource(s) },
                            label = { Text(Sources.label(s)) },
                            modifier = Modifier.lxFocusable(shape = CircleShape),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("主题", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                )
                            },
                            modifier = Modifier.lxFocusable(shape = CircleShape),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("关于", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "LX Music Compose · Jetpack Compose 重写版\n" +
                        "内置 kw / kg / tx / wy / mg / xm 六平台搜索与歌词；" +
                        "播放取 URL 依赖自定义音源脚本（与原版发货版一致）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
