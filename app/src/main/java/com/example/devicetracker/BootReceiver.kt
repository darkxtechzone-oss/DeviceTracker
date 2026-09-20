package com.example.devicetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PrefsHelper(context)

        
        if (prefs.getToken().isNotEmpty() && prefs.getChatId().isNotEmpty() && prefs.isServiceRunning()) {
            Log.d("BootReceiver", "Re start MonitorService baada ya reboot...")

            val serviceIntent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
