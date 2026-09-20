package com.example.devicetracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object TelegramHelper {

    /**
     * Tuma txt kwenye Telegram bot.
     * Inarudisha true kama imefanikiwa, false kama imeshindwa.
     */
    suspend fun sendMessage(
        token: String,
        chatId: String,
        message: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isEmpty() || chatId.isEmpty()) return@withContext false

        try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
            }

            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
                put("parse_mode", "Markdown")
                put("disable_web_page_preview", false)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
