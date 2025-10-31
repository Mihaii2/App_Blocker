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
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class MainActivity : AppCompatActivity(), CountdownDialogFragment.CountdownDialogListener {

    private val OVERLAY_PERMISSION_CODE = 1002
    private val TAG = "MainActivity"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var blockingSwitch: SwitchMaterial
    private lateinit var delayInput: EditText
    private lateinit var transparencySeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity created.")
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        blockingSwitch = findViewById(R.id.blockingSwitch)
        delayInput = findViewById(R.id.delayInput)
        transparencySeekBar = findViewById(R.id.transparencySeekBar)

        blockingSwitch.isChecked = sharedPreferences.getBoolean("blockingEnabled", true)
        delayInput.setText(sharedPreferences.getInt("blockingDelay", 500).toString())
        transparencySeekBar.progress = (sharedPreferences.getFloat("timerTransparency", 0.8f) * 100).toInt()

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
            Log.d(TAG, "onCreate: Manage Keywords button clicked.")
            startActivity(Intent(this, KeywordsActivity::class.java))
        }

        findViewById<Button>(R.id.enableServiceButton).setOnClickListener {
            Log.d(TAG, "onCreate: Enable Service button clicked.")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        
        findViewById<Button>(R.id.enableAutostartButton).setOnClickListener {
            val intent = Intent()
            intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            startActivity(intent)
        }

        findViewById<Button>(R.id.startTrackingButton).setOnClickListener {
            val intent = Intent(this, ScreenTimeTrackerService::class.java)
            startService(intent)
        }

        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        requestIgnoreBatteryOptimizations()
        checkOverlayPermission()
        scheduleMidnightReset()
    }

    private fun restartTimerService() {
        stopService(Intent(this, TimerService::class.java))
        startService(Intent(this, TimerService::class.java))
    }

    override fun onPause() {
        super.onPause()
        val delay = delayInput.text.toString().toIntOrNull() ?: 1000
        sharedPreferences.edit().putInt("blockingDelay", delay).apply()
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent()
        val packageName = packageName
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.d(TAG, "requestIgnoreBatteryOptimizations: Requesting to ignore battery optimizations.")
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            Log.d(TAG, "requestIgnoreBatteryOptimizations: Already ignoring battery optimizations.")
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
            add(Calendar.DAY_OF_YEAR, 1) // Set for tomorrow midnight
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
                if (Settings.canDrawOverlays(this)) {
                    // Permission granted
                } else {
                    Toast.makeText(this, "Overlay permission is required for the timer to work.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDialogDismissed() {
        // Nothing to do here
    }

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