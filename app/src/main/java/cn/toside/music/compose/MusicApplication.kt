package cn.toside.music.compose

import android.app.Application
import cn.toside.music.compose.data.MyListRepository
import cn.toside.music.compose.data.SettingsRepository
import cn.toside.music.compose.data.UserScriptRepository
import cn.toside.music.compose.js.UserApiController
import cn.toside.music.compose.player.PlayerRepository
import com.whl.quickjs.android.QuickJSLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 手动 DI 容器（一期不引入 Hilt）：JS 运行时、仓库、播放器。
 */
class MusicGraph(private val app: Application) {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(app) }

    val userScriptRepository: UserScriptRepository by lazy { UserScriptRepository(app) }

    val myListRepository: MyListRepository by lazy { MyListRepository(app) }

    val jsController: UserApiController by lazy {
        UserApiController(userScriptMetaProvider = { text -> userScriptRepository.parseMetaJson(text) })
    }

    val playerRepository: PlayerRepository by lazy {
        PlayerRepository(app, jsController, settingsRepository)
    }

    val downloadRepository: cn.toside.music.compose.download.DownloadRepository by lazy {
        cn.toside.music.compose.download.DownloadRepository(app, jsController, settingsRepository)
    }

    /** JS 引擎启动较慢（evaluate 650KB IIFE），放后台线程 */
    fun startJsEngine() {
        Thread({
            try {
                jsController.start()
                loadSavedScript()
            } catch (e: Throwable) {
                // 引擎启动失败不杀进程：UI 降级可用（搜索/播放会提示未就绪）
                android.util.Log.e("MusicGraph", "js engine boot failed", e)
            }
        }, "js-boot").start()
    }

    /** 冷启动后恢复上次选中的音源脚本 */
    private fun loadSavedScript() {
        // 等 JS 沙箱就绪
        val deadline = System.currentTimeMillis() + 30_000
        while (!jsController.isReady() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        if (!jsController.isReady()) return
        val settings = kotlinx.coroutines.runBlocking { settingsRepository.settings.first() }
        val scriptId = settings.apiScriptId
        if (scriptId.isEmpty()) return
        val text = kotlinx.coroutines.runBlocking { userScriptRepository.readScript(scriptId) } ?: return
        jsController.setActiveApi(scriptId)
        jsController.loadUserScript(scriptId, text)
    }

    fun release() {
        jsController.shutdown()
    }
}

class MusicApplication : Application() {
    lateinit var graph: MusicGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // QuickJS so 必须先加载，否则 QuickJSContext.create() 抛致命异常（js-boot 线程未捕获会杀进程）
        QuickJSLoader.init()
        graph = MusicGraph(this)
        graph.startJsEngine()
    }

    override fun onTerminate() {
        graph.release()
        super.onTerminate()
    }

    companion object {
        @Volatile
        lateinit var instance: MusicApplication
            private set
    }
}
