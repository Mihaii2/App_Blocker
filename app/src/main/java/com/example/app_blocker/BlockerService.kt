package com.example.app_blocker

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors

class BlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val CHANNEL_ID = "BlockerServiceChannel"
    private val TAG = "BlockerService"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var keywords: Set<String>
    private var blockingEnabled = true
    private var blockingDelay = 500
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var lastCheckTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: Service has been connected.")
        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        keywords = sharedPreferences.getStringSet("keywords", setOf()) ?: setOf()
        blockingEnabled = sharedPreferences.getBoolean("blockingEnabled", true)
        blockingDelay = sharedPreferences.getInt("blockingDelay", 500)
        startAsForegroundService()
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Blocker")
            .setContentText("Service is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(1, notification)
        Log.d(TAG, "startAsForegroundService: Service started in foreground.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Blocker Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "createNotificationChannel: Notification channel created.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Received start command.")
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !blockingEnabled || keywords.isEmpty()) {
            return
        }
        
        val rootNode = rootInActiveWindow ?: return

        // Ignore events from our own app
        if (rootNode.packageName != null && rootNode.packageName.toString() == applicationContext.packageName) {
            rootNode.recycle()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCheckTime < 250) { // Debounce check
            rootNode.recycle()
            return
        }
        lastCheckTime = currentTime

        executor.execute {
            if (checkForKeywords(rootNode)) {
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, blockingDelay.toLong())
            }
            rootNode.recycle()
        }
    }

    private fun checkForKeywords(rootNode: AccessibilityNodeInfo): Boolean {
        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue

            val text = node.text?.toString()
            val description = node.contentDescription?.toString()

            if (text != null && keywords.any { text.contains(it, ignoreCase = true) }) {
                Log.d(TAG, "checkForKeywords: Found keyword in text: '$text'")
                return true
            }

            if (description != null && keywords.any { description.contains(it, ignoreCase = true) }) {
                Log.d(TAG, "checkForKeywords: Found keyword in description: '$description'")
                return true
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: Service has been interrupted.")
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: Service has been unbound.")
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        return super.onUnbind(intent)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "keywords" -> {
                keywords = sharedPreferences?.getStringSet("keywords", setOf()) ?: setOf()
                Log.d(TAG, "onSharedPreferenceChanged: Keywords updated.")
            }
            "blockingEnabled" -> {
                blockingEnabled = sharedPreferences?.getBoolean("blockingEnabled", true) ?: true
                Log.d(TAG, "onSharedPreferenceChanged: Blocking enabled updated to: $blockingEnabled")
            }
            "blockingDelay" -> {
                blockingDelay = sharedPreferences?.getInt("blockingDelay", 500) ?: 500
                Log.d(TAG, "onSharedPreferenceChanged: Blocking delay updated to: $blockingDelay")
            }
        }
    }
}