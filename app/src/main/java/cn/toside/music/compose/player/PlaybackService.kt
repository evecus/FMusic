package cn.toside.music.compose.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cn.toside.music.compose.MainActivity

/**
 * 播放服务：Media3 MediaSessionService + ExoPlayer。
 * 实际播放控制经 [PlayerRepository] 的 MediaController 完成。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // 后台播放 CPU 优化（media3 1.4.1 源码实证）：
            // 默认播放中 ExoPlayer 主循环固定每 10ms 唤醒一次（每秒 100 次空转），
            // CPU 无法进入深度空闲。开启动态调度后，主循环按音频缓冲实际需要
            // 睡眠（上限 1s），缓冲将尽/seek/切歌时由 Renderer 回调立即唤醒。
            .experimentalSetDynamicSchedulingEnabled(true)
            .build()
        player = exo
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(activityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        if (session == null || !session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
        }
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
