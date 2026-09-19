package com.example.devicetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.devicetracker.databinding.ActivitySetupBinding
import kotlinx.coroutines.*

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: PrefsHelper
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Ruhusa zinazohitajika
    private val requiredPermissions get() = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) {
            doStartMonitoring()
        } else {
            Toast.makeText(
                this,
                "❌ Ruhusa zifuatazo zimekataliwa:\n${denied.joinToString("\n") { it.substringAfterLast(".") }}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsHelper(this)

        // Pakia thamani zilizohifadhiwa
        binding.etToken.setText(prefs.getToken())
        binding.etChatId.setText(prefs.getChatId())
        updateStatusDisplay()

        // ===== Washa Ufuatiliaji =====
        binding.btnActivate.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "⚠️ Jaza Token na Chat ID kwanza!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.saveToken(token)
            prefs.saveChatId(chatId)
            checkPermissionsAndStart()
        }

        // ===== Simamisha Ufuatiliaji =====
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
            prefs.setServiceRunning(false)
            updateStatusDisplay()
            Toast.makeText(this, "🛑 Ufuatiliaji umesimama", Toast.LENGTH_SHORT).show()
        }

        // ===== Jaribu Muunganisho =====
        binding.btnTest.setOnClickListener {
            val token = binding.etToken.text.toString().trim()
            val chatId = binding.etChatId.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "⚠️ Jaza Token na Chat ID kwanza!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnTest.isEnabled = false
            binding.btnTest.text = "Inajaribu..."

            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    TelegramHelper.sendMessage(
                        token, chatId,
                        "✅ *Device Tracker - Majaribio*\n\nMuunganisho umefanikiwa! App iko tayari."
                    )
                }
                binding.btnTest.isEnabled = true
                binding.btnTest.text = "🔗 Jaribu Muunganisho"

                if (ok) {
                    Toast.makeText(this@SetupActivity, "✅ Ujumbe wa majaribio umetumwa!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SetupActivity, "❌ Imeshindwa! Angalia Token na Chat ID", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusDisplay()
    }

    private fun checkPermissionsAndStart() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            doStartMonitoring()
        } else {
            permLauncher.launch(missing.toTypedArray())
        }
    }

    private fun doStartMonitoring() {
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatusDisplay()
        Toast.makeText(this, "✅ Ufuatiliaji umeanzishwa!", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatusDisplay() {
        val running = prefs.isServiceRunning()
        binding.tvStatus.text = if (running) "Hali: 🟢 Inaendesha" else "Hali: 🔴 Imesimama"
        binding.btnStop.isEnabled = running
        binding.btnActivate.isEnabled = !running
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
