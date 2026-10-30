package cn.toside.music.compose.ui.setting

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.toside.music.compose.MusicApplication
import cn.toside.music.compose.data.AppSettings
import cn.toside.music.compose.data.SettingsRepository
import cn.toside.music.compose.data.ThemeMode
import cn.toside.music.compose.data.UserScript
import cn.toside.music.compose.data.UserScriptRepository
import cn.toside.music.compose.data.RemoteScriptFetcher
import cn.toside.music.compose.data.model.Qualitys
import cn.toside.music.compose.data.model.Sources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingUiState(
    val settings: AppSettings = AppSettings(),
    val scripts: List<UserScript> = emptyList(),
    val message: String? = null,
    val importing: Boolean = false,
)

class SettingViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph
    private val settingsRepository: SettingsRepository = graph.settingsRepository
    private val userScriptRepository: UserScriptRepository = graph.userScriptRepository

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()

    /** 供主题等直接消费 */
    val settingsFlow = settingsRepository.settings

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _uiState.value = _uiState.value.copy(settings = s)
            }
        }
        viewModelScope.launch {
            userScriptRepository.scripts.collect { list ->
                _uiState.value = _uiState.value.copy(scripts = list)
            }
        }
        refreshScripts()
    }

    private fun refreshScripts() {
        viewModelScope.launch {
            val list = userScriptRepository.load()
            _uiState.value = _uiState.value.copy(scripts = list)
        }
    }

    /** 从 uri（SAF 选择的 .js 文件）导入脚本 */
    fun importScript(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importing = true, message = null)
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalArgumentException("无法读取文件")
                }
                applyScript(text, sourceUrl = "")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(message = "导入失败：${e.message}")
            }
            _uiState.value = _uiState.value.copy(importing = false)
        }
    }

    /**
     * 从远程链接导入脚本。
     * 支持 js 直链，也支持 GitHub / Gitee 的 blob 网页链接（自动转 raw 直链）。
     * 若之前已用同一链接导入过，则覆盖更新原脚本。
     */
    fun importScriptFromUrl(rawUrl: String) {
        val url = RemoteScriptFetcher.normalize(rawUrl)
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "请输入脚本链接")
            return
        }
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            _uiState.value = _uiState.value.copy(message = "链接格式不正确，需以 http(s):// 开头")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importing = true, message = null)
            runCatching {
                val text = withContext(Dispatchers.IO) { RemoteScriptFetcher.fetch(url) }
                val existingId = userScriptRepository.findBySourceUrl(url)
                applyScript(text, sourceUrl = url, existingId = existingId?.id)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(message = "导入失败：${e.message}")
            }
            _uiState.value = _uiState.value.copy(importing = false)
        }
    }

    /** 更新：对远程导入的脚本重新拉取一次来源链接 */
    fun refreshScript(id: String) {
        val script = _uiState.value.scripts.firstOrNull { it.id == id } ?: return
        if (script.sourceUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "该脚本为本地导入，无法更新")
            return
        }
        importScriptFromUrl(script.sourceUrl)
    }

    /** 校验 + 落盘 + 设为当前源并加载 */
    private suspend fun applyScript(
        text: String,
        sourceUrl: String,
        existingId: String? = null,
    ): UserScript {
        // 两代模板：新模板直接用 globalThis.lx（无 lx_setup 调用），旧模板调 lx_setup
        val isLxSource = text.contains("globalThis.lx") ||
            text.contains("lx_setup") ||
            text.contains("EVENT_NAMES")
        if (!isLxSource) {
            throw IllegalArgumentException("不是有效的 LX Music 音源脚本")
        }
        val script = userScriptRepository.save(text, existingId, sourceUrl)
        // 导入后自动设为当前 api 源并加载
        settingsRepository.setApiScriptId(script.id)
        loadScript(script.id)
        _uiState.value = _uiState.value.copy(message = "已导入：${script.name}")
        return script
    }

    /** 设为当前 api 源（加载到 JS 沙箱） */
    fun selectScript(id: String) {
        viewModelScope.launch {
            settingsRepository.setApiScriptId(id)
            loadScript(id)
        }
    }

    private fun loadScript(id: String) {
        viewModelScope.launch {
            val text = userScriptRepository.readScript(id) ?: return@launch
            graph.jsController.setActiveApi(id)
            graph.jsController.loadUserScript(id, text)
        }
    }

    fun deleteScript(id: String) {
        viewModelScope.launch {
            userScriptRepository.delete(id)
            if (_uiState.value.settings.apiScriptId == id) {
                settingsRepository.setApiScriptId("")
                graph.jsController.setActiveApi("")
            }
            _uiState.value = _uiState.value.copy(message = "已删除")
        }
    }

    fun setQuality(quality: String) {
        viewModelScope.launch { settingsRepository.setDefaultQuality(quality) }
    }

    fun toggleSearchSource(source: String) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().searchSources
            val next = if (source in current) current - source else current + source
            settingsRepository.setSearchSources(next)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MusicApplication
                SettingViewModel(app)
            }
        }
    }
}
