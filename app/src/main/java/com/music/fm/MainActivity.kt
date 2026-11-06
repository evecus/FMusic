package com.music.fm

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.music.fm.download.StorageAccess
import com.music.fm.ui.MainScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 下载缺少存储权限时，由下载仓库通知这里去申请
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as MusicApplication).graph.downloadRepository
                    .permissionRequests.collect {
                        if (StorageAccess.hasAccess(this@MainActivity)) return@collect
                        if (Build.VERSION.SDK_INT >= 30) {
                            StorageAccess.openAllFilesSettings(this@MainActivity)
                        } else {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
            }
        }
        // targetSdk 35 强制 edge-to-edge：必须显式开启并交给 Compose 处理 insets
        // 仅浅色主题：系统栏固定使用深色图标，不随系统深色模式变化
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                MainScreen()
            }
        }
    }
}
