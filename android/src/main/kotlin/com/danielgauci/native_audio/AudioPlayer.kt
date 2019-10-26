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

        loadAudio(url) { resume() }
    }

    fun resume() {
        mediaPlayer?.apply { if (isLoaded && !isPlaying) start() }
    }

    fun pause() {
        mediaPlayer?.apply { if (isPlaying) pause() }
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

        progressCallbackHandler?.removeCallbacks(progressCallback)
        progressCallbackHandler = null
        progressCallback = null

        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadAudio(url: String, onAudioLoaded: () -> Unit) {
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

                // Setup progress callback
                initProgressCallback()
                progressCallbackHandler?.postDelayed(progressCallback, TimeUnit.SECONDS.toMillis(1))

                // Update flags
                isLoaded = true
            }

            setOnCompletionListener {
                // Notify callback
                onCompleted?.invoke()

                // Clear progress callback
                progressCallbackHandler?.removeCallbacks(progressCallback)

                // Update flags
                isLoaded = false
            }
        }
    }

    private fun initProgressCallback() {
        progressCallbackHandler = Handler()
        progressCallback = Runnable {
            val mediaPlayer = this.mediaPlayer
            if (mediaPlayer != null && mediaPlayer.isPlaying) {
                val progress = mediaPlayer.currentPosition.toLong()
                if (progress != currentProgress) {
                    onProgressChanged?.invoke(progress)
                    currentProgress = progress
                }

                progressCallbackHandler?.postDelayed(progressCallback, TimeUnit.SECONDS.toMillis(1))
            }
        }
    }
}