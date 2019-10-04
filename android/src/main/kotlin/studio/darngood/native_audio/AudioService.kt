package studio.darngood.native_audio

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.ColorInt
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import java.util.concurrent.TimeUnit

class AudioService : Service() {

    companion object {
        private const val MEDIA_SESSION_TAG = "studio.darngood.soundbite"

        private const val NOTIFICATION_ID = 10

        private const val NOTIFICATION_CHANNEL_ID = "media_playback_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Media Playback"
        private const val NOTIFICATION_CHANNEL_DESCRIPTION = "Media Playback Controls"
    }

    // TODO: Confirm that this does not leak the activity
    var onLoaded: ((Long) -> Unit)? = null
    var onProgressChanged: ((Long) -> Unit)? = null
    var onResumed: (() -> Unit)? = null
    var onPaused: (() -> Unit)? = null
    var onStopped: (() -> Unit)? = null
    var onCompleted: (() -> Unit)? = null

    private var currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
    private var currentPositionInMillis = 0L
    private var durationInMillis = 0L
    private var isNotificationShown = false
    private var notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

    private val binder by lazy { AudioServiceBinder() }
    private val session by lazy {
        MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
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

                    val metadata = MediaMetadataCompat.Builder()
                            .putLong(METADATA_KEY_DURATION, durationInMillis)
                            .build()

                    session.setMetadata(metadata)
                },
                onProgressChanged = {
                    currentPositionInMillis = it
                    onProgressChanged?.invoke(it)
                    updatePlaybackState()
                },
                onCompleted = { onCompleted?.invoke() }
        )
    }

    private val playbackStateBuilder by lazy {
        PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or
                        PlaybackStateCompat.ACTION_REWIND
        )
    }


    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    fun play(
            url: String,
            title: String? = null,
            artist: String? = null,
            album: String? = null,
            imageUrl: String? = null
    ) {
        audioPlayer.play(url)

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
        audioPlayer.resume()

        currentPlaybackState = PlaybackStateCompat.STATE_PLAYING
        updatePlaybackState()

        onResumed?.invoke()
    }

    fun pause() {
        audioPlayer.pause()

        currentPlaybackState = PlaybackStateCompat.STATE_PAUSED
        updatePlaybackState()

        onPaused?.invoke()
    }

    fun stop() {
        audioPlayer.stop()

        currentPlaybackState = PlaybackStateCompat.STATE_STOPPED
        updatePlaybackState()

        cancelNotification()
        session.isActive = false

        onStopped?.invoke()
    }

    fun seekTo(timeInMillis: Long) {
        audioPlayer.seekTo(timeInMillis)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel()
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setCancelButtonIntent(stopIntent)
                .setShowCancelButton(true)

        notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSmallIcon(R.drawable.play)
                .setContentIntent(contentIntent)
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
            val isPlaying = playbackState.state == PlaybackStateCompat.STATE_PLAYING

            // Update buttons based on current state
            setNotificationButtons(notificationBuilder, isPlaying)

            // Allow notification to be dismissed if not playing
            notificationBuilder.setOngoing(isPlaying)

            if (isPlaying) {
                // Update notification with the updated builder
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            } else {
                // Allow notification to be dismissed if not playing by changing the service to a non-foreground service
                stopForeground(false)
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
            }
        }
    }

    inner class AudioServiceBinder : Binder() {

        fun getService() = this@AudioService
    }
}