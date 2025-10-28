package com.example.app_blocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val NOTIFICATION_PERMISSION_CODE = 1001
    private val TAG = "MainActivity"

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

        findViewById<Button>(R.id.manageKeywordsButton).setOnClickListener {
            Log.d(TAG, "onCreate: Manage Keywords button clicked.")
            // TODO: Open keyword management screen
        }

        findViewById<Button>(R.id.enableServiceButton).setOnClickListener {
            Log.d(TAG, "onCreate: Enable Service button clicked.")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        requestIgnoreBatteryOptimizations()
        requestNotificationPermission()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "requestNotificationPermission: Requesting notification permission.")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            } else {
                Log.d(TAG, "requestNotificationPermission: Notification permission already granted.")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Notification permission granted.")
            } else {
                Log.d(TAG, "onRequestPermissionsResult: Notification permission denied.")
            }
        }
    }
}