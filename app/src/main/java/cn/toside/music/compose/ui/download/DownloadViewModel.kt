package cn.toside.music.compose.ui.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.toside.music.compose.MusicApplication
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.download.DownloadTask
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 下载管理（歌曲 + MV 任务队列） */
class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as MusicApplication).graph.downloadRepository

    val tasks: StateFlow<List<DownloadTask>> = repository.tasks

    fun addSongTask(info: MusicInfo, quality: String) {
        repository.addSongTask(info, quality)
    }

    fun addMvTask(info: MusicInfo) {
        repository.addMvTask(info)
    }

    fun pause(id: String) = repository.pauseTask(id)
    fun resume(id: String) = repository.resumeTask(id)
    fun retry(id: String) = repository.retryTask(id)
    fun remove(id: String) = repository.removeTask(id)
    fun clearCompleted() = repository.clearCompleted()

    companion object {
        val Factory = viewModelFactory {
            initializer { DownloadViewModel(this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application) }
        }
    }
}
