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

class WakeUpReminderSettingsActivity : AppCompatActivity(), CountdownDialogFragment.CountdownDialogListener {

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

    private var startMinutes: Int = WakeUpReminderService.DEFAULT_START_MINUTES
    private var endMinutes: Int = WakeUpReminderService.DEFAULT_END_MINUTES
    private var widthFraction: Float = WakeUpReminderService.DEFAULT_WIDTH_FRACTION
    private var heightFraction: Float = WakeUpReminderService.DEFAULT_HEIGHT_FRACTION

    private var pendingAction: PendingAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wake_up_reminder_settings)

        sharedPreferences = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)

        pendingAction = savedInstanceState?.getString(STATE_PENDING_ACTION)?.let { savedName ->
            runCatching { PendingAction.valueOf(savedName) }.getOrNull()
        }

        reminderTextInput = findViewById(R.id.wakeUpReminderTextInput)
        updateTextButton = findViewById(R.id.updateWakeUpReminderTextButton)
        startTimeButton = findViewById(R.id.wakeUpStartTimeButton)
        endTimeButton = findViewById(R.id.wakeUpEndTimeButton)
        widthSeekBar = findViewById(R.id.wakeUpWidthSeekBar)
        heightSeekBar = findViewById(R.id.wakeUpHeightSeekBar)
        widthValueLabel = findViewById(R.id.wakeUpWidthValueLabel)
        heightValueLabel = findViewById(R.id.wakeUpHeightValueLabel)
        saveButton = findViewById(R.id.saveWakeUpReminderButton)
        showButton = findViewById(R.id.showWakeUpReminderButton)
        hideButton = findViewById(R.id.hideWakeUpReminderButton)

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
        val text = sharedPreferences.getString(WakeUpReminderService.KEY_TEXT, WakeUpReminderService.DEFAULT_TEXT)
            ?: WakeUpReminderService.DEFAULT_TEXT
        reminderTextInput.setText(text)

        startMinutes = sharedPreferences.getInt(
            WakeUpReminderService.KEY_START_MINUTES,
            WakeUpReminderService.DEFAULT_START_MINUTES
        )
        endMinutes = sharedPreferences.getInt(
            WakeUpReminderService.KEY_END_MINUTES,
            WakeUpReminderService.DEFAULT_END_MINUTES
        )
        widthFraction = sharedPreferences.getFloat(
            WakeUpReminderService.KEY_WIDTH_FRACTION,
            WakeUpReminderService.DEFAULT_WIDTH_FRACTION
        )
        heightFraction = sharedPreferences.getFloat(
            WakeUpReminderService.KEY_HEIGHT_FRACTION,
            WakeUpReminderService.DEFAULT_HEIGHT_FRACTION
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
                Toast.makeText(this, R.string.wake_up_reminder_text_saved_toast, Toast.LENGTH_SHORT).show()
                WakeUpReminderService.requestRefresh(this)
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
                R.string.wake_up_reminder_save_countdown_message,
                R.string.wake_up_reminder_save_button,
                PendingAction.SAVE
            )
        }

        showButton.setOnClickListener {
            WakeUpReminderService.requestRefresh(this)
        }

        hideButton.setOnClickListener {
            showCountdownDialog(
                R.string.wake_up_reminder_hide_countdown_message,
                R.string.wake_up_reminder_hide_button,
                PendingAction.HIDE
            )
        }
    }

    private fun saveReminderText(): Boolean {
        val newText = reminderTextInput.text.toString().ifBlank { WakeUpReminderService.DEFAULT_TEXT }
        val currentText = sharedPreferences.getString(WakeUpReminderService.KEY_TEXT, WakeUpReminderService.DEFAULT_TEXT)
        if (currentText == newText) {
            return false
        }
        sharedPreferences.edit().putString(WakeUpReminderService.KEY_TEXT, newText).apply()
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
        widthValueLabel.text = getString(R.string.wake_up_reminder_width_value, (widthFraction * 100).roundToInt())
        heightValueLabel.text = getString(R.string.wake_up_reminder_height_value, (heightFraction * 100).roundToInt())
    }

    private fun applyScheduleAndSizeSettings() {
        sharedPreferences.edit()
            .putInt(WakeUpReminderService.KEY_START_MINUTES, startMinutes)
            .putInt(WakeUpReminderService.KEY_END_MINUTES, endMinutes)
            .putFloat(WakeUpReminderService.KEY_WIDTH_FRACTION, widthFraction)
            .putFloat(WakeUpReminderService.KEY_HEIGHT_FRACTION, heightFraction)
            .apply()

        WakeUpReminderService.requestRefresh(this)
        Toast.makeText(this, R.string.wake_up_reminder_saved_toast, Toast.LENGTH_SHORT).show()
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
            PendingAction.HIDE -> hideWakeUpReminder()
            null -> Unit
        }
        pendingAction = null
    }

    private fun hideWakeUpReminder() {
        stopService(Intent(this, WakeUpReminderService::class.java))
        Toast.makeText(this, R.string.wake_up_reminder_hidden_toast, Toast.LENGTH_SHORT).show()
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
        private const val COUNTDOWN_DIALOG_TAG = "WakeUpReminderCountdown"
    }
}
