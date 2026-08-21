package com.example.app_blocker

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NotificationSchedulerActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var listContainer: LinearLayout
    private lateinit var globalStartInput: EditText
    private lateinit var globalEndInput: EditText
    private val notificationsList = mutableListOf<NotificationItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notification_scheduler)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootSchedulerLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.rootSchedulerLayout).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard(currentFocus)
            }
            false
        }

        sharedPrefs = getSharedPreferences("AppBlockerPrefs", Context.MODE_PRIVATE)
        listContainer = findViewById(R.id.listContainer)
        globalStartInput = findViewById(R.id.globalStartInput)
        globalEndInput = findViewById(R.id.globalEndInput)

        globalStartInput.setText(sharedPrefs.getInt("globalStartHour", 10).toString())
        globalEndInput.setText(sharedPrefs.getInt("globalEndHour", 22).toString())

        val globalHoursWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val start = globalStartInput.text.toString().toIntOrNull() ?: 10
                val end = globalEndInput.text.toString().toIntOrNull() ?: 22
                sharedPrefs.edit().putInt("globalStartHour", start).putInt("globalEndHour", end).commit()
            }
        }

        globalStartInput.addTextChangedListener(globalHoursWatcher)
        globalEndInput.addTextChangedListener(globalHoursWatcher)

        findViewById<Button>(R.id.btnAddNotification).setOnClickListener {
            showEditDialog(null)
        }

        loadData()
        NotificationSchedulerReceiver.scheduleNextCheck(this)
    }

    private fun hideKeyboard(view: View?) {
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        notificationsList.clear()
        notificationsList.addAll(NotificationSchedulerReceiver.loadNotifications(sharedPrefs))
        notificationsList.sort()
        renderList()
    }

    private fun saveData() {
        notificationsList.sort()
        NotificationSchedulerReceiver.saveNotifications(sharedPrefs, notificationsList)
        NotificationSchedulerReceiver.scheduleNextCheck(this)
        renderList()
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (item in notificationsList) {
            val view = inflater.inflate(R.layout.item_notification_card, listContainer, false)
            val txtTime = view.findViewById<TextView>(R.id.txtTime)
            val txtLabel = view.findViewById<TextView>(R.id.txtLabel)
            val txtDays = view.findViewById<TextView>(R.id.txtDays)
            val itemSwitch = view.findViewById<SwitchMaterial>(R.id.itemSwitch)

            txtTime.text = item.getTimeSummary()
            txtLabel.text = if (item.title.isNotBlank()) "${item.title} - ${item.text}" else item.text
            txtDays.text = item.getDaysSummary()
            itemSwitch.isChecked = item.enabled

            if (item.enabled) {
                view.setBackgroundColor(Color.parseColor("#222222"))
                txtTime.setTextColor(Color.parseColor("#66BBBA"))
                txtLabel.setTextColor(Color.parseColor("#FFFFFF"))
                txtDays.setTextColor(Color.parseColor("#AAAAAA"))
            } else {
                view.setBackgroundColor(Color.parseColor("#161616"))
                txtTime.setTextColor(Color.parseColor("#555555"))
                txtLabel.setTextColor(Color.parseColor("#666666"))
                txtDays.setTextColor(Color.parseColor("#444444"))
            }

            itemSwitch.setOnCheckedChangeListener { _, isChecked ->
                item.enabled = isChecked
                if (isChecked) {
                    item.lastTriggeredMillis = System.currentTimeMillis()
                }
                saveData()
            }

            view.setOnClickListener {
                showEditDialog(item)
            }

            listContainer.addView(view)
        }
    }

    private fun showEditDialog(existingItem: NotificationItem?) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_edit_notification, null)

        val rbInterval = view.findViewById<RadioButton>(R.id.rbInterval)
        val rbExact = view.findViewById<RadioButton>(R.id.rbExact)
        val editInterval = view.findViewById<EditText>(R.id.editInterval)
        val txtIntervalHoursPreview = view.findViewById<TextView>(R.id.txtIntervalHoursPreview)
        val editHour = view.findViewById<EditText>(R.id.editHour)
        val editMinute = view.findViewById<EditText>(R.id.editMinute)
        val editTitle = view.findViewById<EditText>(R.id.editTitle)
        val editText = view.findViewById<EditText>(R.id.editText)
        val chkSelectAll = view.findViewById<CheckBox>(R.id.chkSelectAll)
        val txtNextRunPreview = view.findViewById<TextView>(R.id.txtNextRunPreview)

        val chkMon = view.findViewById<CheckBox>(R.id.chkMon)
        val chkTue = view.findViewById<CheckBox>(R.id.chkTue)
        val chkWed = view.findViewById<CheckBox>(R.id.chkWed)
        val chkThu = view.findViewById<CheckBox>(R.id.chkThu)
        val chkFri = view.findViewById<CheckBox>(R.id.chkFri)
        val chkSat = view.findViewById<CheckBox>(R.id.chkSat)
        val chkSun = view.findViewById<CheckBox>(R.id.chkSun)

        val dayCheckBoxes = listOf(chkMon, chkTue, chkWed, chkThu, chkFri, chkSat, chkSun)
        val dayMap = mapOf(
            2 to chkMon, 3 to chkTue, 4 to chkWed, 5 to chkThu, 6 to chkFri, 7 to chkSat, 1 to chkSun
        )

        val target = existingItem ?: NotificationItem()
        rbInterval.isChecked = target.isInterval
        rbExact.isChecked = !target.isInterval
        editInterval.setText(target.intervalMinutes.toString())
        editHour.setText(target.exactHour.toString())
        editMinute.setText(target.exactMinute.toString())
        editTitle.setText(target.title)
        editText.setText(target.text)

        dayMap.forEach { (dayCode, chk) -> chk.isChecked = target.days.contains(dayCode) }
        chkSelectAll.isChecked = target.days.size == 7

        fun formatDuration(totalMins: Int): String {
            val days = totalMins / (24 * 60)
            val remainingMinsAfterDays = totalMins % (24 * 60)
            val hours = remainingMinsAfterDays / 60
            val mins = remainingMinsAfterDays % 60

            val parts = mutableListOf<String>()
            if (days > 0) parts.add("${days}d")
            if (hours > 0) parts.add("${hours}h")
            if (mins > 0 || parts.isEmpty()) parts.add("${mins}m")

            return parts.joinToString(" ")
        }

        val calculateNextRun = {
            val now = Calendar.getInstance()
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val selectedDays = dayMap.filter { it.value.isChecked }.keys

            if (rbInterval.isChecked) {
                val mins = editInterval.text.toString().toIntOrNull() ?: 60
                val targetTime = Calendar.getInstance().apply { add(Calendar.MINUTE, mins) }
                val formattedExact = timeFormat.format(targetTime.time)

                txtNextRunPreview.text = "Fires in ${formatDuration(mins)} at $formattedExact"
            } else {
                val targetHour = editHour.text.toString().toIntOrNull() ?: 12
                val targetMinute = editMinute.text.toString().toIntOrNull() ?: 0

                val targetCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (selectedDays.isEmpty() || selectedDays.size == 7) {
                    if (targetCalendar.before(now)) {
                        targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
                    }
                } else {
                    var daysChecked = 0
                    while (daysChecked < 7) {
                        val currentDayCode = targetCalendar.get(Calendar.DAY_OF_WEEK)
                        val isTimeValid = targetCalendar.after(now)

                        if (selectedDays.contains(currentDayCode) && isTimeValid) {
                            break
                        }

                        targetCalendar.add(Calendar.DAY_OF_YEAR, 1)
                        daysChecked++
                    }
                }

                val diffMillis = targetCalendar.timeInMillis - now.timeInMillis
                val totalMins = (diffMillis / (1000 * 60)).toInt().coerceAtLeast(0)
                val formattedExact = timeFormat.format(targetCalendar.time)

                txtNextRunPreview.text = "Fires in ${formatDuration(totalMins)} at $formattedExact"
            }
        }

        val updateHoursPreview = {
            val mins = editInterval.text.toString().toIntOrNull() ?: 0
            txtIntervalHoursPreview.text = "(${formatDuration(mins)})"
            calculateNextRun()
        }

        val selectIntervalRadio = {
            if (!rbInterval.isChecked) {
                rbInterval.isChecked = true
                rbExact.isChecked = false
            }
            updateHoursPreview()
        }

        val selectExactRadio = {
            if (!rbExact.isChecked) {
                rbExact.isChecked = true
                rbInterval.isChecked = false
            }
            updateHoursPreview()
        }

        // RECALCULATE ON EVERY FOCUS AND CLICK
        editInterval.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectIntervalRadio() else updateHoursPreview()
        }
        editInterval.setOnClickListener { selectIntervalRadio() }

        editHour.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectExactRadio() else updateHoursPreview()
        }
        editHour.setOnClickListener { selectExactRadio() }

        editMinute.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectExactRadio() else updateHoursPreview()
        }
        editMinute.setOnClickListener { selectExactRadio() }

        editTitle.setOnFocusChangeListener { _, _ -> updateHoursPreview() }
        editTitle.setOnClickListener { updateHoursPreview() }

        editText.setOnFocusChangeListener { _, _ -> updateHoursPreview() }
        editText.setOnClickListener { updateHoursPreview() }

        // RECALCULATE ON TEXT MODIFICATIONS
        editHour.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateHoursPreview()
                if (s?.length == 2) {
                    editMinute.requestFocus()
                    editMinute.selectAll()
                }
            }
        })

        editInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateHoursPreview()
            }
        })

        editMinute.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateHoursPreview()
            }
        })

        editTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateHoursPreview()
            }
        })

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateHoursPreview()
            }
        })

        // RECALCULATE ON TOGGLES AND CHECKS
        dayCheckBoxes.forEach { chk ->
            chk.setOnCheckedChangeListener { _, _ -> updateHoursPreview() }
        }

        rbInterval.setOnCheckedChangeListener { _, isChecked -> if (isChecked) updateHoursPreview() }
        rbExact.setOnCheckedChangeListener { _, isChecked -> if (isChecked) updateHoursPreview() }

        chkSelectAll.setOnCheckedChangeListener { _, isChecked ->
            dayCheckBoxes.forEach { it.isChecked = isChecked }
            updateHoursPreview()
        }

        // HIDE KEYBOARD AND RECALCULATE ON TOUCH OUTSIDE
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard(view.findFocus())
                updateHoursPreview()
            }
            false
        }

        updateHoursPreview()

        builder.setView(view)
            .setTitle(if (existingItem == null) "Add Notification" else "Edit Notification")
            .setPositiveButton("Save") { _, _ ->
                target.isInterval = rbInterval.isChecked
                target.intervalMinutes = editInterval.text.toString().toIntOrNull() ?: 60
                target.exactHour = editHour.text.toString().toIntOrNull() ?: 12
                target.exactMinute = editMinute.text.toString().toIntOrNull() ?: 0
                target.title = editTitle.text.toString()
                target.text = editText.text.toString().ifBlank { "Notification" }
                target.lastTriggeredMillis = System.currentTimeMillis()

                target.days.clear()
                dayMap.forEach { (dayCode, chk) ->
                    if (chk.isChecked) target.days.add(dayCode)
                }

                if (target.days.isEmpty()) {
                    val msg = if (target.isInterval) {
                        "Notice: Notification will run ONCE in ${target.intervalMinutes}m and then set to Never."
                    } else {
                        "Notice: Notification will run ONCE at ${String.format(Locale.US, "%02d:%02d", target.exactHour, target.exactMinute)} and then set to Never."
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }

                if (existingItem == null) {
                    notificationsList.add(target)
                }
                saveData()
            }
            .setNegativeButton("Cancel", null)

        if (existingItem != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                notificationsList.remove(existingItem)
                saveData()
            }
        }

        val dialog = builder.create()
        dialog.show()

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}