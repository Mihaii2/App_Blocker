package com.example.app_blocker

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment

class CountdownDialogFragment : DialogFragment() {

    private lateinit var countdownText: TextView
    private lateinit var confirmButton: Button
    private lateinit var countdownTimer: CountDownTimer
    private var listener: CountdownDialogListener? = null
    private var countdownMessage: String? = null
    private var buttonText: String? = null
    private var actionTag: String? = null

    interface CountdownDialogListener {
        fun onDialogDismissed()
        fun onActionConfirmed()
    }

    companion object {
        private const val ARG_COUNTDOWN_MESSAGE = "countdown_message"
        private const val ARG_BUTTON_TEXT = "button_text"
        private const val ARG_ACTION_TAG = "action_tag"

        fun newInstance(countdownMessage: String, buttonText: String, actionTag: String? = null): CountdownDialogFragment {
            val fragment = CountdownDialogFragment()
            val args = Bundle()
            args.putString(ARG_COUNTDOWN_MESSAGE, countdownMessage)
            args.putString(ARG_BUTTON_TEXT, buttonText)
            args.putString(ARG_ACTION_TAG, actionTag)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? CountdownDialogListener ?: context as? CountdownDialogListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            countdownMessage = it.getString(ARG_COUNTDOWN_MESSAGE)
            buttonText = it.getString(ARG_BUTTON_TEXT)
            actionTag = it.getString(ARG_ACTION_TAG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_countdown_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        countdownText = view.findViewById(R.id.countdownText)
        confirmButton = view.findViewById(R.id.confirmButton)

        confirmButton.visibility = View.GONE
        confirmButton.text = buttonText ?: "Confirm"

        countdownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = "$countdownMessage\nTime remaining: ${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                countdownText.text = "You can now perform the action."
                confirmButton.visibility = View.VISIBLE
            }
        }.start()

        confirmButton.setOnClickListener {
            listener?.onActionConfirmed()
            dismiss()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onStop() {
        super.onStop()
        countdownTimer.cancel()
        listener?.onDialogDismissed()
    }

    fun getActionTag(): String? = actionTag
}