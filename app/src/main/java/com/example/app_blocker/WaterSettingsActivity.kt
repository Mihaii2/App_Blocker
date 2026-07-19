package com.example.app_blocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class WaterSettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var waterReminderSwitch: SwitchMaterial
    private lateinit var waterIntervalInput: EditText
    private lateinit var waterStartHourInput: EditText
    private lateinit var waterEndHourInput: EditText
    private lateinit var waterTitleInput: EditText
    private lateinit var waterTextInput: EditText

    private lateinit var btnCommitStorage: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_water_settings)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)

        waterReminderSwitch = findViewById(R.id.waterReminderSwitch)
        waterIntervalInput = findViewById(R.id.waterIntervalInput)
        waterStartHourInput = findViewById(R.id.waterStartHourInput)
        waterEndHourInput = findViewById(R.id.waterEndHourInput)
        waterTitleInput = findViewById(R.id.waterTitleInput)
        waterTextInput = findViewById(R.id.waterTextInput)

        btnCommitStorage = findViewById(R.id.btnCommitStorage)

        // Load current state safely from device storage
        waterReminderSwitch.isChecked = sharedPreferences.getBoolean("waterReminderEnabled", false)
        waterIntervalInput.setText(sharedPreferences.getInt("waterIntervalMinutes", 60).toString())
        waterStartHourInput.setText(sharedPreferences.getInt("waterStartHour", 10).toString())
        waterEndHourInput.setText(sharedPreferences.getInt("waterEndHour", 22).toString())
        waterTitleInput.setText(sharedPreferences.getString("waterCustomTitle", ""))
        waterTextInput.setText(sharedPreferences.getString("waterCustomText", "Water"))

        waterReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveWaterConfig(false)
            sharedPreferences.edit().putBoolean("waterReminderEnabled", isChecked).apply()
            if (isChecked) {
                WaterReminderReceiver.scheduleNextReminder(this)
                Toast.makeText(this, "Water alarms active", Toast.LENGTH_SHORT).show()
            } else {
                WaterReminderReceiver.cancelReminder(this)
                Toast.makeText(this, "Water alarms stopped", Toast.LENGTH_SHORT).show()
            }
        }

        val instantSyncWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveWaterConfig(false)
                if (waterReminderSwitch.isChecked) {
                    WaterReminderReceiver.scheduleNextReminder(this@WaterSettingsActivity)
                }
            }
        }

        waterIntervalInput.addTextChangedListener(instantSyncWatcher)
        waterStartHourInput.addTextChangedListener(instantSyncWatcher)
        waterEndHourInput.addTextChangedListener(instantSyncWatcher)
        waterTitleInput.addTextChangedListener(instantSyncWatcher)
        waterTextInput.addTextChangedListener(instantSyncWatcher)

        // MANUAL COMMIT TRIGGER: Forces immediate hardware flash sync without editing any fields
        btnCommitStorage.setOnClickListener {
            val successful = saveWaterConfig(true)
            if (successful) {
                Toast.makeText(this, "All settings & keywords forced to disk!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage commit failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handles writing data to storage. If useCommit is true, it forces an immediate disk write.
    private fun saveWaterConfig(useCommit: Boolean): Boolean {
        val interval = waterIntervalInput.text.toString().toIntOrNull() ?: 60
        val start = waterStartHourInput.text.toString().toIntOrNull() ?: 10
        val end = waterEndHourInput.text.toString().toIntOrNull() ?: 22
        val title = waterTitleInput.text.toString()
        val text = waterTextInput.text.toString()

        val editor = sharedPreferences.edit()
            .putInt("waterIntervalMinutes", interval)
            .putInt("waterStartHour", start)
            .putInt("waterEndHour", end)
            .putString("waterCustomTitle", title)
            .putString("waterCustomText", text)

        return if (useCommit) {
            editor.commit() // Synchronous structural hardware commit
        } else {
            editor.apply() // Asynchronous cloud background cache write
            true
        }
    }
}