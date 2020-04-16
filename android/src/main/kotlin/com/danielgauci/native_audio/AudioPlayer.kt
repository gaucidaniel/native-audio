package com.danielgauci.native_audio

import android.media.MediaPlayer
import android.os.Handler
import android.util.Log
import java.util.concurrent.TimeUnit

class AudioPlayer(
    private val onLoaded: ((duration: Long) -> Unit)? = null,
    private val onProgressChanged: ((currentTime: Long) -> Unit)? = null,
    private val onCompleted: (() -> Unit)? = null,
    private val onBufferStart: (() -> Unit)? = null,
    private val onBufferEnd: (() -> Unit)? = null,
    private val onBufferingUpdate: ((Int) -> Unit)? = null,
    private val onDuration: ((Int) -> Unit)? = null,
    private val onError: ((String) -> Unit)? = null
) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressCallbackHandler: Handler? = null
    private var progressCallback: Runnable? = null
    private var currentProgress = 0L
    private var isLoaded = false
    private var isLooping = false

    fun play(url: String, looping: Boolean?) {
        isLooping = looping ?: false;
        if (mediaPlayer == null) initMediaPlayer()
        Log.d(this::class.java.simpleName, "initMediaPlayer")
        if (mediaPlayer?.isPlaying == true) stop()
        Log.d(this::class.java.simpleName, "stop")
        loadAudio(url)
        Log.d(this::class.java.simpleName, "loadAudio")
        startListeningForProgress()
    }

    fun getDuration() {
        mediaPlayer?.apply { if (isLoaded)
        onDuration?.invoke(getDuration())
    }
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
        mediaPlayer?.apply {   if (isLoaded) seekTo(timeInMillis.toInt()) }
    }

    fun release() {
        stop(release = false)
        stopListeningForProgress()

        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadAudio(url: String) {
        mediaPlayer?.apply {
            setOnInfoListener { mp, what, extra ->
                Log.d(this::class.java.simpleName, "Oninfo -  code: $what Extra code: $extra")
                when (what) {
                  MediaPlayer.MEDIA_INFO_BUFFERING_START -> onBufferStart?.invoke()
                  MediaPlayer.MEDIA_INFO_BUFFERING_END -> onBufferEnd?.invoke()
                }
                 false
               }
            setOnErrorListener { mp, what, extra ->
                when (what) {
                    MediaPlayer.MEDIA_ERROR_UNKNOWN -> onError?.invoke("MEDIA_ERROR_UNKNOWN")
                    MediaPlayer.MEDIA_ERROR_SERVER_DIED -> onError?.invoke("MEDIA_ERROR_SERVER_DIED")
                }
                when (extra) {
                    MediaPlayer.MEDIA_ERROR_IO -> onError?.invoke("MEDIA_ERROR_IO")
                    // / Media server died. In this case, the application must release 
                    // the MediaPlayer object and instantiate a new one.
                    MediaPlayer.MEDIA_ERROR_SERVER_DIED -> onError?.invoke("MEDIA_ERROR_SERVER_DIED")
                    MediaPlayer.MEDIA_ERROR_TIMED_OUT -> onError?.invoke("MEDIA_ERROR_TIMED_OUT")
                    MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> onError?.invoke("MEDIA_ERROR_UNSUPPORTED")
                }
                Log.d(this::class.java.simpleName, "OnError - Error code: $what Extra code: $extra")

                // Return false to trigger on complete
                false
            }

            setOnBufferingUpdateListener { mp, what ->
                onBufferingUpdate?.invoke(what)
            }
            

            reset()
            setDataSource(url)
            prepareAsync()
            // setLooping(isLooping)
        }
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener {
                // Start audio once loaded
                start()
                

                // Notify callback
                onLoaded?.invoke(duration.toLong())
                Log.d(this::class.java.simpleName, "OnPreparedListener -  code: ${it.duration} Extra ")

                // Update flags
                isLoaded = true
               setOnInfoListener { mp, what, extra ->
                Log.d(this::class.java.simpleName, "Oninfo -  code: $what Extra code: $extra")
                when (what) {
                  MediaPlayer.MEDIA_INFO_BUFFERING_START -> onBufferStart?.invoke()
                  MediaPlayer.MEDIA_INFO_BUFFERING_END -> onBufferEnd?.invoke()
                }
                 false
               }
            }
            setOnInfoListener { mp, what, extra ->
                Log.d(this::class.java.simpleName, "Oninfo -  code: $what Extra code: $extra")
                when (what) {
                  MediaPlayer.MEDIA_INFO_BUFFERING_START -> onBufferStart?.invoke()
                  MediaPlayer.MEDIA_INFO_BUFFERING_END -> onBufferEnd?.invoke()
                }
                 false
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
