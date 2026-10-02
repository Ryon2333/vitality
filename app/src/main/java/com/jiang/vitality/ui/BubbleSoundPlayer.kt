package com.jiang.vitality.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jiang.vitality.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class BubbleSoundPlayer(context: Context) {
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loaded += sampleId
            }
        }

    private val callItADay = pool.load(context, R.raw.bubble_call_it_day, 1)
    private val restSounds = intArrayOf(
        pool.load(context, R.raw.bubble_rest_1, 1),
        pool.load(context, R.raw.bubble_rest_2, 1),
        pool.load(context, R.raw.bubble_rest_3, 1)
    )
    private var lastRestSound = -1
    private var lastRestPlayAt = 0L

    fun playCallItADay() {
        play(callItADay)
    }

    fun playRandomRestBubble() {
        val now = SystemClock.elapsedRealtime()
        // Consecutive taps within 2.5s stay silent so rapid dismiss taps don't stack pops.
        if (now - lastRestPlayAt < 2_500L) return
        val available = restSounds.filter { it in loaded && it != lastRestSound }
            .ifEmpty { restSounds.filter { it in loaded } }
        if (available.isEmpty()) return
        lastRestPlayAt = now
        lastRestSound = available[Random.nextInt(available.size)]
        play(lastRestSound)
    }

    fun release() {
        pool.release()
        loaded.clear()
    }

    private fun play(sampleId: Int) {
        if (sampleId in loaded) pool.play(sampleId, .82f, .82f, 1, 0, 1f)
    }
}

@Composable
fun rememberBubbleSoundPlayer(): BubbleSoundPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { BubbleSoundPlayer(context) }
    DisposableEffect(player) {
        onDispose(player::release)
    }
    return player
}
