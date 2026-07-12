package com.example.app_blocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class KeywordsActivity : AppCompatActivity(), CountdownDialogFragment.CountdownDialogListener {

    private lateinit var editTextPackageName: EditText
    private lateinit var editTextKeyword: EditText
    private lateinit var buttonAddKeyword: Button
    private lateinit var listViewKeywords: ListView
    private lateinit var keywords: MutableSet<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var sharedPreferences: SharedPreferences
    private var keywordToRemove: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keywords)

        editTextPackageName = findViewById(R.id.editTextPackageName)
        editTextKeyword = findViewById(R.id.editTextKeyword)
        buttonAddKeyword = findViewById(R.id.buttonAddKeyword)
        listViewKeywords = findViewById(R.id.listViewKeywords)
        val buttonInfo = findViewById<TextView>(R.id.buttonInfo)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        keywords = sharedPreferences.getStringSet("keywords", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, keywords.toMutableList())
        listViewKeywords.adapter = adapter

        buttonInfo.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("App Blocker Quick Guide")
                .setMessage(
                    "📍 HOW TO TARGET AN APP\n" +
                            "Type the app's exact package ID in the top box. Common ones:\n" +
                            " • Instagram: com.instagram.android\n" +
                            " • Reddit: com.reddit.frontpage\n" +
                            " • YouTube: com.google.android.youtube\n" +
                            " • TikTok: com.zhiliaoapp.musically\n\n" +
                            "🌍 HOW TO TARGET ALL APPS (GLOBAL)\n" +
                            "Type a single asterisk * in the package box. The blocker will check your entire phone for your trigger words, no matter what app you open.\n\n" +
                            "💬 HOW TO FORMAT PHRASES\n" +
                            "Type words or full phrases. Separate them with a comma to create a pairing rule (e.g., 'for you, reels'). The app will ONLY block if ALL items are visible on your screen at the exact same time."
                )
                .setPositiveButton("Got it", null)
                .show()
        }

        buttonAddKeyword.setOnClickListener {
            val targetPackage = editTextPackageName.text.toString().trim().lowercase()
            val rawPhrases = editTextKeyword.text.toString().trim().lowercase()

            if (targetPackage.isEmpty()) {
                Toast.makeText(this, "Please enter an app package name or *", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (rawPhrases.isEmpty()) {
                Toast.makeText(this, "Please enter trigger phrases", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Bind into standard rule syntax -> package:phrase1,phrase2
            val formattedRule = "$targetPackage:$rawPhrases"

            if (!keywords.contains(formattedRule)) {
                keywords.add(formattedRule)
                saveKeywords()
                updateListView()

                editTextKeyword.text.clear()
                Toast.makeText(this, "Rule added successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "This exact rule profile already exists", Toast.LENGTH_SHORT).show()
            }
        }

        // Long-press removes a keyword instantly for fluid testing
        listViewKeywords.setOnItemLongClickListener { _, _, position, _ ->
            keywordToRemove = adapter.getItem(position)
            onActionConfirmed()
            Toast.makeText(this, "Rule removed instantly", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun saveKeywords() {
        sharedPreferences.edit().putStringSet("keywords", keywords).apply()
    }

    private fun updateListView() {
        adapter.clear()
        adapter.addAll(keywords.toList())
        adapter.notifyDataSetChanged()
    }

    override fun onDialogDismissed() {
        keywordToRemove = null
    }

    override fun onActionConfirmed() {
        if (keywordToRemove != null) {
            keywords.remove(keywordToRemove)
            saveKeywords()
            updateListView()
            keywordToRemove = null
        }
    }
}