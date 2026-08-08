package com.example.app_blocker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class MainActivity : AppCompatActivity(), CountdownDialogFragment.CountdownDialogListener {

    private val OVERLAY_PERMISSION_CODE = 1002
    private val TAG = "MainActivity"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var blockingSwitch: SwitchMaterial
    private lateinit var delayInput: EditText
    private lateinit var transparencySeekBar: SeekBar
    private lateinit var btnCommitStorageMain: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        blockingSwitch = findViewById(R.id.blockingSwitch)
        delayInput = findViewById(R.id.delayInput)
        transparencySeekBar = findViewById(R.id.transparencySeekBar)
        btnCommitStorageMain = findViewById(R.id.btnCommitStorageMain)

        blockingSwitch.isChecked = sharedPreferences.getBoolean("blockingEnabled", true)
        delayInput.setText(sharedPreferences.getInt("blockingDelay", 500).toString())
        transparencySeekBar.progress = (sharedPreferences.getFloat("timerTransparency", 0.8f) * 100).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        blockingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                val dialog = CountdownDialogFragment.newInstance("You have to wait 30 seconds before disabling blocking", "Disable")
                dialog.show(supportFragmentManager, "CountdownDialogFragment")
                blockingSwitch.isChecked = true
            } else {
                sharedPreferences.edit().putBoolean("blockingEnabled", true).apply()
            }
        }

        transparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val transparency = progress / 100f
                sharedPreferences.edit().putFloat("timerTransparency", transparency).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                restartTimerService()
            }
        })

        findViewById<Button>(R.id.manageKeywordsButton).setOnClickListener {
            startActivity(Intent(this, KeywordsActivity::class.java))
        }

        findViewById<Button>(R.id.notificationSchedulerButton).setOnClickListener {
            startActivity(Intent(this, NotificationSchedulerActivity::class.java))
        }

        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // COMMIT ALL STRING SETS TO DISK PERSISTENTLY
        btnCommitStorageMain.setOnClickListener {
            val delay = delayInput.text.toString().toIntOrNull() ?: 500
            val freshPrefs = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)

            val currentKeywords = freshPrefs.getStringSet("keywords", emptySet()) ?: emptySet()
            val currentNotifications = freshPrefs.getStringSet("notifications", emptySet()) ?: emptySet()

            val success = freshPrefs.edit()
                .putInt("blockingDelay", delay)
                .putBoolean("blockingEnabled", blockingSwitch.isChecked)
                .putFloat("timerTransparency", transparencySeekBar.progress / 100f)
                .putStringSet("keywords", currentKeywords)
                .putStringSet("notifications", currentNotifications)
                .commit()

            if (success) {
                Toast.makeText(this, "Committed keywords, notifications & settings to disk!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to commit data", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.enableServiceButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.enableAutostartButton).setOnClickListener {
            try {
                val intent = Intent()
                intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Autostart settings shortcut not supported on this device.", Toast.LENGTH_SHORT).show()
            }
        }

        requestIgnoreBatteryOptimizations()
        checkOverlayPermission()
        scheduleMidnightReset()
    }

    private fun restartTimerService() {
        stopService(Intent(this, TimerService::class.java))
        ContextCompat.startForegroundService(this, Intent(this, TimerService::class.java))
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        blockingSwitch.isChecked = sharedPreferences.getBoolean("blockingEnabled", true)
        delayInput.setText(sharedPreferences.getInt("blockingDelay", 500).toString())
        ServiceHeartbeatReceiver.triggerImmediateHeartbeat(this)
    }

    override fun onPause() {
        super.onPause()
        val delay = delayInput.text.toString().toIntOrNull() ?: 500
        sharedPreferences.edit().putInt("blockingDelay", delay).apply()
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
            }
        }
    }

    private fun scheduleMidnightReset() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MidnightReceiver::class.java).apply {
            action = "com.example.app_blocker.MIDNIGHT"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission is required for the timer to work.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDialogDismissed() {}

    override fun onActionConfirmed() {
        blockingSwitch.setOnCheckedChangeListener(null)
        blockingSwitch.isChecked = false
        sharedPreferences.edit().putBoolean("blockingEnabled", false).apply()
        blockingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                val dialog = CountdownDialogFragment.newInstance("You have to wait 30 seconds before disabling blocking", "Disable")
                dialog.show(supportFragmentManager, "CountdownDialogFragment")
                blockingSwitch.isChecked = true
            } else {
                sharedPreferences.edit().putBoolean("blockingEnabled", true).apply()
            }
        }
    }
}