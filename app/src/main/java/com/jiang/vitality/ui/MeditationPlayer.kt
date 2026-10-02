package com.jiang.vitality.ui

import android.content.Context
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.runtime.mutableStateOf
import java.io.File

/**
 * 冥想音乐播放器：MediaPlayer 循环播放 + MediaSession 集成，
 * 让播放能被系统识别（通知栏媒体控制、锁屏、耳机按键）。
 */
class MeditationPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var session: MediaSession? = null

    private val currentNameState = mutableStateOf<String?>(null)
    private val isPlayingState = mutableStateOf(false)

    val currentName: String? get() = currentNameState.value
    val isPlaying: Boolean get() = isPlayingState.value

    private fun ensureSession() {
        if (session == null) {
            session = MediaSession(appContext, "vitality_meditation").apply {
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() = resume()
                    override fun onPause() = pause()
                    override fun onStop() = stop()
                })
                setActive(true)
            }
        }
    }

    fun play(file: File, name: String) {
        ensureSession()
        if (currentNameState.value == name && mediaPlayer != null) {
            toggle()
            return
        }
        releasePlayer()
        currentNameState.value = name
        val nextPlayer = MediaPlayer()
        mediaPlayer = runCatching {
            nextPlayer.apply {
                setDataSource(file.absolutePath)
                isLooping = true
                setOnPreparedListener { player ->
                    player.start()
                    isPlayingState.value = true
                    publishState()
                }
                setOnErrorListener { failedPlayer, _, _ ->
                    runCatching { failedPlayer.reset() }
                    isPlayingState.value = false
                    currentNameState.value = null
                    publishState()
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            runCatching { nextPlayer.release() }
            currentNameState.value = null
            isPlayingState.value = false
            publishState()
            return
        }
        publishMetadata(name)
        publishState()
    }

    fun toggle() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) pause() else resume()
    }

    private fun resume() {
        mediaPlayer?.start()
        isPlayingState.value = true
        publishState()
    }

    private fun pause() {
        mediaPlayer?.pause()
        isPlayingState.value = false
        publishState()
    }

    fun stop() {
        releasePlayer()
        currentNameState.value = null
        isPlayingState.value = false
        publishState()
    }

    fun release() {
        releasePlayer()
        session?.release()
        session = null
    }

    private fun releasePlayer() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    private fun publishMetadata(name: String) {
        session?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "江 · 冥想")
                .build()
        )
    }

    private fun publishState() {
        val state = if (isPlayingState.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }
}
