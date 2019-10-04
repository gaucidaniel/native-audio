package studio.darngood.soundbite

import android.app.ActivityManager
import android.content.Context

fun <T> Context.isServiceRunning(serviceClass: Class<T>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    manager.getRunningServices(Integer.MAX_VALUE).forEach {
        if (serviceClass.name == it.service.className) return true
    }

    return false
}