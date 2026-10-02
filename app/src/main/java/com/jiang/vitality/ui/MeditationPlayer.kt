package com.jiang.vitality.ui

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jiang.vitality.playback.MeditationPlaybackService
import java.io.File

enum class MusicPlaybackMode(val title: String) {
    PLAY_ONCE("播放一次"),
    CONTINUOUS("顺序播放"),
    REPEAT_ALL("列表循环"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放")
}

/** UI-facing controller for the Media3 playback service. */
class MeditationPlayer(context: Context) : Player.Listener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vitality_music", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var lastTracks: List<Pair<String, File>> = emptyList()

    var currentName by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMillis by mutableLongStateOf(0L)
        private set
    var durationMillis by mutableLongStateOf(0L)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    var mode by mutableStateOf(readMode())
        private set

    private val progressTicker = object : Runnable {
        override fun run() {
            updateState()
            mainHandler.postDelayed(this, if (isPlaying) 400L else 1_000L)
        }
    }

    init {
        val token = SessionToken(appContext, ComponentName(appContext, MeditationPlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }.onSuccess { connected ->
                    controller = connected
                    connected.addListener(this)
                    applyMode(connected)
                    updateState()
                    pendingAction?.invoke(connected)
                    pendingAction = null
                    mainHandler.removeCallbacks(progressTicker)
                    mainHandler.post(progressTicker)
                }.onFailure { playbackError = "播放器连接失败：${it.message ?: "未知错误"}" }
            }, ContextCompat.getMainExecutor(appContext))
        }
    }

    fun play(name: String, tracks: List<Pair<String, File>>) = withController { player ->
        val validTracks = tracks.filter { (_, file) -> file.isFile && file.length() > 0L }
        lastTracks = validTracks
        val selected = validTracks.indexOfFirst { it.first == name }
        if (selected < 0) {
            playbackError = "找不到音频文件，请重新导入"
            return@withController
        }
        playbackError = null
        val source = if (mode == MusicPlaybackMode.PLAY_ONCE) listOf(validTracks[selected]) else validTracks
        val startIndex = if (mode == MusicPlaybackMode.PLAY_ONCE) 0 else selected
        player.setMediaItems(source.map { (title, file) -> file.asMediaItem(title) }, startIndex, 0L)
        applyMode(player)
        player.prepare()
        player.play()
    }

    fun toggle() = withController {
        if (it.isPlaying) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_ENDED) {
                it.seekTo(0L)
                it.prepare()
            }
            it.play()
        }
    }
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController {
        if (it.currentPosition > 4_000L) it.seekTo(0L)
        else if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
    }
    fun seekTo(position: Long) = withController { it.seekTo(position.coerceAtLeast(0L)) }

    fun setMode(value: MusicPlaybackMode, tracks: List<Pair<String, File>> = lastTracks) {
        mode = value
        lastTracks = tracks.filter { (_, file) -> file.isFile && file.length() > 0L }
        prefs.edit().putString("mode", value.name).apply()
        controller?.let { player ->
            val title = currentName
            val selected = lastTracks.indexOfFirst { it.first == title }
            if (selected >= 0) {
                val wasPlaying = player.playWhenReady
                val position = player.currentPosition.coerceAtLeast(0L)
                val source = if (value == MusicPlaybackMode.PLAY_ONCE) listOf(lastTracks[selected]) else lastTracks
                val startIndex = if (value == MusicPlaybackMode.PLAY_ONCE) 0 else selected
                player.setMediaItems(source.map { (name, file) -> file.asMediaItem(name) }, startIndex, position)
                applyMode(player)
                player.prepare()
                player.playWhenReady = wasPlaying
            } else {
                applyMode(player)
            }
        }
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
        updateState()
    }

    override fun onEvents(player: Player, events: Player.Events) = updateState()
    override fun onPlayerError(error: PlaybackException) {
        playbackError = "无法播放：${error.errorCodeName}"
        updateState()
    }

    fun release() {
        mainHandler.removeCallbacks(progressTicker)
        controller?.removeListener(this)
        controller?.release()
        controller = null
        controllerFuture = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { pendingAction = action }
    }

    private fun applyMode(player: MediaController) {
        player.shuffleModeEnabled = mode == MusicPlaybackMode.SHUFFLE
        player.repeatMode = when (mode) {
            MusicPlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            MusicPlaybackMode.REPEAT_ALL, MusicPlaybackMode.SHUFFLE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun updateState() {
        val player = controller ?: return
        currentName = player.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
        isPlaying = player.isPlaying
        positionMillis = player.currentPosition.coerceAtLeast(0L)
        durationMillis = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        if (player.playerError == null && player.playbackState != Player.STATE_IDLE) playbackError = null
    }

    private fun readMode(): MusicPlaybackMode = runCatching {
        MusicPlaybackMode.valueOf(prefs.getString("mode", null) ?: MusicPlaybackMode.CONTINUOUS.name)
    }.getOrDefault(MusicPlaybackMode.CONTINUOUS)
}

private fun File.asMediaItem(title: String): MediaItem = MediaItem.Builder()
    .setMediaId(title)
    .setUri(Uri.fromFile(this))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist("江 · 冥想")
            .setIsPlayable(true)
            .build()
    )
    .build()
