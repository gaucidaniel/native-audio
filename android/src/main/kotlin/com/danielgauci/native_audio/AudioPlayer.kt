package com.danielgauci.native_audio

import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.util.concurrent.TimeUnit

class AudioPlayer(
        onLoaded: ((duration: Long) -> Unit)? = null,
        onProgressChanged: ((currentTime: Long) -> Unit)? = null,
        onCompleted: (() -> Unit)? = null
) {

    private val progressCallbackHandler: Handler by lazy { Handler() }
    private val progressCallback: Runnable by lazy {
        Runnable {
            val progress = mediaPlayer.currentPosition.toLong()
            if (progress != currentProgress) {
                onProgressChanged?.invoke(progress)
                currentProgress = progress
            }

            progressCallbackHandler.postDelayed(progressCallback, TimeUnit.SECONDS.toMillis(1))
        }
    }

    private val mediaPlayer: MediaPlayer by lazy {
        MediaPlayer().apply {
            setOnPreparedListener {
                // Start audio once loaded
                start()

                // Notify callback
                onLoaded?.invoke(duration.toLong())

                // Setup progress callback
                progressCallbackHandler.postDelayed(progressCallback, TimeUnit.SECONDS.toMillis(1))

                // Update flags
                isLoaded = true
            }

            setOnCompletionListener {
                // Notify callback
                onCompleted?.invoke()

                // Clear progress callback
                progressCallbackHandler.removeCallbacks(progressCallback)

                // Update flags
                isLoaded = false
            }
        }
    }

    private var currentProgress = 0L
    private var isLoaded = false

    fun play(url: String) {
        if (mediaPlayer.isPlaying) stop()

        loadAudio(url) { resume() }
    }

    fun resume() {
        mediaPlayer.apply {
            if (isLoaded && !isPlaying) mediaPlayer.start()
        }
    }

    fun pause() {
        mediaPlayer.apply {
            if (isPlaying) mediaPlayer.pause()
        }
    }

    fun stop() {
        mediaPlayer.apply {
            if (isPlaying) {
                this.stop()
                this.reset()
            }
        }
    }

    fun seekTo(timeInMillis: Long) {
        mediaPlayer.apply {
            if (isLoaded) mediaPlayer.seekTo(timeInMillis.toInt())
        }
    }

    fun release() {
        mediaPlayer.release()
    }

    private fun loadAudio(url: String, onAudioLoaded: () -> Unit) {
        mediaPlayer.apply {
            setOnErrorListener { mp, what, extra ->
                Log.d(this::class.java.simpleName, "OnError - Error code: $what Extra code: $extra")

                // Return false to trigger on complete
                false
            }

            reset()
            setDataSource(url)
            prepareAsync()
        }
    }
}