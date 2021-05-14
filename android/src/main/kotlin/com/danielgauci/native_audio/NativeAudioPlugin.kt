package com.danielgauci.native_audio

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodChannel

class NativeAudioPlugin : FlutterPlugin {

    private lateinit var methodCallHandler: NativeAudioPluginHandler

    companion object {
        private const val CHANNEL = "com.danielgauci.native_audio"
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        methodCallHandler = NativeAudioPluginHandler(binding.applicationContext, channel)

        channel.setMethodCallHandler(methodCallHandler)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodCallHandler.dispose()
    }
}