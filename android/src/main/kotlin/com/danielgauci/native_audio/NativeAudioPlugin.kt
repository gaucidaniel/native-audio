package com.danielgauci.native_audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.view.FlutterNativeView

class NativeAudioPlugin(
        private val context: Context,
        private val channel: MethodChannel
) : MethodCallHandler {

    companion object {

        private const val CHANNEL = "com.danielgauci.native_audio"

        private const val NATIVE_METHOD_PLAY = "play"
        private const val NATIVE_METHOD_PLAY_ARG_URL = "url"
        private const val NATIVE_METHOD_PLAY_ARG_TITLE = "title"
        private const val NATIVE_METHOD_PLAY_ARG_ARTIST = "artist"
        private const val NATIVE_METHOD_PLAY_ARG_ALBUM = "album"
        private const val NATIVE_METHOD_PLAY_ARG_IMAGE_URL = "imageUrl"
        private const val NATIVE_METHOD_PLAY_START_AUTOMATICALLY = "startAutomatically"
        private const val NATIVE_METHOD_PLAY_START_FROM_MILLIS = "startFromMillis"
        private const val NATIVE_METHOD_RESUME = "resume"
        private const val NATIVE_METHOD_PAUSE = "pause"
        private const val NATIVE_METHOD_STOP = "stop"
        private const val NATIVE_METHOD_SKIP_FORWARD = "skipForward"
        private const val NATIVE_METHOD_SKIP_BACKWARD = "skipBackward"
        private const val NATIVE_METHOD_SEEK_TO = "seekTo"
        private const val NATIVE_METHOD_SEEK_TO_ARG_TIME = "timeInMillis"
        private const val NATIVE_METHOD_RELEASE = "release"
        private const val NATIVE_METHOD_SET_SKIP_TIME = "setSkipTime"
        private const val NATIVE_METHOD_SET_SKIP_TIME_ARG_FORWARD_MILLIS = "forwardMillis"
        private const val NATIVE_METHOD_SET_SKIP_TIME_ARG_BACKWARD_MILLIS = "backwardMillis"
        private const val FLUTTER_METHOD_ON_LOADED = "onLoaded"
        private const val FLUTTER_METHOD_ON_LOADED_ARG_STARTED_AUTOMATICALLY = "startedAutomatically"
        private const val FLUTTER_METHOD_ON_LOADED_ARG_TOTAL_DURATION_IN_MILLIS = "totalDurationInMillis"
        private const val FLUTTER_METHOD_ON_PROGRESS_CHANGED = "onProgressChanged"
        private const val FLUTTER_METHOD_ON_RESUMED = "onResumed"
        private const val FLUTTER_METHOD_ON_PAUSED = "onPaused"
        private const val FLUTTER_METHOD_ON_STOPPED = "onStopped"
        private const val FLUTTER_METHOD_ON_COMPLETED = "onCompleted"

        private var pluginRegistrantCallback: PluginRegistry.PluginRegistrantCallback? = null

        @JvmStatic
        fun setPluginRegistrantCallback(callback: PluginRegistry.PluginRegistrantCallback) {
            pluginRegistrantCallback = callback
        }

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), CHANNEL)
            channel.setMethodCallHandler(NativeAudioPlugin(registrar.context(), channel))
        }
    }

    private var flutterView: FlutterNativeView? = null
    private var audioService: AudioService? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onMethodCall(call: MethodCall, result: Result) {
        withService { service ->
            if (flutterView == null) {
                // Register all plugins for the application with our new FlutterNativeView's
                // plugin registry.
                // Other plugins will not work when running in the background if this isn't done
                flutterView = FlutterNativeView(service, false).apply {
                    pluginRegistrantCallback?.registerWith(pluginRegistry)
                            ?: throw IllegalStateException("No pluginRegistrantCallback has been set. Make sure you call NativeAudioPlugin.setPluginRegistrantCallback(this) in your application's onCreate.")
                }
            }

            when (call.method) {
                NATIVE_METHOD_PLAY -> {
                    withArgument(call, NATIVE_METHOD_PLAY_ARG_URL) { url: String ->
                        // Get optional arguments
                        val title = call.argument<String>(NATIVE_METHOD_PLAY_ARG_TITLE)
                        val artist = call.argument<String>(NATIVE_METHOD_PLAY_ARG_ARTIST)
                        val album = call.argument<String>(NATIVE_METHOD_PLAY_ARG_ALBUM)
                        val imageUrl = call.argument<String>(NATIVE_METHOD_PLAY_ARG_IMAGE_URL)
                        val startAutomatically = call.argument<Boolean>(NATIVE_METHOD_PLAY_START_AUTOMATICALLY)
                        val startFromMillis = call.argument<Int>(NATIVE_METHOD_PLAY_START_FROM_MILLIS)

                        // Call service
                        withService {
                            it.play(
                                    url = url,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    imageUrl = imageUrl,
                                    startAutomatically = startAutomatically ?: true,
                                    startFromMillis = startFromMillis?.toLong() ?: 0L
                            )
                        }
                    }
                }
                NATIVE_METHOD_RESUME -> service.resume()
                NATIVE_METHOD_PAUSE -> service.pause()
                NATIVE_METHOD_STOP -> service.stop()
                NATIVE_METHOD_RELEASE -> releaseAudioService()
                NATIVE_METHOD_SEEK_TO -> {
                    withArgument(call, NATIVE_METHOD_SEEK_TO_ARG_TIME) { timeInMillis: Long ->
                        service.seekTo(timeInMillis)
                    }
                }
                NATIVE_METHOD_SKIP_FORWARD -> service.skipForward()
                NATIVE_METHOD_SKIP_BACKWARD -> service.skipBackward()
                NATIVE_METHOD_SET_SKIP_TIME -> {
                    withArgument(call, NATIVE_METHOD_SET_SKIP_TIME_ARG_FORWARD_MILLIS) { forward: Long ->
                        withArgument(call, NATIVE_METHOD_SET_SKIP_TIME_ARG_BACKWARD_MILLIS) { rewind: Long ->
                            AudioService.SKIP_FORWARD_TIME_MILLIS = forward
                            AudioService.SKIP_BACKWARD_TIME_MILLIS = rewind
                        }
                    }
                }
            }
        }
    }

    private fun <T> withArgument(methodCall: MethodCall, argumentKey: String, withArgument: (T) -> Unit) {
        val argument = methodCall.argument<T>(argumentKey)
                ?: throw IllegalArgumentException("Argument $argumentKey is required when calling the ${methodCall.method} method.")

        withArgument(argument)
    }

    private fun withService(withService: (AudioService) -> Unit) {
        if (audioService == null) {
            // Audio service not available yes, bind and setup
            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = (binder as AudioService.AudioServiceBinder).getService()
                    bindAudioServiceWithChannel(service)
                    withService(service)

                    audioService = service
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    audioService = null
                }
            }

            val serviceIntent = Intent(context, AudioService::class.java)
            if (!context.isServiceRunning(AudioService::class.java)) context.startService(serviceIntent)
            context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

            // Return and wait for service to be connected
            return
        }

        // Call lambda with service
        withService(audioService!!)
    }

    private fun bindAudioServiceWithChannel(service: AudioService) {
        service.apply {
            // Notify flutter with audio updates
            onLoaded = { totalDurationInMillis, startedAutomatically ->
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_LOADED, mapOf(
                            FLUTTER_METHOD_ON_LOADED_ARG_TOTAL_DURATION_IN_MILLIS to totalDurationInMillis,
                            FLUTTER_METHOD_ON_LOADED_ARG_STARTED_AUTOMATICALLY to startedAutomatically
                    ))
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, e.message, e)
                }
            }

            onProgressChanged = {
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_PROGRESS_CHANGED, it)
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, e.message, e)
                }
            }

            onResumed = {
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_RESUMED, null)
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, e.message, e)
                }
            }

            onPaused = {
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_PAUSED, null)
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, e.message, e)
                }
            }

            onStopped = {
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_STOPPED, null)
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, e.message, e)
                }
            }

            onCompleted = {
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_COMPLETED, null)
                } catch (e: Exception) {
                    Log.e(this::class.java.simpleName, e.message, e)
                }
            }
        }
    }

    private fun releaseAudioService() {
        serviceConnection?.let { context.unbindService(it) }
        context.stopService(Intent(context, AudioService::class.java))
    }
}