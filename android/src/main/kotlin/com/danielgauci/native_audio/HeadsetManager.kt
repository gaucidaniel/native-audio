package com.danielgauci.native_audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

class HeadsetManager {

    private var headsetReceiver: BroadcastReceiver? = null

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun registerHeadsetPlugReceiver(context: Context, onConnected: () -> Unit, onDisconnected: () -> Unit) {
        val filter = IntentFilter()
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG)

        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isInitialStickyBroadcast) return
                if (intent.hasExtra("state")) {
                    val state = intent.getIntExtra("state", 0)
                    if (state == 0) onDisconnected() else if (state == 1) onConnected()
                }
            }
        }

        context.registerReceiver(headsetReceiver, filter)
    }

    fun unregisterHeadsetPlugReceiver(context: Context) {
        context.unregisterReceiver(headsetReceiver)
    }
}