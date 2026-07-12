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
        val activePackage = rootNode.packageName?.toString()?.trim()?.lowercase() ?: return

        // Ignore events from our own configuration dashboard screen
        if (activePackage == applicationContext.packageName) {
            rootNode.recycle()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCheckTime < 400) { // Fluid debounce buffer protection
            rootNode.recycle()
            return
        }
        lastCheckTime = currentTime

        executor.execute {
            if (checkForTargetedKeywords(rootNode, activePackage)) {
                handler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, blockingDelay.toLong())
            }
            rootNode.recycle()
        }
    }

    private fun checkForTargetedKeywords(rootNode: AccessibilityNodeInfo, activePackage: String): Boolean {
        // 1. Gather all active layout strings and descriptions into lowercase
        val screenTexts = mutableListOf<String>()
        extractAllScreenText(rootNode, screenTexts)

        if (screenTexts.isEmpty()) return false

        // 2. Safely cycle through saved rules to find a contextual target match
        for (rule in keywords) {
            if (!rule.contains(":")) continue

            val parts = rule.split(":")
            if (parts.size < 2) continue

            val rulePackage = parts[0].trim().lowercase()
            val phrasesString = parts[1].trim().lowercase()

            // Conditional Target Match: Package matches exactly OR rule is a global wildcard '*'
            if (rulePackage == "*" || rulePackage == activePackage) {
                val triggerPhrases = phrasesString.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (triggerPhrases.isEmpty()) continue

                // Check if every single multi-phrase item in this rule line exists concurrently
                val allPhrasesMatch = triggerPhrases.all { phrase ->
                    screenTexts.any { screenText -> screenText.contains(phrase) }
                }

                if (allPhrasesMatch) {
                    Log.d(TAG, "checkForTargetedKeywords: Target matched app [$rulePackage]. Core phrases triggered block.")
                    return true
                }
            }
        }
        return false
    }

    private fun extractAllScreenText(node: AccessibilityNodeInfo?, textList: MutableList<String>) {
        if (node == null) return

        node.text?.toString()?.trim()?.lowercase()?.let {
            if (it.isNotEmpty()) textList.add(it)
        }
        node.contentDescription?.toString()?.trim()?.lowercase()?.let {
            if (it.isNotEmpty()) textList.add(it)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            extractAllScreenText(child, textList)
            child?.recycle()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        return super.onUnbind(intent)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "keywords" -> {
                keywords = sharedPreferences?.getStringSet("keywords", setOf()) ?: setOf()
            }
            "blockingEnabled" -> {
                blockingEnabled = sharedPreferences?.getBoolean("blockingEnabled", true) ?: true
            }
            "blockingDelay" -> {
                blockingDelay = sharedPreferences?.getInt("blockingDelay", 500) ?: 500
            }
        }
    }
}