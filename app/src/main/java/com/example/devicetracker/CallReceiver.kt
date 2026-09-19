package com.example.devicetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") return

        val prefs = PrefsHelper(context)

        // Kama token haijawekwa, haifanyi kitu
        if (prefs.getToken().isEmpty()) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val previousState = prefs.getCallState()

        Log.d(TAG, "Hali: $previousState -> $stateStr")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Simu inakuja - hifadhi hali
                prefs.saveCallState("RINGING")
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Simu ilijibiwa
                prefs.saveCallState("OFFHOOK")
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasRinging = previousState == "RINGING"
                prefs.saveCallState("IDLE")

                // Simu iliyokosekana = ilikuwa inalia lakini haikujibiwa
                if (wasRinging) {
                    // goAsync() ili tuwe na muda wa kufanya network call
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Subiri kidogo ili call log isasaishwe
                            delay(2000)
                            val number = getMissedCallNumber(context)
                            sendMissedCallAlert(prefs, number)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    // ===== Pata nambari kutoka call log =====
    private fun getMissedCallNumber(context: Context): String {
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    if (!number.isNullOrEmpty()) number else "Nambari isiyojulikana"
                } else {
                    "Nambari isiyojulikana"
                }
            } ?: "Nambari isiyojulikana"

        } catch (e: Exception) {
            Log.e(TAG, "Kosa la call log: ${e.message}")
            "Nambari isiyojulikana"
        }
    }

    // ===== Tuma arifa ya Telegram =====
    private suspend fun sendMissedCallAlert(prefs: PrefsHelper, number: String) {
        val locText = prefs.getLastLocationText()
        val time = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date())

        val message = """
📵 *SIMU ILIYOKOSEKANA!*

📞 *Kutoka:* `$number`
🕐 *Saa:* $time

$locText
        """.trimIndent()

        val success = TelegramHelper.sendMessage(
            prefs.getToken(),
            prefs.getChatId(),
            message
        )
        Log.d(TAG, "Arifa ya simu iliyokosekana: ${if (success) "✅" else "❌"}")
    }
}
