package com.danielgauci.native_audio

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.ColorInt
import androidx.core.app.NotificationCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.session.MediaButtonReceiver
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import java.util.concurrent.TimeUnit


class AudioService : Service() {

    companion object {
        private const val MEDIA_SESSION_TAG = "com.danielgauci.native_audio"

        private const val NOTIFICATION_ID = 10

        private const val NOTIFICATION_CHANNEL_ID = "media_playback_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Media Playback"
        private const val NOTIFICATION_CHANNEL_DESCRIPTION = "Media Playback Controls"

        // private  fun createMediaMetadata( title : String,
        // album : String, artist : String,  imageUrl : String): MediaMetadataCompat {
		// return MediaMetadataCompat.fromMediaMetadata({
        //     "title": title,
        //     "artist": artist,
        //     "album": album,
        //     "imageUrl": imageUrl,
        // });
	// }
    }

    // TODO: Confirm that this does not leak the activity
    var onLoaded: ((Long) -> Unit)? = null
    var onProgressChanged: ((Long) -> Unit)? = null
    var onResumed: (() -> Unit)? = null
    var onPaused: (() -> Unit)? = null
    var onStopped: (() -> Unit)? = null
    var onCompleted: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onBufferEnd: (() -> Unit)? = null
    var onBufferingUpdate: ((Int) -> Unit)? = null
    var onDuration: ((Int) -> Unit)? = null
    var onBufferStart: (() -> Unit)? = null

    private var currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
    private var oldPlaybackState: Int = Int.MIN_VALUE
    private var currentPositionInMillis = 0L
    private var durationInMillis = 0L
    private var resumeOnAudioFocus = false
    private var isNotificationShown = false
    private var notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
    private var metadata = MediaMetadataCompat.fromMediaMetadata(metadataObj)Builder()
    // private var audioList : List<AudioModel>;

    private val binder by lazy { AudioServiceBinder() }
    private val session by lazy {
        MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    resume()
                }

                override fun onPause() {
                    super.onPause()
                    pause()
                }

                override fun onStop() {
                    super.onStop()
                    stop()
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    seekTo(pos)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    forward30()
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    rewind30()
                }

                override fun onFastForward() {
                    super.onFastForward()
                    forward30()
                }

                override fun onRewind() {
                    super.onRewind()
                    rewind30()
                }
            })
        }
    }

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val audioPlayer by lazy {
        AudioPlayer(
                onLoaded = {
                    durationInMillis = it
                    onLoaded?.invoke(it)

                    metadata.putLong(METADATA_KEY_DURATION, durationInMillis)
                    session.setMetadata(metadata.build())
                },
                onProgressChanged = {
                    currentPositionInMillis = it
                    onProgressChanged?.invoke(it)
                    updatePlaybackState()
                },
                onCompleted = { onCompleted?.invoke() },
                onError = { onError?.invoke(it) },
                onBufferStart = { onBufferStart?.invoke() },
                onBufferEnd = { onBufferEnd?.invoke() },
                onBufferingUpdate = { onBufferingUpdate?.invoke(it) },
                onDuration = { onDuration?.invoke(it) }
        )
    }

    private val playbackStateBuilder by lazy {
        PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
        )
    }

    private val audioFocusRequest by lazy {
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { audioFocus ->
                    when (audioFocus) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (resumeOnAudioFocus && !isPlaying()) {
                                resume()
                                resumeOnAudioFocus = false
                            } else if (isPlaying()) {
                                // TODO: Set volume to full
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // TODO: Set volume to duck
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            if (isPlaying()) {
                                resumeOnAudioFocus = true
                                pause()
                            }
                        }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            resumeOnAudioFocus = false
                            stop()
                        }
                    }
                }
                .build()
    }

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val headsetManager by lazy { HeadsetManager() }
    private val bluetoothManager by lazy { BluetoothManager() }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)

        headsetManager.registerHeadsetPlugReceiver(
                this,
                onConnected = {},
                onDisconnected = { pause() })

        bluetoothManager.registerBluetoothReceiver(
                this,
                onConnected = {},
                onDisconnected = { pause() })

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.release()
    }

//     fun setQueue(items: List<Map<String, var>>) {
//      var q : List<MediaMetadataCompat> =  emptyList();
//     for (i in items) {
//         q.p
//     }
// }

    fun play(
            url: String,
            title: String? = null,
            artist: String? = null,
            album: String? = null,
            imageUrl: String? = null,
            isLooping: Boolean? = null
    ) {
        requestFocus()

        audioPlayer.play(url, isLooping)

        session.isActive = true
        currentPlaybackState = PlaybackStateCompat.STATE_PLAYING
        updatePlaybackState()

        showNotification(
                title = title ?: "",
                artist = artist ?: "",
                album = album ?: "",
                imageUrl = imageUrl ?: ""
        )
    }

    fun resume() {
        requestFocus()

        audioPlayer.resume()

        currentPlaybackState = PlaybackStateCompat.STATE_PLAYING
        updatePlaybackState()

        onResumed?.invoke()
    }

    fun getDuration() {
        audioPlayer.getDuration();
    }

    fun pause() {
        audioPlayer.pause()

        currentPlaybackState = PlaybackStateCompat.STATE_PAUSED
        updatePlaybackState()

        onPaused?.invoke()

        if (!resumeOnAudioFocus) abandonFocus()
    }

    fun stop() {
        audioPlayer.stop()

        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED

        cancelNotification()
        session.isActive = false

        onStopped?.invoke()

        abandonFocus()

        stopSelf()
    }

    fun seekTo(timeInMillis: Long) {
        audioPlayer.seekTo(timeInMillis)
        resume() 
    }

    private fun forward30() {
        val forwardTime = TimeUnit.SECONDS.toMillis(30)
        if (durationInMillis - currentPositionInMillis > forwardTime) {
            seekTo(currentPositionInMillis + forwardTime.toInt())
        }
    }

    private fun rewind30() {
        val rewindTime = TimeUnit.SECONDS.toMillis(30)
        if (currentPositionInMillis - rewindTime > 0) {
            seekTo(currentPositionInMillis - rewindTime.toInt())
        }
    }

    private fun requestFocus() {
        AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
    }

    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }

    @TargetApi(26)
    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = NOTIFICATION_CHANNEL_DESCRIPTION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        })
    }

    private fun updateNotificationBuilder(
            title: String,
            artist: String,
            album: String,
            @ColorInt notificationColor: Int? = null,
            image: Bitmap? = null
    ) {
        metadata.putString(METADATA_KEY_TITLE, title)
                .putString(METADATA_KEY_ARTIST, artist)
                .putBitmap(METADATA_KEY_ALBUM_ART, image)

        session.setMetadata(metadata.build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setCancelButtonIntent(stopIntent)
                .setShowCancelButton(true)

        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSmallIcon(R.drawable.play)
                .setContentIntent(contentIntent)
                .setDeleteIntent(stopIntent)
                .setContentTitle(title)
                .setContentText(album)
                .setSubText(artist)

        notificationBuilder.apply {
            if (image != null) setLargeIcon(image)
            if (notificationColor != null) color = notificationColor

            // Add play/pause action
            setNotificationButtons(this)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setNotificationButtons(builder: NotificationCompat.Builder, isPlaying: Boolean = true) {
        builder.apply {
            mActions.clear()

            // Add play/pause action
            val playPauseAction = NotificationCompat.Action.Builder(
                    if (isPlaying) R.drawable.pause else R.drawable.play,
                    if (isPlaying) "Pause" else "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this@AudioService, PlaybackStateCompat.ACTION_PLAY_PAUSE)
            ).build()
            addAction(playPauseAction)

            // Add rewind action
            val rewindAction = NotificationCompat.Action.Builder(
                    R.drawable.rewind_30,
                    "Rewind",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this@AudioService, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            ).build()
            addAction(rewindAction)

            // Add fast forward action
            val forwardAction = NotificationCompat.Action.Builder(
                    R.drawable.fast_forward_30,
                    "Fast Forward",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this@AudioService, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            ).build()
            addAction(forwardAction)
             // Add fast forward action
             val stopAction = NotificationCompat.Action.Builder(
                R.drawable.stop,
                "Fast Forward",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this@AudioService, PlaybackStateCompat.ACTION_STOP)
        ).build()
        addAction(stopAction)
        }
    }

    private fun showNotification(title: String, artist: String, album: String, imageUrl: String? = null) {
        if (imageUrl.isNullOrBlank()) {
            // No image is set, show notification
            updateNotificationBuilder(title, artist, album)
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
            isNotificationShown = true
        } else {
            // Get image show notification
            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            Palette.from(resource).generate { palette ->
                                palette?.let {
                                    // Palette generated, show notification with bitmap and palette
                                    val color = it.getVibrantColor(Color.WHITE)
                                    updateNotificationBuilder(
                                            title = title,
                                            artist = artist,
                                            album = album,
                                            notificationColor = color,
                                            image = resource
                                    )

                                    startForeground(NOTIFICATION_ID, notificationBuilder.build())
                                    isNotificationShown = true
                                } ?: run {
                                    // Failed to generate palette, show notification with bitmap
                                    updateNotificationBuilder(
                                            title = title,
                                            artist = artist,
                                            album = album,
                                            image = resource
                                    )

                                    startForeground(NOTIFICATION_ID, notificationBuilder.build())
                                    isNotificationShown = true
                                }
                            }
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            super.onLoadFailed(errorDrawable)

                            // Failed to load image, show notification
                            updateNotificationBuilder(title, artist, album)
                            startForeground(NOTIFICATION_ID, notificationBuilder.build())
                            isNotificationShown = true
                        }
                    })
        }
    }

    private fun cancelNotification() {
        stopForeground(true)
        notificationManager.cancel(NOTIFICATION_ID)
        isNotificationShown = false
    }

    private fun updatePlaybackState() {
        val playbackState = playbackStateBuilder
                .setState(currentPlaybackState, currentPositionInMillis, 0f)
                .build()

        // Update session
        session.setPlaybackState(playbackState)

        // Try to update notification
        if (isNotificationShown) {
            val stateChanged = currentPlaybackState != oldPlaybackState

            // Update buttons based on current state
            setNotificationButtons(notificationBuilder, isPlaying())

            // Allow notification to be dismissed if not playing
            notificationBuilder.setOngoing(isPlaying())

            if (isPlaying() && stateChanged) {
                // Update notification and ensure that notification is in foreground as it could have been stopped before
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            } else if (isPlaying()) {
                // Notification was already in foreground, update with the latest information
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            } else {
                // Allow notification to be dismissed if not playing by changing the service to a non-foreground service
                stopForeground(false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
        }

        // Update playback state
        oldPlaybackState = currentPlaybackState
    }

    private fun isPlaying() = currentPlaybackState == PlaybackStateCompat.STATE_PLAYING

    inner class AudioServiceBinder : Binder() {

        fun getService() = this@AudioService
    }
}