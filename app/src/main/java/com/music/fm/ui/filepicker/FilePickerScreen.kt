package com.music.fm.ui.filepicker

import java.io.File
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.component.lxFocusable

/**
 * TV 端本地文件选择页（音源脚本导入用）。
 * TV 系统普遍没有 SAF 文件选择器，这里自建目录浏览器：
 *  - 列出内置存储 / U 盘根目录，逐级浏览，只显示目录和 .js 文件；
 *  - 所有行遥控器可聚焦、OK 键点击，进入目录自动聚焦首行；
 *  - 选中 .js 后通过 [onFileSelected] 返回绝对路径。
 */
@Composable
private fun FilePickerContent(
    onFileSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    extensions: Set<String> = setOf("js"),
    title: String = "选择音源脚本文件",
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current

    // 起始根目录：内置存储优先，其次枚举 /storage 下的挂载点（U 盘等）
    val roots = remember {
        buildList {
            val internal = File("/storage/emulated/0")
            if (internal.isDirectory && internal.canRead()) add(internal)
            File("/storage").listFiles()?.forEach { f ->
                if (f.isDirectory && f.canRead() && f.absolutePath != internal.absolutePath) {
                    // /storage 下的 self / emulated 目录是内部存储别名，跳过重复
                    if (f.name != "self" && f.name != "emulated") add(f)
                }
            }
            if (isEmpty() && File("/sdcard").isDirectory) add(File("/sdcard"))
        }.ifEmpty { listOf(File("/")) }
    }
    var currentDir by remember { mutableStateOf(roots.first()) }

    val entries = remember(currentDir) {
        val dirs = mutableListOf<File>()
        val files = mutableListOf<File>()
        currentDir.listFiles()?.forEach { f ->
            if (f.isDirectory) dirs += f else if (extensions.any { f.extension.equals(it, true) }) files += f
        }
        dirs.sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
    }
    val atRoot = roots.any { it.absolutePath == currentDir.absolutePath }

    // 进入目录后自动聚焦首行，遥控器有起点
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(currentDir) {
        runCatching { firstFocus.requestFocus() }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = adaptive.pagePadding, vertical = 12.dp)) {
        // 标题 + 当前路径
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            currentDir.absolutePath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(10.dp))

        if (!atRoot || currentDir.parentFile != null) {
            PickerRow(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                iconTint = MaterialTheme.colorScheme.secondary,
                title = "上级目录",
                subtitle = currentDir.parentFile?.absolutePath ?: "/",
                focusRequester = if (entries.isEmpty()) firstFocus else null,
                onClick = {
                    val parent = currentDir.parentFile
                    if (parent != null && parent.canRead()) currentDir = parent else onDismiss()
                },
            )
        }

        when {
            entries.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "此目录下没有可导入的 ${extensions.joinToString(" / ") { ".$it" }} 文件",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries) { index, entry ->
                    val isDir = entry.isDirectory
                    PickerRow(
                        icon = if (isDir) Icons.Default.Folder else Icons.Default.Description,
                        iconTint = if (isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        title = entry.name,
                        subtitle = if (isDir) "目录" else formatSize(entry.length()),
                        focusRequester = if (index == 0) firstFocus else null,
                        onClick = {
                            if (isDir) {
                                currentDir = entry
                            } else {
                                onFileSelected(entry.absolutePath)
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var m = Modifier
        .fillMaxWidth()
        .lxFocusable(shape = RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    Row(
        modifier = m.padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / 1024f / 1024f)
}


/** 是否已有读取外部存储（浏览任意目录）的权限 */
private fun hasStoragePermission(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

/**
 * TV 端本地文件选择页入口：先确认存储权限，未授权时显示说明与授权按钮，
 * 授权后（从系统设置页返回时自动重新检查）进入目录浏览。
 */
@Composable
fun FilePickerScreen(
    onFileSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    extensions: Set<String> = setOf("js"),
    title: String = "选择音源脚本文件",
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasStoragePermission(ctx)) }
    var hint by remember { mutableStateOf<String?>(null) }

    // 从系统授权页 / 权限弹窗返回后重新检查
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasStoragePermission(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = hasStoragePermission(ctx) }

    if (granted) {
        FilePickerContent(onFileSelected, onDismiss, extensions, title, modifier)
        return
    }

    val adaptive = LocalAdaptive.current
    val authFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { authFocus.requestFocus() } }

    Column(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = adaptive.pagePadding, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "需要「文件访问」权限才能浏览并选择本地文件。授权后请返回本页，将自动继续。",
            style = MaterialTheme.typography.bodyMedium,
        )
        hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val pkg = Uri.parse("package:${ctx.packageName}")
                        val ok = runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
                            )
                        }.isSuccess || runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }.isSuccess
                        if (!ok) hint = "无法打开系统授权页，请在系统设置 → 应用 → FMusic → 权限 中手动开启文件访问"
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier.lxFocusable(focusRequester = authFocus, shape = RoundedCornerShape(50)),
            ) { Text("去授权") }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.lxFocusable(shape = RoundedCornerShape(50)),
            ) { Text("返回") }
        }
    }
}
