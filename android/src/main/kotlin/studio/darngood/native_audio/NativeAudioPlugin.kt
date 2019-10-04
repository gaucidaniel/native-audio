package studio.darngood.native_audio

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
import studio.darngood.soundbite.isServiceRunning

class NativeAudioPlugin(
        private val context: Context,
        private val channel: MethodChannel
) : MethodCallHandler {

    companion object {

        private const val CHANNEL = "studio.darngood.soundbite/native_audio"

        private const val NATIVE_METHOD_PLAY = "play"
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

        private const val FLUTTER_METHOD_ON_LOADED = "onLoaded"
        private const val FLUTTER_METHOD_ON_LOADED_ARG_DURATION = "duration"
        private const val FLUTTER_METHOD_ON_PROGRESS_CHANGED = "onProgressChanged"
        private const val FLUTTER_METHOD_ON_PROGRESS_CHANGED_ARG_CURRENT_TIME = "currentTime"
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

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (flutterView == null) {
            // Register all plugins for the application with our new FlutterNativeView's
            // plugin registry.
            // Other plugins will not work when running in the background if this isn't done
            flutterView = FlutterNativeView(context, true)
            pluginRegistrantCallback?.registerWith(flutterView!!.pluginRegistry)
        }

        when (call.method) {
            NATIVE_METHOD_PLAY -> {
                withArgument(call, NATIVE_METHOD_PLAY_ARG_URL) { url: String ->
                    // Get optional arguments
                    val title = call.argument<String>(NATIVE_METHOD_PLAY_ARG_TITLE)
                    val artist = call.argument<String>(NATIVE_METHOD_PLAY_ARG_ARTIST)
                    val album = call.argument<String>(NATIVE_METHOD_PLAY_ARG_ALBUM)
                    val imageUrl = call.argument<String>(NATIVE_METHOD_PLAY_ARG_IMAGE_URL)

                    // Call service
                    withService {
                        it.play(url, title, artist, album, imageUrl)
                    }
                }
            }
            NATIVE_METHOD_RESUME -> withService { it.resume() }
            NATIVE_METHOD_PAUSE -> withService { it.pause() }
            NATIVE_METHOD_STOP -> withService { it.stop() }
            NATIVE_METHOD_RELEASE -> releaseAudioService()
            NATIVE_METHOD_SEEK_TO -> {
                withArgument(call, NATIVE_METHOD_SEEK_TO_ARG_TIME) { timeInMillis: Int ->
                    withService { it.seekTo(timeInMillis.toLong()) }
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
            val serviceConnection: ServiceConnection = object : ServiceConnection {
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
            onLoaded = {
                try {
                    channel.invokeMethod(FLUTTER_METHOD_ON_LOADED, it)
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
        context.stopService(Intent(context, AudioService::class.java))
    }
}
