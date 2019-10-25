package com.danielgauci.native_audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BluetoothManager {

    private var bluetoothReceiver: BroadcastReceiver? = null

    fun registerBluetoothReceiver(context: Context, onConnected: () -> Unit, onDisconnected: () -> Unit) {

        val filter = IntentFilter()
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                val action = intent.action
                if (action != null) {
                    val extras = intent.extras
                    when (action) {
                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> if (extras != null) {
                            val state = extras.getInt(BluetoothA2dp.EXTRA_STATE)
                            val previousState = extras.getInt(BluetoothA2dp.EXTRA_PREVIOUS_STATE)

                            if (state == BluetoothA2dp.STATE_CONNECTED) {
                                onConnected()
                            } else if ((state == BluetoothA2dp.STATE_DISCONNECTED || state == BluetoothA2dp.STATE_DISCONNECTING) && previousState == BluetoothA2dp.STATE_CONNECTED) {
                                onDisconnected()
                            }
                        }

                        BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> if (extras != null) {
                            val state = extras.getInt(BluetoothHeadset.EXTRA_STATE)
                            val previousState = extras.getInt(BluetoothHeadset.EXTRA_PREVIOUS_STATE)

                            if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                                onConnected()
                            } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED && previousState == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                                onDisconnected()
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun unregisterBluetoothReceiver(context: Context) {
        context.unregisterReceiver(bluetoothReceiver)
    }
}