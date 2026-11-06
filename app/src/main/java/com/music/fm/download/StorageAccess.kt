package com.music.fm.download

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 下载目录与存储权限。歌曲 / MV 直接写公共目录（不使用应用私有目录）：
 *  - 歌曲：/sdcard/Music/fmusic
 *  - MV：  /sdcard/Movie/fmusic
 * Android 11+ 需「所有文件访问」，Android 10 及以下需 WRITE_EXTERNAL_STORAGE。
 */
object StorageAccess {

    private val root: File get() = Environment.getExternalStorageDirectory()

    val musicDir: File get() = File(root, "Music/fmusic")
    val movieDir: File get() = File(root, "Movie/fmusic")

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** Android 11+ 跳转系统「所有文件访问」授权页；失败返回 false */
    fun openAllFilesSettings(ctx: Context): Boolean {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess || runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}
