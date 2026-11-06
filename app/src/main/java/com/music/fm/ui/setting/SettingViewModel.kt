package com.music.fm.ui.setting

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.music.fm.MusicApplication
import com.music.fm.data.AppSettings
import com.music.fm.data.DisplayMode
import com.music.fm.data.LxmcCodec
import com.music.fm.data.LxmcParseResult
import com.music.fm.data.SettingsRepository
import com.music.fm.data.UserScript
import com.music.fm.data.UserScriptRepository
import com.music.fm.data.RemoteScriptFetcher
import com.music.fm.data.model.Qualitys
import com.music.fm.data.model.Sources
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
    /** 已解析、等待用户确认的列表导入 */
    val pendingListImport: LxmcParseResult? = null,
)

class SettingViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as MusicApplication).graph
    private val settingsRepository: SettingsRepository = graph.settingsRepository
    private val userScriptRepository: UserScriptRepository = graph.userScriptRepository

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState: StateFlow<SettingUiState> = _uiState.asStateFlow()

    /** 供外部直接消费 */
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

    /** 从本地文件路径导入（TV 端自建文件选择页，无 SAF） */
    fun importScriptFromFile(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importing = true, message = null)
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    val f = java.io.File(path)
                    if (!f.isFile || !f.canRead()) throw IllegalArgumentException("无法读取文件")
                    f.readText()
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
            throw IllegalArgumentException("不是有效的 FMusic 音源脚本")
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

    // ── 备份与恢复（列表导入 / 导出，lx .lxmc 互通） ──────────────

    private suspend fun buildLxmcText(): String {
        val all = graph.myListRepository.snapshotAll()
        // 与 lx 导出一致：不导出空的默认列表；FM 的「喜欢」始终导出
        return LxmcCodec.encode(all)
    }

    /** 导出到 SAF 选定的 uri（手机 / 平板） */
    fun exportListsToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = buildLxmcText()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalArgumentException("无法写入文件")
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(message = "列表导出成功")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(message = "导出失败：${e.message}")
            }
        }
    }

    /** 导出到系统「下载/FMusic」目录（TV 无 SAF 保存界面） */
    fun exportListsToDownloads() {
        viewModelScope.launch {
            runCatching {
                val text = buildLxmcText()
                withContext(Dispatchers.IO) { saveToDownloads(text) }
            }.onSuccess { name ->
                _uiState.value = _uiState.value.copy(message = "已导出到 下载/FMusic/$name")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(message = "导出失败：${e.message}")
            }
        }
    }

    private fun saveToDownloads(text: String): String {
        val ctx = getApplication<Application>()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val name = "fm_list_$stamp.lxmc"
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/FMusic",
                )
            }
            val uri = ctx.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
            ) ?: throw IllegalStateException("无法创建文件")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入文件")
        } else {
            val dir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                ),
                "FMusic",
            )
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建目录")
            java.io.File(dir, name).writeBytes(bytes)
        }
        return name
    }

    /** 读取并解析备份文件（SAF uri），解析成功后等待用户确认 */
    fun prepareListImport(uri: Uri) = prepareListImportInternal {
        getApplication<Application>().contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalArgumentException("无法读取文件")
    }

    /** 读取并解析备份文件（本地路径，TV 文件选择页） */
    fun prepareListImportFromFile(path: String) = prepareListImportInternal {
        val f = java.io.File(path)
        if (!f.isFile || !f.canRead()) throw IllegalArgumentException("无法读取文件")
        f.readText(Charsets.UTF_8)
    }

    private fun prepareListImportInternal(reader: () -> String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(importing = true, message = null)
            runCatching {
                val text = withContext(Dispatchers.IO) { reader() }
                LxmcCodec.decode(text.removePrefix("\uFEFF"))
            }.onSuccess { parsed ->
                _uiState.value = if (parsed.lists.isEmpty()) {
                    _uiState.value.copy(message = "文件中没有可导入的列表")
                } else {
                    _uiState.value.copy(pendingListImport = parsed)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(message = "导入失败：${e.message}")
            }
            _uiState.value = _uiState.value.copy(importing = false)
        }
    }

    fun confirmListImport() {
        val pending = _uiState.value.pendingListImport ?: return
        _uiState.value = _uiState.value.copy(pendingListImport = null)
        viewModelScope.launch {
            runCatching { graph.myListRepository.importLists(pending.lists) }
                .onSuccess { r ->
                    val extra = buildString {
                        if (r.truncated) append("，部分歌单超出 2000 首已截断")
                        if (r.listsSkipped > 0) append("，${r.listsSkipped} 个歌单因数量上限被跳过")
                        if (pending.skippedSongs > 0) append("，${pending.skippedSongs} 首不支持的歌曲(如本地歌曲)已忽略")
                    }
                    _uiState.value = _uiState.value.copy(
                        message = "已导入 ${r.lists} 个列表，共 ${r.songs} 首歌曲$extra",
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(message = "导入失败：${e.message}")
                }
        }
    }

    fun cancelListImport() {
        _uiState.value = _uiState.value.copy(pendingListImport = null)
    }

    /** 屏幕层直接投递提示消息（如 TV 无文件选择器的兜底提示） */
    fun notifyMessage(msg: String) {
        _uiState.value = _uiState.value.copy(message = msg)
    }

    fun setDisplayMode(mode: DisplayMode) {
        viewModelScope.launch { settingsRepository.setDisplayMode(mode) }
    }

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch { settingsRepository.setDownloadQuality(quality) }
    }

    fun setDownloadLyric(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDownloadLyric(enabled) }
    }

    fun setWriteTags(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWriteTags(enabled) }
    }

    fun setWriteCover(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWriteCover(enabled) }
    }

    fun setFileNameFormat(format: String) {
        viewModelScope.launch { settingsRepository.setFileNameFormat(format) }
    }

    fun setWriteRomaLyric(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWriteRomaLyric(enabled) }
    }

    fun setWriteEmbeddedLyric(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWriteEmbeddedLyric(enabled) }
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
