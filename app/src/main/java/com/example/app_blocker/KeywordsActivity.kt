package com.example.app_blocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class KeywordsActivity : AppCompatActivity() {

    private lateinit var editTextKeyword: EditText
    private lateinit var buttonAddKeyword: Button
    private lateinit var listViewKeywords: ListView
    private lateinit var keywords: MutableSet<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keywords)

        editTextKeyword = findViewById(R.id.editTextKeyword)
        buttonAddKeyword = findViewById(R.id.buttonAddKeyword)
        listViewKeywords = findViewById(R.id.listViewKeywords)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        keywords = sharedPreferences.getStringSet("keywords", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, keywords.toMutableList())
        listViewKeywords.adapter = adapter

        buttonAddKeyword.setOnClickListener {
            val keyword = editTextKeyword.text.toString().trim()
            if (keyword.isNotEmpty() && !keywords.contains(keyword)) {
                keywords.add(keyword)
                saveKeywords()
                updateListView()
                editTextKeyword.text.clear()
            } else if (keywords.contains(keyword)) {
                Toast.makeText(this, "Keyword already exists", Toast.LENGTH_SHORT).show()
            }
        }

        listViewKeywords.setOnItemLongClickListener { _, _, position, _ ->
            val keywordToRemove = adapter.getItem(position)
            AlertDialog.Builder(this)
                .setTitle("Remove Keyword")
                .setMessage("Are you sure you want to remove \"$keywordToRemove\"?")
                .setPositiveButton("Yes") { _, _ ->
                    keywords.remove(keywordToRemove)
                    saveKeywords()
                    updateListView()
                }
                .setNegativeButton("No", null)
                .show()
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
}