package com.example.devicetracker

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "DeviceTrackerChannel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "MonitorService"
        private const val REPORT_INTERVAL_MS = 15 * 60 * 1000L // dakika 15
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: PrefsHelper

    // ===== Kipokezi cha Betri =====
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level == -1 || scale == -1) return

            val batteryPct = level * 100 / scale
            val isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                    BatteryManager.BATTERY_STATUS_CHARGING

            // Tuma arifa maalum betri chini ya 15%
            if (batteryPct <= 15 && !isCharging && !prefs.isLowBatteryAlertSent()) {
                prefs.setLowBatteryAlertSent(true)
                scope.launch {
                    TelegramHelper.sendMessage(
                        prefs.getToken(),
                        prefs.getChatId(),
                        buildBatteryAlert(batteryPct)
                    )
                }
            }

            // Reset flag ukifika 30%
            if (batteryPct > 30) {
                prefs.setLowBatteryAlertSent(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        registerBatteryReceiver()
        startLocationUpdates()

        Log.d(TAG, "Service imeanza")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs.setServiceRunning(true)
        return START_STICKY // Ianzishe upya ukiuawa na OS
    }

    // ===== Mahali (Location) =====
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            REPORT_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(REPORT_INTERVAL_MS / 2)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                prefs.saveLocation(location.latitude, location.longitude)

                // Tuma ripoti kamili kila dakika 15
                scope.launch {
                    sendStatusReport(location.latitude, location.longitude)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Ruhusa ya location imekataliwa: ${e.message}")
        }
    }

    // ===== Ripoti ya Hali =====
    private suspend fun sendStatusReport(lat: Double, lng: Double) {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging

        val chargeIcon = if (isCharging) "⚡" else "🔋"
        val chargeText = if (isCharging) "Inachaji" else "Haichaji"
        val mapLink = "https://maps.google.com/?q=$lat,$lng"
        val time = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date())

        val message = """
📊 *Ripoti ya Simu*

📍 [Angalia Mahali Kwenye Ramani]($mapLink)

$chargeIcon *Betri:* $batteryLevel% ($chargeText)

🕐 $time
        """.trimIndent()

        TelegramHelper.sendMessage(prefs.getToken(), prefs.getChatId(), message)
    }

    // ===== Arifa ya Betri Chini =====
    private fun buildBatteryAlert(level: Int): String {
        val locText = prefs.getLastLocationText()
        val time = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date())
        return """
⚠️ *BETRI CHINI - TAHADHARI!*

🔋 Betri imebaki *$level%* na haichajiwi!

$locText

🕐 $time
        """.trimIndent()
    }

    // ===== Notification =====
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "⚠️ Simu hii inafuatiliwa - Gonga kwa maelezo zaidi"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, SetupActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📡 Device Tracker Inafanya Kazi")
            .setContentText("⚠️ Simu hii inafuatiliwa. Gonga kwa maelezo.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Haiwezi kufutwa na user
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error on destroy: ${e.message}")
        }
        prefs.setServiceRunning(false)
        Log.d(TAG, "Service imesimama")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
