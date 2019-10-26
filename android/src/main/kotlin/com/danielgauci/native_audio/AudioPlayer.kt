package com.danielgauci.native_audio

import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.util.concurrent.TimeUnit

class AudioPlayer(
        private val onLoaded: ((duration: Long) -> Unit)? = null,
        private val onProgressChanged: ((currentTime: Long) -> Unit)? = null,
        private val onCompleted: (() -> Unit)? = null
) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressCallbackHandler: Handler? = null
    private var progressCallback: Runnable? = null
    private var currentProgress = 0L
    private var isLoaded = false

    fun play(url: String) {
        if (mediaPlayer == null) initMediaPlayer()

        if (mediaPlayer?.isPlaying == true) stop()

        loadAudio(url)
        startListeningForProgress()
    }

    fun resume() {
        mediaPlayer?.apply { if (isLoaded && !isPlaying) start() }
        startListeningForProgress()
    }

    fun pause() {
        mediaPlayer?.apply { if (isPlaying) pause() }
        stopListeningForProgress()
    }

    fun stop(release: Boolean = true) {
        mediaPlayer?.apply {
            if (isPlaying) {
                this.stop()
                this.reset()
            }
        }

        if (release) release()
    }

    fun seekTo(timeInMillis: Long) {
        mediaPlayer?.apply { if (isLoaded) seekTo(timeInMillis.toInt()) }
    }

    fun release() {
        stop(release = false)
        stopListeningForProgress()

        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadAudio(url: String) {
        mediaPlayer?.apply {
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

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener {
                // Start audio once loaded
                start()

                // Notify callback
                onLoaded?.invoke(duration.toLong())

                // Update flags
                isLoaded = true
            }

            setOnCompletionListener {
                // Notify callback
                onCompleted?.invoke()

                // Update flags
                isLoaded = false

                // Release
                this@AudioPlayer.release()
            }
        }
    }

    private fun initProgressCallback() {
        progressCallbackHandler = Handler()
        progressCallback = Runnable {
            val mediaPlayer = this.mediaPlayer
            if (mediaPlayer != null) {
                val progress = mediaPlayer.currentPosition.toLong()
                if (progress != currentProgress) {
                    onProgressChanged?.invoke(progress)
                    currentProgress = progress
                }

                progressCallbackHandler?.postDelayed(progressCallback, TimeUnit.SECONDS.toMillis(1))
            }
        }
    }

    private fun startListeningForProgress() {
        // Try to clear any existing listeners
        stopListeningForProgress()

        // Setup progress callback
        initProgressCallback()
        progressCallbackHandler?.postDelayed(progressCallback, TimeUnit.SECONDS.toMillis(1))
    }

    private fun stopListeningForProgress() {
        progressCallbackHandler?.removeCallbacks(progressCallback)
        progressCallbackHandler = null
        progressCallback = null
    }
}