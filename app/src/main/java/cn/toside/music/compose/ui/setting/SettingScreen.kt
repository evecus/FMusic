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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.DisplayMode
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
    onImportUrl: (String) -> Unit,
    onRefreshScript: (String) -> Unit,
    onSelectScript: (String) -> Unit,
    onDeleteScript: (String) -> Unit,
    onSetQuality: (String) -> Unit,
    onToggleSearchSource: (String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDisplayMode: (DisplayMode) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showUrlDialog by remember { mutableStateOf(false) }
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
                    "导入 LX Music 自定义音源脚本（.js），可从本地文件导入，也可直接粘贴远程链接导入；" +
                        "脚本在独立 QuickJS 沙箱中运行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            launcher.launch(
                                arrayOf("text/javascript", "application/javascript", "*/*")
                            )
                        },
                        enabled = !state.importing,
                        modifier = Modifier.lxFocusable(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("本地文件导入")
                    }
                    OutlinedButton(
                        onClick = { showUrlDialog = true },
                        enabled = !state.importing,
                        modifier = Modifier.lxFocusable(),
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("链接导入")
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
                                    buildString {
                                        val hasMeta = script.version.isNotEmpty() || script.author.isNotEmpty()
                                        if (script.version.isNotEmpty()) append(script.version)
                                        if (script.author.isNotEmpty()) {
                                            if (script.version.isNotEmpty()) append(" · ")
                                            append(script.author)
                                        }
                                        if (script.sourceUrl.isNotBlank()) {
                                            if (hasMeta) append(" · ")
                                            append(shortUrl(script.sourceUrl))
                                        }
                                        if (isEmpty()) append(script.id.take(8))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = state.settings.apiScriptId == script.id,
                                    onClick = { onSelectScript(script.id) },
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (script.sourceUrl.isNotBlank()) {
                                        LxIconButton(
                                            onClick = { onRefreshScript(script.id) },
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "更新脚本",
                                        )
                                    }
                                    LxIconButton(
                                        onClick = { onDeleteScript(script.id) },
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "删除",
                                    )
                                }
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

                Text("显示模式", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "选择界面按哪种设备形态渲染；自动时按当前设备自动检测。" +
                        "切换后立即生效（导航结构：手机底部栏 / 平板与电视左侧栏）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.settings.displayMode == mode,
                            onClick = { onSetDisplayMode(mode) },
                            label = { Text(mode.label) },
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

    if (showUrlDialog) {
        ImportUrlDialog(
            importing = state.importing,
            onDismiss = { showUrlDialog = false },
            onConfirm = { url ->
                onImportUrl(url)
                showUrlDialog = false
            },
        )
    }
}

/**
 * 「链接导入」弹窗：输入 .js 直链即可远程导入音源。
 * 支持 GitHub / Gitee 的 blob 网页链接，会自动转为 raw 直链。
 * TV 上弹窗内控件均可被遥控器聚焦，输入框自动获焦。
 */
@Composable
private fun ImportUrlDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val adaptive = LocalAdaptive.current
    val clipboard = LocalClipboardManager.current
    val dialogFocus = remember { FocusRequester() }
    val inputFocus = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { inputFocus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        modifier = Modifier
            .lxFocusable(focusRequester = dialogFocus, showBorder = false)
            .then(if (adaptive.isTv) Modifier.widthIn(min = 420.dp, max = 620.dp) else Modifier),
        title = { Text("从链接导入音源") },
        text = {
            Column {
                Text(
                    "粘贴 .js 直链即可导入；GitHub / Gitee 的 blob 网页链接会自动转为 raw 直链。" +
                        "同一链接重复导入将覆盖更新已有脚本。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("脚本链接") },
                        placeholder = { Text("https://.../music-sdk.js") },
                        singleLine = true,
                        enabled = !importing,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { if (text.isNotBlank()) onConfirm(text.trim()) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(inputFocus),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            clipboard.getText()?.text?.trim()?.let { text = it }
                        },
                        enabled = !importing,
                        modifier = Modifier.lxFocusable(),
                    ) { Text("粘贴") }
                }
                if (importing) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "正在下载…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = !importing && text.isNotBlank(),
                modifier = Modifier.lxFocusable(),
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !importing,
                modifier = Modifier.lxFocusable(),
            ) { Text("取消") }
        },
    )
}

/** URL 简短展示：去掉协议头并截断 */
private fun shortUrl(url: String): String {
    var s = url.trim()
    if (s.startsWith("https://", true)) s = s.removePrefix("https://")
    else if (s.startsWith("http://", true)) s = s.removePrefix("http://")
    return if (s.length > 42) s.take(40) + "…" else s
}
