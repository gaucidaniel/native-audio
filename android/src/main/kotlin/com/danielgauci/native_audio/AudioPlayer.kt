package com.danielgauci.native_audio

import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.util.concurrent.TimeUnit

class AudioPlayer(
        private val onLoaded: ((duration: Long) -> Unit)? = null,
        private val onResumed: (() -> Unit)? = null,
        private var onPaused: (() -> Unit)? = null,
        private var onStopped: (() -> Unit)? = null,
        private val onProgressChanged: ((currentTime: Long) -> Unit)? = null,
        private val onCompleted: (() -> Unit)? = null
) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressCallbackHandler: Handler? = null
    private var progressCallback: Runnable? = null
    private var currentProgress = 0L
    private var isLoaded = false

    fun play(url: String, startWhenPrepared: Boolean = true, startFromMillis: Long = 0L) {
        if (mediaPlayer == null) mediaPlayer = MediaPlayer()

        mediaPlayer?.apply {
            // Stop audio if already playing
            if (isPlaying) stop()

            // Set listeners
            setOnPreparedListener {
                // Update flags
                isLoaded = true

                // Notify callback
                onLoaded?.invoke(duration.toLong())

                // Seek if start time is not 0
                if (startFromMillis > 0) seekTo(startFromMillis)

                // Start audio once loaded
                if (startWhenPrepared) resume()
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

        // Load audio
        loadAudio(url)
    }

    fun resume() {
        mediaPlayer?.apply {
            if (isLoaded && !isPlaying) {
                start()
                onResumed?.invoke()
            }
        }

        startListeningForProgress()
    }

    fun pause() {
        mediaPlayer?.apply {
            if (isPlaying) {
                pause()
                onPaused?.invoke()
            }
        }

        stopListeningForProgress()
    }

    fun stop(release: Boolean = true) {
        mediaPlayer?.apply {
            this.stop()
            this.reset()
            onStopped?.invoke()
        }

        if (release) release()
    }

    fun seekTo(timeInMillis: Long) {
        mediaPlayer?.apply {
            if (isLoaded) {
                // Manually notify onProgressChanged since it is not called automatically if player is paused
                onProgressChanged?.invoke(timeInMillis)

                seekTo(timeInMillis.toInt())
            }
        }
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