package cn.toside.music.compose.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.toside.music.compose.data.SettingsRepository
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.MusicUrl
import cn.toside.music.compose.js.UserApiController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val Context.downloadsDataStore: DataStore<Preferences> by preferencesDataStore(name = "downloads")

/**
 * 下载仓库（对齐 mobile 版 core/download.ts + downloadMv.ts）：
 *  - 单任务队列串行下载，带进度 / 速度上报
 *  - 歌曲：取 URL（经音源脚本）→ 下载 → 可选写 .lrc 歌词
 *  - MV：wy/kg 内置直链 → 下载 mp4
 *  - 存储：API 29+ 写入公共媒体库（Music/LX Music、Movies/LX Music，无需权限）；
 *          API 24-28 写入应用外部私有目录（Music/、Movies/）
 *  - 任务列表持久化，重启后未完成任务标记为失败可重试
 */
class DownloadRepository(
    private val context: Context,
    private val jsController: UserApiController,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val isProcessing = AtomicBoolean(false)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val songDir: File by lazy {
        File(context.getExternalFilesDir(null), "Music").apply { mkdirs() }
    }
    private val mvDir: File by lazy {
        File(context.getExternalFilesDir(null), "Movies").apply { mkdirs() }
    }

    init {
        scope.launch {
            val saved = context.downloadsDataStore.data.first()[TASKS_KEY]
            val list = saved?.let { DownloadTask.listFromJson(it) } ?: emptyList()
            // 重启后进行中/等待中的任务已断线，标记失败供重试
            _tasks.value = list.map {
                if (it.status == DownloadStatus.WAITING || it.status == DownloadStatus.DOWNLOADING) {
                    it.copy(status = DownloadStatus.ERROR, errorMsg = "应用已重启，请重试", speed = "")
                } else it
            }
        }
    }

    private suspend fun persist() {
        context.downloadsDataStore.edit {
            it[TASKS_KEY] = DownloadTask.listToJson(_tasks.value)
        }
    }

    private fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        synchronized(_tasks) {
            _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        }
        // 持久化放后台，避免阻塞进度刷新
        scope.launch { runCatching { persist() } }
    }

    private fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    // ── 添加任务 ─────────────────────────────────────────────────

    /** 添加歌曲下载任务（同 id 去重，与 mobile 一致 id = md5(id-quality)） */
    fun addSongTask(info: MusicInfo, quality: String) {
        val ext = if (quality.startsWith("flac")) "flac" else "mp3"
        val fileName = filterFileName("${info.name} - ${info.singer}") + ".$ext"
        val id = md5("${info.uniqueKey}-$quality")
        addTask(
            DownloadTask(
                id = id,
                type = DownloadTaskType.SONG,
                source = info.source,
                musicInfoJson = info.toPersistentJson().toString(),
                name = info.name,
                singer = info.singer,
                quality = quality,
                fileName = fileName,
                status = DownloadStatus.WAITING,
            ),
        )
    }

    /** 添加 MV 下载任务（id = 歌曲唯一 key，同 mobile） */
    fun addMvTask(info: MusicInfo) {
        if (info.source != "wy" && info.source != "kg") {
            setToast("该来源暂不支持下载MV")
            return
        }
        val mvId = info.mv ?: run { setToast("该歌曲没有MV"); return }
        val fileName = filterFileName("${info.name} - ${info.singer}") + ".mp4"
        addTask(
            DownloadTask(
                id = md5("${info.uniqueKey}-mv"),
                type = DownloadTaskType.MV,
                source = info.source,
                musicInfoJson = info.toPersistentJson().toString(),
                name = info.name,
                singer = info.singer,
                quality = "",
                fileName = fileName,
                status = DownloadStatus.WAITING,
            ),
        )
    }

    private fun addTask(task: DownloadTask) {
        if (_tasks.value.any { it.id == task.id }) {
            setToast("任务已存在")
            return
        }
        synchronized(_tasks) { _tasks.value = _tasks.value + task }
        scope.launch { runCatching { persist() } }
        setToast("已加入下载队列：${task.fileName}")
        processQueue()
    }

    // ── 任务操作（暂停 / 继续 / 重试 / 删除） ─────────────────────

    fun pauseTask(id: String) {
        val task = find(id) ?: return
        if (task.status != DownloadStatus.DOWNLOADING && task.status != DownloadStatus.WAITING) return
        update(id) { it.copy(status = DownloadStatus.PAUSED, speed = "") }
        jobs[id]?.cancel()
        setToast("${task.fileName} 已暂停")
    }

    fun resumeTask(id: String) {
        val task = find(id) ?: return
        if (task.status != DownloadStatus.PAUSED) return
        update(id) {
            it.copy(
                status = DownloadStatus.WAITING,
                errorMsg = "",
                percent = 0f, downloaded = 0, total = 0, speed = "",
            )
        }
        processQueue()
    }

    fun retryTask(id: String) {
        val task = find(id) ?: return
        if (task.status != DownloadStatus.ERROR) return
        update(id) {
            it.copy(
                status = DownloadStatus.WAITING,
                errorMsg = "",
                percent = 0f, downloaded = 0, total = 0, speed = "",
            )
        }
        processQueue()
    }

    fun removeTask(id: String) {
        val task = find(id) ?: return
        if (task.status == DownloadStatus.DOWNLOADING) {
            update(id) { it.copy(status = DownloadStatus.PAUSED) }
            jobs[id]?.cancel()
        }
        // 完成的文件保留，未完成的清理（简化：公共媒体库的半截文件由 IS_PENDING 自动失效）
        synchronized(_tasks) { _tasks.value = _tasks.value.filterNot { it.id == id } }
        scope.launch { runCatching { persist() } }
        processQueue()
    }

    fun clearCompleted() {
        synchronized(_tasks) {
            _tasks.value = _tasks.value.filterNot { it.status == DownloadStatus.COMPLETED }
        }
        scope.launch { runCatching { persist() } }
    }

    // ── 队列调度（单任务串行，同 mobile processQueue） ────────────

    private fun processQueue() {
        if (!isProcessing.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val task = _tasks.value.firstOrNull { it.status == DownloadStatus.WAITING }
                        ?: break
                    runTask(task)
                }
            } finally {
                isProcessing.set(false)
            }
            // 执行期间新加入的任务再拉起一轮
            if (_tasks.value.any { it.status == DownloadStatus.WAITING }) processQueue()
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        update(task.id) { it.copy(status = DownloadStatus.DOWNLOADING, errorMsg = "", speed = "") }
        val job = scope.launch {
            try {
                when (task.type) {
                    DownloadTaskType.SONG -> downloadSong(task)
                    DownloadTaskType.MV -> downloadMv(task)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 用户主动暂停导致的取消，不标记失败
                if (find(task.id)?.status != DownloadStatus.PAUSED) {
                    update(task.id) { it.copy(status = DownloadStatus.ERROR, errorMsg = e.message ?: "下载失败", speed = "") }
                    setToast("${task.fileName} 下载失败：${e.message}")
                }
            }
        }
        jobs[task.id] = job
        try {
            job.join()
        } finally {
            jobs.remove(task.id)
        }
    }

    // ── 歌曲下载 ─────────────────────────────────────────────────

    private suspend fun downloadSong(task: DownloadTask) = withContext(Dispatchers.IO) {
        val urlJson = jsController.musicUrl(task.source, task.musicInfoJson, task.quality)
        val url = MusicUrl.parse(urlJson)?.url
            ?: throw IllegalStateException("获取下载地址失败")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = insertMediaStore(
                task, MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                1, "Music/LX Music", audioMime(task.quality),
            ) ?: throw IllegalStateException("创建下载文件失败")
            try {
                downloadToFile(task, url) { contentOut(uri) }
                finishMediaStore(uri)
                if (settingsRepository.settings.first().downloadLyric) {
                    runCatching { downloadLyric(task) }
                }
            } catch (e: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val file = File(songDir, task.fileName)
            try {
                downloadToFile(task, url) { file.outputStream() }
                if (settingsRepository.settings.first().downloadLyric) {
                    runCatching { downloadLyric(task) }
                }
            } catch (e: Throwable) {
                runCatching { file.delete() }
                throw e
            }
            scanFile(file)
        }
        update(task.id) { it.copy(status = DownloadStatus.COMPLETED, percent = 1f, speed = "") }
        setToast("${task.fileName} 下载完成!")
    }

    // ── MV 下载 ──────────────────────────────────────────────────

    private suspend fun downloadMv(task: DownloadTask) = withContext(Dispatchers.IO) {
        val mvId = task.mvId ?: throw IllegalStateException("该歌曲没有MV")
        val urlJson = jsController.mvUrl(task.source, mvId)
        val url = cn.toside.music.compose.data.model.MusicUrl.parse(urlJson)?.url
            ?: throw IllegalStateException("获取MV下载地址失败")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = insertMediaStore(
                task, MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                null, "Movies/LX Music", "video/mp4",
            ) ?: throw IllegalStateException("创建下载文件失败")
            try {
                downloadToFile(task, url) { contentOut(uri) }
                finishMediaStore(uri)
            } catch (e: Throwable) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val file = File(mvDir, task.fileName)
            try {
                downloadToFile(task, url) { file.outputStream() }
            } catch (e: Throwable) {
                runCatching { file.delete() }
                throw e
            }
            scanFile(file)
        }
        update(task.id) { it.copy(status = DownloadStatus.COMPLETED, percent = 1f, speed = "") }
        setToast("${task.fileName} MV下载完成!")
    }

    // ── 通用下载（带进度上报） ────────────────────────────────────

    private suspend fun downloadToFile(
        task: DownloadTask,
        url: String,
        makeStream: suspend () -> OutputStream,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            // mobile 对 wy 媒体使用空 UA
            header("User-Agent", if (task.source == "wy") "" else DOWNLOAD_UA)
        }.build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("无响应内容")
            val total = body.contentLength()
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L
            val out = makeStream()
            body.byteStream().use { input ->
                out.use { output ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        // 暂停/取消时中断写入
                        if (find(task.id)?.status == DownloadStatus.PAUSED) {
                            throw kotlinx.coroutines.CancellationException("paused")
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 500) {
                            val speed = (written - lastBytes) * 1000 / (now - lastTime)
                            val percent = if (total > 0) written.toFloat() / total else 0f
                            update(task.id) {
                                it.copy(
                                    percent = percent,
                                    downloaded = written,
                                    total = if (total > 0) total else 0,
                                    speed = "${sizeFormate(speed)}/s",
                                )
                            }
                            lastBytes = written
                            lastTime = now
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    // ── MediaStore（API 29+ 公共媒体库） ─────────────────────────

    private fun insertMediaStore(
        task: DownloadTask,
        collection: Uri,
        isMusic: Int?,
        relativePath: String,
        mime: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, task.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            isMusic?.let { put(MediaStore.Audio.AudioColumns.IS_MUSIC, it) }
        }
        return context.contentResolver.insert(collection, values)
    }

    private fun contentOut(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法写入文件")

    private fun finishMediaStore(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    private suspend fun downloadLyric(task: DownloadTask) = withContext(Dispatchers.IO) {
        val lyricJson = jsController.lyric(task.source, task.musicInfoJson)
        val lyric = cn.toside.music.compose.data.model.LyricInfo.parse(lyricJson)?.lyric
            ?: return@withContext
        val baseName = task.fileName.substringBeforeLast('.')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.lrc")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/LX Music")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
            ) ?: return@withContext
            context.contentResolver.openOutputStream(uri)?.use { it.write(lyric.toByteArray()) }
            finishMediaStore(uri)
        } else {
            File(songDir, "$baseName.lrc").writeText(lyric)
        }
    }

    private fun scanFile(file: File) {
        runCatching {
            val paths = arrayOf(file.absolutePath)
            val mimes = arrayOf(if (file.name.endsWith(".mp4")) "video/mp4" else "audio/mpeg")
            android.media.MediaScannerConnection.scanFile(context, paths, mimes, null)
        }
    }

    private fun audioMime(quality: String): String =
        if (quality.startsWith("flac")) "audio/flac" else "audio/mpeg"

    // ── 工具 ─────────────────────────────────────────────────────

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private fun setToast(msg: String) {
        mainHandler.post {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val TASKS_KEY = stringPreferencesKey("download_tasks_v1")
        private const val DOWNLOAD_UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36"

        fun filterFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

        fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
