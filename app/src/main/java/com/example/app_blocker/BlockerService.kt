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

class BlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val CHANNEL_ID = "BlockerServiceChannel"
    private val TAG = "BlockerService"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var keywords: Set<String>
    private var blockingEnabled = true
    private var blockingDelay = 1000

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: Service has been connected.")
        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        keywords = sharedPreferences.getStringSet("keywords", setOf("facebook.com", "facebook", "instagram")) ?: setOf()
        blockingEnabled = sharedPreferences.getBoolean("blockingEnabled", true)
        blockingDelay = sharedPreferences.getInt("blockingDelay", 1000)
        startAsForegroundService()
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Blocker")
            .setContentText("Service is running.")
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
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
        if (event == null || !blockingEnabled) {
            return
        }

        Log.d(TAG, "onAccessibilityEvent: Received event type: " + AccessibilityEvent.eventTypeToString(event.eventType) + " from package: " + event.packageName)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.d(TAG, "onAccessibilityEvent: Root node is null.")
                return
            }

            val rootPackageName = rootNode.packageName
            if (rootPackageName != null && rootPackageName.toString() == applicationContext.packageName) {
                Log.d(TAG, "onAccessibilityEvent: Active window is own app ($rootPackageName), ignoring.")
                rootNode.recycle()
                return
            }

            checkForKeywords(rootNode)
        }
    }

    private fun checkForKeywords(rootNode: AccessibilityNodeInfo) {
        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue

            val text = node.text?.toString()
            val description = node.contentDescription?.toString()

            if (text != null && keywords.any { text.contains(it, ignoreCase = true) }) {
                Log.d(TAG, "checkForKeywords: Found keyword in text: '$text'")
                closeAppWithDelay()
                node.recycle()
                return
            }

            if (description != null && keywords.any { description.contains(it, ignoreCase = true) }) {
                Log.d(TAG, "checkForKeywords: Found keyword in description: '$description'")
                closeAppWithDelay()
                node.recycle()
                return
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
            node.recycle()
        }
    }

    private fun closeAppWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, blockingDelay.toLong())
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
        if (key == "keywords") {
            keywords = sharedPreferences?.getStringSet("keywords", setOf("facebook.com", "facebook", "instagram")) ?: setOf()
            Log.d(TAG, "onSharedPreferenceChanged: Keywords updated.")
        } else if (key == "blockingEnabled") {
            blockingEnabled = sharedPreferences?.getBoolean("blockingEnabled", true) ?: true
            Log.d(TAG, "onSharedPreferenceChanged: Blocking enabled updated to: $blockingEnabled")
        } else if (key == "blockingDelay") {
            blockingDelay = sharedPreferences?.getInt("blockingDelay", 1000) ?: 1000
            Log.d(TAG, "onSharedPreferenceChanged: Blocking delay updated to: $blockingDelay")
        }
    }
}