package com.el.timepay.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.el.timepay.R
import com.el.timepay.models.CalendarDayInfo
import com.el.timepay.repository.CalendarDayRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * One reusable bottom sheet for logging or planning a calendar day, shared by the
 * Calendar and Home screens. Owns the Firestore writes via [CalendarDayRepository];
 * communicates the outcome back through [setFragmentResult] under [requestKey].
 */
class LogHoursBottomSheet : BottomSheetDialogFragment() {

    private val calendarRepository = CalendarDayRepository()

    private val keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private lateinit var targetDate: LocalDate
    private var existingStatus: String? = null
    private var existingNote: String? = null
    private var salaryRate: Double = 0.0
    private var requestKey: String = DEFAULT_REQUEST_KEY

    /** Entry point for analytics: Home opens the sheet with planning disabled. */
    private var analyticsSource: String = com.el.timepay.util.Analytics.SOURCE_CALENDAR

    private var startT: LocalTime = LocalTime.of(9, 0)
    private var endT: LocalTime = LocalTime.of(18, 0)
    private var hours: Double = 0.0
    private var noteVisible: Boolean = false

    // Views
    private lateinit var dateTitle: TextView
    private lateinit var statusChip: TextView
    private lateinit var intentToggle: MaterialButtonToggleGroup
    private lateinit var btnLog: MaterialButton
    private lateinit var btnPlan: MaterialButton
    private lateinit var timesBlock: LinearLayout
    private lateinit var startBtn: MaterialButton
    private lateinit var endBtn: MaterialButton
    private lateinit var planCaption: TextView
    private lateinit var earningsPreview: TextView
    private lateinit var noteInput: TextInputLayout
    private lateinit var noteEdit: TextInputEditText
    private lateinit var editNoteBtn: MaterialButton
    private lateinit var primaryBtn: MaterialButton
    private lateinit var removeBtn: MaterialButton

    private var removeArmed = false

    /** True between onViewCreated and onDestroyView; guards post-write UI touches. */
    private var _viewAlive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_log_hours, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        targetDate = LocalDate.ofEpochDay(args.getLong(ARG_DATE))
        existingStatus = args.getString(ARG_STATUS)
        existingNote = args.getString(ARG_NOTE)
        salaryRate = args.getDouble(ARG_RATE)
        requestKey = args.getString(ARG_REQUEST_KEY) ?: DEFAULT_REQUEST_KEY

        // Resolve initial times: an existing day loads its own saved times; a NEW
        // day always opens at a clean 09:00–18:00 (no carry-over from the last shift).
        val existingStart = args.getString(ARG_START)
        val existingEnd = args.getString(ARG_END)
        startT = parseTimeOr(existingStart, LocalTime.of(9, 0))
        endT = parseTimeOr(existingEnd, LocalTime.of(18, 0))

        bindViews(view)

        // Header.
        dateTitle.text = targetDate.format(
            DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
        )
        renderStatusChip()

        // Toggle. Default selection is always "Log hours". When the caller disallows
        // planning (e.g. Home's "Log today's hours"), hide it and force Log mode.
        btnLog.setOnClickListener { applyMode(logMode = true) }
        btnPlan.setOnClickListener { applyMode(logMode = false) }
        intentToggle.check(R.id.sheetBtnLog)
        val allowPlan = args.getBoolean(ARG_ALLOW_PLAN, true)
        analyticsSource = if (allowPlan) {
            com.el.timepay.util.Analytics.SOURCE_CALENDAR
        } else {
            com.el.timepay.util.Analytics.SOURCE_HOME
        }
        if (!allowPlan) {
            intentToggle.visibility = View.GONE
        }

        // Time pickers.
        startBtn.setOnClickListener { pickTime(isStart = true) }
        endBtn.setOnClickListener { pickTime(isStart = false) }

        // Note prefill + edit-note toggle (only for existing days).
        noteEdit.setText(existingNote ?: "")
        editNoteBtn.setOnClickListener { toggleNote() }

        // Remove (existing days only), confirm-by-second-tap.
        removeBtn.setOnClickListener { onRemoveTapped() }

        primaryBtn.setOnClickListener { onPrimaryClicked() }

        recomputeHours()
        applyMode(logMode = true)
        _viewAlive = true
    }

    override fun onDestroyView() {
        _viewAlive = false
        super.onDestroyView()
    }

    private fun bindViews(view: View) {
        dateTitle = view.findViewById(R.id.sheetDateTitle)
        statusChip = view.findViewById(R.id.sheetStatusChip)
        intentToggle = view.findViewById(R.id.sheetIntentToggle)
        btnLog = view.findViewById(R.id.sheetBtnLog)
        btnPlan = view.findViewById(R.id.sheetBtnPlan)
        timesBlock = view.findViewById(R.id.sheetTimesBlock)
        startBtn = view.findViewById(R.id.sheetStartBtn)
        endBtn = view.findViewById(R.id.sheetEndBtn)
        planCaption = view.findViewById(R.id.sheetPlanCaption)
        earningsPreview = view.findViewById(R.id.sheetEarningsPreview)
        noteInput = view.findViewById(R.id.sheetNoteInput)
        noteEdit = view.findViewById(R.id.sheetNoteEdit)
        editNoteBtn = view.findViewById(R.id.sheetEditNoteBtn)
        primaryBtn = view.findViewById(R.id.sheetPrimaryBtn)
        removeBtn = view.findViewById(R.id.sheetRemoveBtn)
    }

    private fun renderStatusChip() {
        when (existingStatus) {
            "done" -> {
                statusChip.visibility = View.VISIBLE
                statusChip.text = getString(R.string.calendar_status_done)
                statusChip.setBackgroundResource(R.drawable.bg_chip_done)
                statusChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_on_primary_container)
                )
            }
            "working" -> {
                statusChip.visibility = View.VISIBLE
                statusChip.text = getString(R.string.calendar_status_planned)
                statusChip.setBackgroundResource(R.drawable.bg_chip_planned)
                statusChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_on_secondary_container)
                )
            }
            else -> statusChip.visibility = View.GONE
        }
        // Secondary actions only when editing an existing day.
        val editing = existingStatus != null
        editNoteBtn.visibility = if (editing) View.VISIBLE else View.GONE
        removeBtn.visibility = if (editing) View.VISIBLE else View.GONE
    }

    private fun isLogMode(): Boolean = intentToggle.checkedButtonId == R.id.sheetBtnLog

    private fun applyMode(logMode: Boolean) {
        // Cancel a pending remove confirmation if the user switches intent.
        if (removeArmed) {
            removeArmed = false
            removeBtn.text = getString(R.string.log_sheet_remove)
        }
        timesBlock.visibility = if (logMode) View.VISIBLE else View.GONE
        if (logMode) {
            updateTimeButtons()
            updateEarningsPreview()
            planCaption.visibility = View.GONE
            earningsPreview.visibility = View.VISIBLE
        } else {
            planCaption.visibility = View.VISIBLE
            earningsPreview.visibility = View.GONE
        }
        updatePrimaryLabel()
    }

    /**
     * Material 24h time picker. Opens in keyboard-entry mode so the user can TYPE
     * the time; the built-in toggle switches to the clock dial for scrolling.
     */
    private fun pickTime(isStart: Boolean) {
        val initial = if (isStart) startT else endT
        val title = getString(
            if (isStart) R.string.calendar_time_start else R.string.calendar_time_end
        )

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .setHour(initial.hour)
            .setMinute(initial.minute)
            .setTitleText(title)
            .build()

        picker.addOnPositiveButtonClickListener {
            val picked = LocalTime.of(picker.hour, picker.minute)
            if (isStart) startT = picked else endT = picked
            recomputeHours()
            updateTimeButtons()
            updateEarningsPreview()
            updatePrimaryLabel()
        }
        picker.show(childFragmentManager, "time_picker")
    }

    private fun updateTimeButtons() {
        startBtn.text = getString(R.string.log_sheet_start, startT.format(timeFormatter))
        endBtn.text = getString(R.string.log_sheet_end, endT.format(timeFormatter))
    }

    /** Overnight-safe duration: when end <= start, roll into the next day, capped at 24h. */
    private fun recomputeHours() {
        val raw = if (endT <= startT) {
            Duration.between(startT, endT).plusHours(24)
        } else {
            Duration.between(startT, endT)
        }
        hours = (raw.toMinutes() / 60.0).coerceAtMost(24.0)
    }

    private fun updateEarningsPreview() {
        if (salaryRate > 0.0) {
            earningsPreview.text = getString(
                R.string.log_sheet_earnings_preview,
                formatEarnings(hours * salaryRate)
            )
            earningsPreview.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.md_on_secondary_container)
            )
        } else {
            earningsPreview.text = getString(R.string.log_sheet_no_rate)
            earningsPreview.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(
                    earningsPreview,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
                )
            )
        }
    }

    private fun updatePrimaryLabel() {
        primaryBtn.text = when {
            !isLogMode() -> getString(R.string.log_sheet_plan_button)
            existingStatus == "done" -> getString(R.string.log_sheet_save_button)
            else -> getString(R.string.log_sheet_log_button, formatHours(hours))
        }
    }

    private fun toggleNote() {
        noteVisible = !noteVisible
        noteInput.visibility = if (noteVisible) View.VISIBLE else View.GONE
    }

    private fun onRemoveTapped() {
        if (!removeArmed) {
            removeArmed = true
            removeBtn.text = getString(R.string.log_sheet_remove_confirm)
            removeBtn.postDelayed({
                if (isAdded) {
                    removeArmed = false
                    removeBtn.text = getString(R.string.log_sheet_remove)
                }
            }, REMOVE_CONFIRM_WINDOW_MS)
            return
        }
        performRemove()
    }

    private fun performRemove() {
        val key = targetDate.format(keyFormatter)
        // Fragment-scoped (not viewLifecycleOwner): the write + result delivery must
        // survive the sheet's view being torn down mid-write.
        setActionsEnabled(false)
        lifecycleScope.launch {
            try {
                calendarRepository.deleteDayInfo(key)
                com.el.timepay.util.Analytics.logDayRemoved()
                deliverResult(ACTION_REMOVED, 0.0)
                dismiss()
            } catch (e: Exception) {
                onWriteFailed()
            }
        }
    }

    private fun onPrimaryClicked() {
        val key = targetDate.format(keyFormatter)
        val note = noteEdit.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val logMode = isLogMode()
        // Snapshot the values that drive the write so a recreated view can't change them.
        val startStr = startT.format(timeFormatter)
        val endStr = endT.format(timeFormatter)
        val loggedHours = hours

        setActionsEnabled(false)
        // Fragment-scoped so the write + result still land if the sheet view dies mid-write.
        lifecycleScope.launch {
            try {
                if (logMode) {
                    calendarRepository.updateDayInfo(
                        key,
                        mapOf(
                            "status" to "done",
                            "startTime" to startStr,
                            "endTime" to endStr,
                            "hoursWorked" to loggedHours,
                            "note" to note
                        )
                    )
                    com.el.timepay.util.Analytics.logHoursLogged(analyticsSource, loggedHours)
                    deliverResult(ACTION_LOGGED, loggedHours)
                } else {
                    calendarRepository.saveDayInfo(
                        key,
                        CalendarDayInfo(status = "working", note = note)
                    )
                    com.el.timepay.util.Analytics.logDayPlanned(analyticsSource)
                    deliverResult(ACTION_PLANNED, 0.0)
                }
                dismiss()
            } catch (e: Exception) {
                onWriteFailed()
            }
        }
    }

    /** Re-enable the buttons and surface the failure; guarded so a dead view is a no-op. */
    private fun onWriteFailed() {
        if (!isAdded) return
        setActionsEnabled(true)
        Toast.makeText(requireContext(), R.string.log_sheet_save_failed, Toast.LENGTH_SHORT).show()
    }

    private fun setActionsEnabled(enabled: Boolean) {
        if (_viewAlive) {
            primaryBtn.isEnabled = enabled
            removeBtn.isEnabled = enabled
        }
    }

    private fun deliverResult(action: String, resultHours: Double) {
        setFragmentResult(
            requestKey,
            bundleOf(
                RESULT_DATE to targetDate.toEpochDay(),
                RESULT_ACTION to action,
                RESULT_HOURS to resultHours
            )
        )
    }

    // region formatting helpers (match CalendarFragment)

    /** Worked hours as H:MM (8.25 -> "8:15"), via the shared formatter. */
    private fun formatHours(hours: Double): String =
        com.el.timepay.util.TimeFormat.hoursToHm(hours)

    /** "PLN 920" / "PLN 920.50", locale-stable. */
    private fun formatEarnings(earnings: Double): String {
        val code = getString(R.string.currency_code)
        return if (earnings % 1.0 == 0.0) "$code ${earnings.toInt()}"
        else "$code " + String.format(Locale.US, "%.2f", earnings)
    }

    // endregion

    private fun parseTimeOr(value: String?, fallback: LocalTime): LocalTime =
        value?.let {
            try {
                LocalTime.parse(it, timeFormatter)
            } catch (e: Exception) {
                fallback
            }
        } ?: fallback

    companion object {
        const val DEFAULT_REQUEST_KEY = "log_hours_result"

        const val RESULT_DATE = "date"
        const val RESULT_ACTION = "action"
        const val RESULT_HOURS = "hours"

        const val ACTION_LOGGED = "logged"
        const val ACTION_PLANNED = "planned"
        const val ACTION_REMOVED = "removed"

        private const val ARG_DATE = "arg_date"
        private const val ARG_STATUS = "arg_status"
        private const val ARG_START = "arg_start"
        private const val ARG_END = "arg_end"
        private const val ARG_NOTE = "arg_note"
        private const val ARG_RATE = "arg_rate"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_ALLOW_PLAN = "arg_allow_plan"

        private const val REMOVE_CONFIRM_WINDOW_MS = 3000L

        fun newInstance(
            targetDate: LocalDate,
            existingStatus: String?,
            existingStartTime: String?,
            existingEndTime: String?,
            existingNote: String?,
            salaryRate: Double,
            requestKey: String,
            allowPlan: Boolean = true
        ): LogHoursBottomSheet = LogHoursBottomSheet().apply {
            arguments = bundleOf(
                ARG_DATE to targetDate.toEpochDay(),
                ARG_STATUS to existingStatus,
                ARG_START to existingStartTime,
                ARG_END to existingEndTime,
                ARG_NOTE to existingNote,
                ARG_RATE to salaryRate,
                ARG_REQUEST_KEY to requestKey,
                ARG_ALLOW_PLAN to allowPlan
            )
        }
    }
}
