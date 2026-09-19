package com.example.devicetracker

import android.content.Context
import android.content.SharedPreferences

class PrefsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("DeviceTrackerPrefs", Context.MODE_PRIVATE)

    // ===== Telegram =====
    fun saveToken(token: String) = prefs.edit().putString("TOKEN", token).apply()
    fun getToken(): String = prefs.getString("TOKEN", "") ?: ""

    fun saveChatId(chatId: String) = prefs.edit().putString("CHAT_ID", chatId).apply()
    fun getChatId(): String = prefs.getString("CHAT_ID", "") ?: ""

    // ===== Hali ya Huduma =====
    fun setServiceRunning(running: Boolean) = prefs.edit().putBoolean("SERVICE_RUNNING", running).apply()
    fun isServiceRunning(): Boolean = prefs.getBoolean("SERVICE_RUNNING", false)

    // ===== Mahali (Location) - kuhifadhi ya mwisho =====
    fun saveLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putString("LAST_LAT", lat.toString())
            .putString("LAST_LNG", lng.toString())
            .apply()
    }

    fun getLastLocationText(): String {
        val lat = prefs.getString("LAST_LAT", null)
        val lng = prefs.getString("LAST_LNG", null)
        return if (lat != null && lng != null) {
            "📍 [Angalia Ramani](https://maps.google.com/?q=$lat,$lng)"
        } else {
            "📍 Location haipatikani bado"
        }
    }

    // ===== Hali ya Simu (kwa CallReceiver) =====
    fun saveCallState(state: String) = prefs.edit().putString("CALL_STATE", state).apply()
    fun getCallState(): String = prefs.getString("CALL_STATE", "IDLE") ?: "IDLE"

    // ===== Betri - kuzuia arifa nyingi =====
    fun setLowBatteryAlertSent(sent: Boolean) = prefs.edit().putBoolean("LOW_BAT_SENT", sent).apply()
    fun isLowBatteryAlertSent(): Boolean = prefs.getBoolean("LOW_BAT_SENT", false)
}
