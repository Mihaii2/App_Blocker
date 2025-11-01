package com.example.app_blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Executors

class BlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Received start command.")
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !blockingEnabled || keywords.isEmpty()) {
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        // Ignore events from our own app
        if (rootNode.packageName != null && rootNode.packageName.toString() == applicationContext.packageName) {
            rootNode.recycle()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCheckTime < 500) { // Debounce check
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
        val visitedNodes = HashSet<AccessibilityNodeInfo>()

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue

            if (!visitedNodes.add(node)) {
                continue
            }

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