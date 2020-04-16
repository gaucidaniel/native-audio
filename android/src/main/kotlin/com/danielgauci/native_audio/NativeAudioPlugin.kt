package com.danielgauci.native_audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.view.FlutterNativeView

class NativeAudioPlugin : MethodCallHandler, FlutterPlugin {

    companion object {

        private const val CHANNEL = "com.danielgauci.native_audio"

        private const val NATIVE_METHOD_PLAY = "play"
        private const val NATIVE_METHOD_SET_QUEUE = "setQueue"
        private const val NATIVE_METHOD_PLAY_ARG_URL = "url"
        private const val NATIVE_METHOD_PLAY_ARG_TITLE = "title"
        private const val NATIVE_METHOD_PLAY_ARG_ARTIST = "artist"
        private const val NATIVE_METHOD_PLAY_ARG_ALBUM = "album"
        private const val NATIVE_METHOD_PLAY_ARG_IMAGE_URL = "imageUrl"
        private const val NATIVE_METHOD_RESUME = "resume"
        private const val NATIVE_METHOD_PAUSE = "pause"
        private const val NATIVE_METHOD_STOP = "stop"
        private const val NATIVE_METHOD_SEEK_TO = "seekTo"
        private const val NATIVE_METHOD_SEEK_TO_ARG_TIME = "timeInMillis"
        private const val NATIVE_METHOD_RELEASE = "release"
        private const val NATIVE_METHOD_SERVICE_STATUS = "serviceStatus"
        private const val NATIVE_METHOD_GET_DURATION = "duration"
        private const val NATIVE_METHOD_PLAY_ARG_LOOPING = "looping"

        private const val FLUTTER_METHOD_ON_LOADED = "onLoaded"
        private const val FLUTTER_METHOD_ON_PROGRESS_CHANGED = "onProgressChanged"
        private const val FLUTTER_METHOD_ON_RESUMED = "onResumed"
        private const val FLUTTER_METHOD_ON_PAUSED = "onPaused"
        private const val FLUTTER_METHOD_ON_STOPPED = "onStopped"
        private const val FLUTTER_METHOD_ON_COMPLETED = "onCompleted"
        private const val FLUTTER_METHOD_ON_ERROR = "onError"
        private const val FLUTTER_METHOD_ON_SERVICE_STATUS = "serviceStatus"
        private const val FLUTTER_METHOD_ON_BUFFERING_START = "bufferingStart"
        private const val FLUTTER_METHOD_ON_BUFFERING_END = "bufferingEnd"
        private const val FLUTTER_METHOD_ON_BUFFERING_UPDATTE = "bufferingUpdate"
        private const val FLUTTER_METHOD_ON_DURATION = "duration"

        private var pluginRegistrantCallback: PluginRegistry.PluginRegistrantCallback? = null

        @JvmStatic
        fun setPluginRegistrantCallback(callback: PluginRegistry.PluginRegistrantCallback) {
            pluginRegistrantCallback = callback
        }

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val instance = NativeAudioPlugin()
            instance.atachedToEngine(registrar.context(), registrar.messenger())
        }
    }
    private var context: Context? = null
    private var channel: MethodChannel? = null
    private var flutterView: FlutterNativeView? = null
    private var audioService: AudioService? = null
    private var serviceConnection: ServiceConnection? = null

    override fun onMethodCall(call: MethodCall, result: Result) {

        if (call.method == NATIVE_METHOD_SERVICE_STATUS) {
        val isRunning = context?.isServiceRunning(AudioService::class.java) == true
        channel?.invokeMethod(FLUTTER_METHOD_ON_SERVICE_STATUS, isRunning)
        if (!isRunning) return
        withService { service ->
            service.resume()
        }
    }
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
                        val isLooping = call.argument<Boolean>(NATIVE_METHOD_PLAY_ARG_LOOPING)
                        NativeAudioPlugin
                        // Call service
                        withService {
                            it.play(url, title, artist, album, imageUrl, isLooping)
                        }
                    }
                }
                NATIVE_METHOD_RESUME -> service.resume()
                // NATIVE_METHOD_SET_QUEUE -> service.resume()
                NATIVE_METHOD_PAUSE -> service.pause()
                NATIVE_METHOD_STOP -> service.stop()
                NATIVE_METHOD_GET_DURATION -> service.getDuration()
                NATIVE_METHOD_RELEASE -> releaseAudioService()
                NATIVE_METHOD_SEEK_TO -> {
                    withArgument(call, NATIVE_METHOD_SEEK_TO_ARG_TIME) { timeInMillis: Int ->
                        service.seekTo(timeInMillis.toLong())
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
            if (context?.isServiceRunning(AudioService::class.java) != true)
            context?.startService(serviceIntent)

            context?.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            // Return and wait for service to be connected
            return
        }

        // Call lambda with service
        withService(audioService!!)
    }

    private fun bindAudioServiceWithChannel(service: AudioService) {
        service.apply {
            // Notify flutter with audio updates
            onLoaded = {
                invokeMethod(FLUTTER_METHOD_ON_LOADED, it)
            }

            onProgressChanged = {
                invokeMethod(FLUTTER_METHOD_ON_PROGRESS_CHANGED, it)
            }

            onResumed = {
                invokeMethod(FLUTTER_METHOD_ON_RESUMED, null)
            }

            onPaused = {
                invokeMethod(FLUTTER_METHOD_ON_PAUSED, null)
            }

            onStopped = {
                invokeMethod(FLUTTER_METHOD_ON_STOPPED, null)
            }

            onCompleted = {
                invokeMethod(FLUTTER_METHOD_ON_COMPLETED, null)
            }
            onError = {
                invokeMethod(FLUTTER_METHOD_ON_ERROR, it)
            }
            onBufferStart = {
                invokeMethod(FLUTTER_METHOD_ON_BUFFERING_START, null)
            }
            onBufferEnd = {
                invokeMethod(FLUTTER_METHOD_ON_BUFFERING_END, null)
            }
            onBufferingUpdate = {
                invokeMethod(FLUTTER_METHOD_ON_BUFFERING_UPDATTE, it)
            }
            onDuration = {
                invokeMethod(FLUTTER_METHOD_ON_DURATION, it)
            }
        }
    }

    private fun invokeMethod(method: String, args: Any?) {
        try {
            channel?.invokeMethod(method, args)
        } catch (e: Exception) {
            Log.e(this::class.java.simpleName, e.message, e)
        }
        }

    private fun atachedToEngine(appContext: Context, messenger: BinaryMessenger) {
        context = appContext
        channel = MethodChannel(messenger, CHANNEL)
        channel?.setMethodCallHandler(this)
        }

    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        atachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger())
        }

  override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
      channel?.setMethodCallHandler(null)
      context = null
      channel = null
      serviceConnection?.let { context?.unbindService(it) }
    }

    private fun releaseAudioService() {
        // serviceConnection?.let { context?.unbindService(it) }
        context?.stopService(Intent(context, AudioService::class.java))
    }
}
