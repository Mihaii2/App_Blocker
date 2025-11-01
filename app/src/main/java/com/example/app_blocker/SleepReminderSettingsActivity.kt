package com.example.app_blocker

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.roundToInt

class SleepReminderSettingsActivity : AppCompatActivity(), CountdownDialogFragment.CountdownDialogListener {

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var reminderTextInput: EditText
    private lateinit var updateTextButton: Button
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var widthSeekBar: SeekBar
    private lateinit var heightSeekBar: SeekBar
    private lateinit var widthValueLabel: TextView
    private lateinit var heightValueLabel: TextView
    private lateinit var saveButton: Button
    private lateinit var showButton: Button
    private lateinit var hideButton: Button

    private var startMinutes: Int = SleepReminderService.DEFAULT_START_MINUTES
    private var endMinutes: Int = SleepReminderService.DEFAULT_END_MINUTES
    private var widthFraction: Float = SleepReminderService.DEFAULT_WIDTH_FRACTION
    private var heightFraction: Float = SleepReminderService.DEFAULT_HEIGHT_FRACTION

    private var pendingAction: PendingAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_reminder_settings)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)

        pendingAction = savedInstanceState?.getString(STATE_PENDING_ACTION)?.let { savedName ->
            runCatching { PendingAction.valueOf(savedName) }.getOrNull()
        }

        reminderTextInput = findViewById(R.id.sleepReminderTextInput)
        updateTextButton = findViewById(R.id.updateSleepReminderTextButton)
        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        widthSeekBar = findViewById(R.id.widthSeekBar)
        heightSeekBar = findViewById(R.id.heightSeekBar)
        widthValueLabel = findViewById(R.id.widthValueLabel)
        heightValueLabel = findViewById(R.id.heightValueLabel)
        saveButton = findViewById(R.id.saveSleepReminderButton)
        showButton = findViewById(R.id.showSleepReminderButton)
        hideButton = findViewById(R.id.hideSleepReminderButton)

        loadCurrentValues()
        bindInteractions()

        val existingDialog = supportFragmentManager.findFragmentByTag(COUNTDOWN_DIALOG_TAG) as? CountdownDialogFragment
        if (existingDialog != null && pendingAction == null) {
            pendingAction = existingDialog.getActionTag()?.let { savedName ->
                runCatching { PendingAction.valueOf(savedName) }.getOrNull()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_ACTION, pendingAction?.name)
    }

    private fun loadCurrentValues() {
        val text = sharedPreferences.getString(SleepReminderService.KEY_TEXT, SleepReminderService.DEFAULT_TEXT)
            ?: SleepReminderService.DEFAULT_TEXT
        reminderTextInput.setText(text)

        startMinutes = sharedPreferences.getInt(
            SleepReminderService.KEY_START_MINUTES,
            SleepReminderService.DEFAULT_START_MINUTES
        )
        endMinutes = sharedPreferences.getInt(
            SleepReminderService.KEY_END_MINUTES,
            SleepReminderService.DEFAULT_END_MINUTES
        )
        widthFraction = sharedPreferences.getFloat(
            SleepReminderService.KEY_WIDTH_FRACTION,
            SleepReminderService.DEFAULT_WIDTH_FRACTION
        )
        heightFraction = sharedPreferences.getFloat(
            SleepReminderService.KEY_HEIGHT_FRACTION,
            SleepReminderService.DEFAULT_HEIGHT_FRACTION
        )

        startTimeButton.text = formatMinutes(startMinutes)
        endTimeButton.text = formatMinutes(endMinutes)

        widthSeekBar.progress = (widthFraction * 100).roundToInt()
        heightSeekBar.progress = (heightFraction * 100).roundToInt()
        updateSizeLabels()
    }

    private fun bindInteractions() {
        updateTextButton.setOnClickListener {
            val changed = saveReminderText()
            if (changed) {
                Toast.makeText(this, R.string.sleep_reminder_text_saved_toast, Toast.LENGTH_SHORT).show()
                SleepReminderService.requestRefresh(this)
            }
        }

        startTimeButton.setOnClickListener {
            openTimePicker(startMinutes) { minutes ->
                startMinutes = minutes
                startTimeButton.text = formatMinutes(minutes)
            }
        }

        endTimeButton.setOnClickListener {
            openTimePicker(endMinutes) { minutes ->
                endMinutes = minutes
                endTimeButton.text = formatMinutes(minutes)
            }
        }

        widthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val coerced = progress.coerceIn(20, 100)
                widthFraction = coerced / 100f
                if (coerced != progress) {
                    widthSeekBar.progress = coerced
                }
                updateSizeLabels()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        heightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val coerced = progress.coerceIn(20, 100)
                heightFraction = coerced / 100f
                if (coerced != progress) {
                    heightSeekBar.progress = coerced
                }
                updateSizeLabels()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        saveButton.setOnClickListener {
            showCountdownDialog(
                R.string.sleep_reminder_save_countdown_message,
                R.string.sleep_reminder_save_button,
                PendingAction.SAVE
            )
        }

        showButton.setOnClickListener {
            SleepReminderService.requestRefresh(this)
        }

        hideButton.setOnClickListener {
            showCountdownDialog(
                R.string.sleep_reminder_hide_countdown_message,
                R.string.sleep_reminder_hide_button,
                PendingAction.HIDE
            )
        }
    }

    private fun saveReminderText(): Boolean {
        val newText = reminderTextInput.text.toString().ifBlank { SleepReminderService.DEFAULT_TEXT }
        val currentText = sharedPreferences.getString(SleepReminderService.KEY_TEXT, SleepReminderService.DEFAULT_TEXT)
        if (currentText == newText) {
            return false
        }
        sharedPreferences.edit().putString(SleepReminderService.KEY_TEXT, newText).apply()
        return true
    }

    private fun openTimePicker(currentMinutes: Int, onResult: (Int) -> Unit) {
        val hour = currentMinutes / 60
        val minute = currentMinutes % 60
        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val minutes = selectedHour * 60 + selectedMinute
            onResult(minutes)
        }, hour, minute, true).show()
    }

    private fun updateSizeLabels() {
        widthValueLabel.text = getString(R.string.sleep_reminder_width_value, (widthFraction * 100).roundToInt())
        heightValueLabel.text = getString(R.string.sleep_reminder_height_value, (heightFraction * 100).roundToInt())
    }

    private fun applyScheduleAndSizeSettings() {
        sharedPreferences.edit()
            .putInt(SleepReminderService.KEY_START_MINUTES, startMinutes)
            .putInt(SleepReminderService.KEY_END_MINUTES, endMinutes)
            .putFloat(SleepReminderService.KEY_WIDTH_FRACTION, widthFraction)
            .putFloat(SleepReminderService.KEY_HEIGHT_FRACTION, heightFraction)
            .apply()

        SleepReminderService.requestRefresh(this)
        Toast.makeText(this, R.string.sleep_reminder_saved_toast, Toast.LENGTH_SHORT).show()
    }

    private fun formatMinutes(minutes: Int): String {
        val clamped = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        val hour = clamped / 60
        val minute = clamped % 60
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    override fun onDialogDismissed() {
        pendingAction = null
    }

    override fun onActionConfirmed() {
        when (val action = pendingAction) {
            PendingAction.SAVE -> applyScheduleAndSizeSettings()
            PendingAction.HIDE -> hideSleepReminder()
            null -> Unit
        }
        pendingAction = null
    }

    private fun hideSleepReminder() {
        stopService(Intent(this, SleepReminderService::class.java))
        Toast.makeText(this, R.string.sleep_reminder_hidden_toast, Toast.LENGTH_SHORT).show()
    }

    private fun showCountdownDialog(messageRes: Int, buttonTextRes: Int, action: PendingAction) {
        if (supportFragmentManager.findFragmentByTag(COUNTDOWN_DIALOG_TAG) != null) {
            return
        }
        pendingAction = action
        val dialog = CountdownDialogFragment.newInstance(
            getString(messageRes),
            getString(buttonTextRes),
            action.name
        )
        dialog.show(supportFragmentManager, COUNTDOWN_DIALOG_TAG)
    }

    private enum class PendingAction { SAVE, HIDE }

    companion object {
        private const val STATE_PENDING_ACTION = "state_pending_action"
        private const val COUNTDOWN_DIALOG_TAG = "SleepReminderCountdown"
    }
}
