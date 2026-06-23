package com.el.timepay.ui.dashboard

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.el.timepay.R
import com.el.timepay.models.CalendarDayInfo
import com.el.timepay.repository.CalendarDayRepository
import com.el.timepay.repository.UserRepository
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private var currentMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null
    private val calendarRepository = CalendarDayRepository()
    private val userRepository = UserRepository()
    private var loadedDays: Map<String, CalendarDayInfo> = emptyMap()
    private var salaryRate: Double = 0.0

    private val keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private lateinit var calendarView: CalendarView
    private lateinit var dayActionButton: ExtendedFloatingActionButton

    private lateinit var monthSummaryHoursText: TextView
    private lateinit var monthSummaryEarningsText: TextView

    private lateinit var dayDetailCard: MaterialCardView
    private lateinit var dayDetailTitle: TextView
    private lateinit var dayDetailStatusChip: TextView
    private lateinit var dayDetailWorked: TextView
    private lateinit var dayDetailEarningsRow: LinearLayout
    private lateinit var dayDetailEarningsValue: TextView
    private lateinit var dayDetailGhost: TextView
    private lateinit var dayDetailNote: TextView
    private lateinit var dayDetailMore: TextView

    // region formatting helpers (match HomeFragment)

    /** Formats hours without a trailing ".0" (8.0 -> "8", 8.5 -> "8.5"), locale-stable. */
    private fun formatHours(hours: Double): String =
        if (hours % 1.0 == 0.0) hours.toInt().toString()
        else String.format(Locale.US, "%.1f", hours)

    /** "PLN 920" / "PLN 920.50", locale-stable. */
    private fun formatEarnings(earnings: Double): String {
        val code = getString(R.string.currency_code)
        return if (earnings % 1.0 == 0.0) "$code ${earnings.toInt()}"
        else "$code " + String.format(Locale.US, "%.2f", earnings)
    }

    private fun dayInfoFor(date: LocalDate?): CalendarDayInfo? =
        date?.let { loadedDays[it.format(keyFormatter)] }

    // endregion

    private fun updateMonthText(monthText: TextView, month: YearMonth) {
        val context = monthText.context
        val monthName = month.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
        monthText.text = context.getString(R.string.month_year_format, monthName, month.year)
    }

    private fun updateMonthSummary() {
        val monthLabel = currentMonth.month.name
        val hours = loadedDays.values
            .filter { it.status == "done" }
            .sumOf { it.hoursWorked ?: 0.0 }

        monthSummaryHoursText.text = getString(
            R.string.calendar_summary_hours,
            monthLabel,
            formatHours(hours)
        )

        if (salaryRate > 0.0 && hours > 0.0) {
            monthSummaryEarningsText.text = "· " + formatEarnings(hours * salaryRate)
            monthSummaryEarningsText.visibility = View.VISIBLE
        } else {
            monthSummaryEarningsText.visibility = View.GONE
        }
    }

    private fun updateActionButton(date: LocalDate?) {
        if (date == null) {
            dayActionButton.visibility = View.GONE
            return
        }
        dayActionButton.visibility = View.VISIBLE

        when (dayInfoFor(date)?.status) {
            "working" -> {
                dayActionButton.text = getString(R.string.calendar_action_mark_done)
                dayActionButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_done)
            }
            "done" -> {
                dayActionButton.text = getString(R.string.calendar_action_edit)
                dayActionButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit)
            }
            else -> {
                dayActionButton.text = getString(R.string.calendar_action_add)
                dayActionButton.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add)
            }
        }
    }

    /** Primary action for the contextual FAB, based on the selected day's state. */
    private fun onActionButtonClicked() {
        val date = selectedDate
        if (date == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.calendar_select_day_first),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        when (dayInfoFor(date)?.status) {
            "working" -> showCompleteWorkDayDialog(date)
            "done" -> showMoreActionsSheet(date)
            else -> showConfirmAddWorkDayDialog(date)
        }
    }

    /** Secondary actions (edit hours / edit note / remove) reachable from the detail card. */
    private fun showMoreActionsSheet(date: LocalDate) {
        val isDone = dayInfoFor(date)?.status == "done"
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (isDone) {
            labels.add(getString(R.string.calendar_more_edit_hours))
            actions.add { showCompleteWorkDayDialog(date) }
        }
        labels.add(getString(R.string.calendar_more_edit_note))
        actions.add { showEditNoteDialog(date) }
        labels.add(getString(R.string.calendar_more_remove))
        actions.add { confirmRemoveWorkDay(date) }

        AlertDialog.Builder(requireContext())
            .setItems(labels.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    private fun confirmRemoveWorkDay(date: LocalDate) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.calendar_remove_work_title))
            .setMessage(getString(R.string.calendar_remove_work_message))
            .setPositiveButton(getString(R.string.remove_button)) { _, _ ->
                removeWorkDay(date)
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun showConfirmAddWorkDayDialog(date: LocalDate) {
        val formatted = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val plainText = getString(R.string.calendar_confirm_add_message, formatted)
        val spannable = SpannableString(plainText)
        val startFormattedIndex = plainText.indexOf(formatted)
        val endFormattedIndex = startFormattedIndex + formatted.length

        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            startFormattedIndex,
            endFormattedIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.calendar_confirm_add_title))
            .setMessage(spannable)
            .setPositiveButton(getString(R.string.save_button)) { _, _ ->
                saveWorkDay(date)
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun showSpinnerTimePicker(
        title: String,
        initialTime: LocalTime,
        onTimeSelected: (LocalTime) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)

        timePicker.setIs24HourView(true)
        timePicker.hour = initialTime.hour
        timePicker.minute = initialTime.minute

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_button)) { _, _ ->
                val selectedTime = LocalTime.of(timePicker.hour, timePicker.minute)
                onTimeSelected(selectedTime)
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun showCompleteWorkDayDialog(date: LocalDate) {
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_work_hours, null)

        val startTimeText = dialogView.findViewById<TextView>(R.id.startTimeText)
        val endTimeText = dialogView.findViewById<TextView>(R.id.endTimeText)

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        var startTime = LocalTime.of(9, 0)
        var endTime = LocalTime.of(18, 0)

        startTimeText.text = getString(
            R.string.calendar_start_time,
            startTime.format(timeFormatter)
        )
        endTimeText.text = getString(
            R.string.calendar_end_time,
            endTime.format(timeFormatter)
        )

        startTimeText.setOnClickListener {
            showSpinnerTimePicker(
                title = getString(R.string.calendar_time_start),
                initialTime = startTime
            ) { selected ->
                startTime = selected
                startTimeText.text = getString(
                    R.string.calendar_start_time,
                    startTime.format(timeFormatter)
                )
            }
        }

        endTimeText.setOnClickListener {
            showSpinnerTimePicker(
                title = getString(R.string.calendar_time_end),
                initialTime = endTime
            ) { selected ->
                endTime = selected
                endTimeText.text = getString(
                    R.string.calendar_end_time,
                    endTime.format(timeFormatter)
                )
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.calendar_mark_done_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_button)) { _, _ ->
                saveDoneWorkDay(date, startTime, endTime)
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private suspend fun reloadCalendar(): Map<String, CalendarDayInfo> {
        val days = calendarRepository.getCurrentMonthDates(currentMonth)
        loadedDays = days
        calendarView.notifyCalendarChanged()
        updateMonthSummary()
        return days
    }

    private fun saveDoneWorkDay(date: LocalDate, startTime: LocalTime, endTime: LocalTime) {
        val hoursWorked = java.time.Duration.between(startTime, endTime).toMinutes() / 60.0

        val updates = mapOf(
            "status" to "done",
            "startTime" to startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            "endTime" to endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            "hoursWorked" to hoursWorked
        )

        viewLifecycleOwner.lifecycleScope.launch {
            calendarRepository.updateDayInfo(
                date.format(keyFormatter),
                updates
            )

            reloadCalendar()
            updateActionButton(date)
            updateDayDetail(date)
            Toast.makeText(requireContext(), getString(R.string.calendar_day_marked_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveWorkDay(date: LocalDate) {
        val info = CalendarDayInfo(status = "working")
        viewLifecycleOwner.lifecycleScope.launch {
            calendarRepository.saveDayInfo(
                date.format(keyFormatter),
                info
            )

            reloadCalendar()
            updateActionButton(date)
            updateDayDetail(date)
            Toast.makeText(requireContext(), getString(R.string.calendar_day_created), Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeWorkDay(date: LocalDate) {
        viewLifecycleOwner.lifecycleScope.launch {
            calendarRepository.deleteDayInfo(
                date.format(keyFormatter)
            )

            reloadCalendar()
            updateActionButton(date)
            updateDayDetail(date)

            Toast.makeText(requireContext(), getString(R.string.calendar_day_removed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDayDetail(date: LocalDate?) {
        if (date == null) {
            dayDetailCard.visibility = View.GONE
            return
        }
        dayDetailCard.visibility = View.VISIBLE

        val titleFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
        dayDetailTitle.text = date.format(titleFormatter)

        val dayInfo = dayInfoFor(date)
        val note = dayInfo?.note
        val hasNote = !note.isNullOrBlank()

        // Reset optional rows.
        dayDetailWorked.visibility = View.GONE
        dayDetailEarningsRow.visibility = View.GONE
        dayDetailGhost.visibility = View.GONE
        dayDetailNote.visibility = View.GONE
        dayDetailMore.visibility = View.GONE
        dayDetailStatusChip.visibility = View.GONE

        when (dayInfo?.status) {
            "done" -> {
                dayDetailStatusChip.visibility = View.VISIBLE
                dayDetailStatusChip.text = getString(R.string.calendar_status_done)
                dayDetailStatusChip.setBackgroundResource(R.drawable.bg_chip_done)
                dayDetailStatusChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_on_primary_container)
                )

                val hoursValue = dayInfo.hoursWorked ?: 0.0
                val start = dayInfo.startTime ?: "-"
                val end = dayInfo.endTime ?: "-"
                val hoursLabel = getString(R.string.calendar_hours_cell, formatHours(hoursValue))
                val workedText = getString(R.string.calendar_worked_summary, hoursLabel, start, end)
                val spannable = SpannableString(workedText)
                val hoursStart = workedText.indexOf(hoursLabel)
                if (hoursStart >= 0) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        hoursStart,
                        hoursStart + hoursLabel.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                dayDetailWorked.text = spannable
                dayDetailWorked.visibility = View.VISIBLE

                if (salaryRate > 0.0) {
                    dayDetailEarningsValue.text = formatEarnings(hoursValue * salaryRate)
                    dayDetailEarningsRow.visibility = View.VISIBLE
                }

                dayDetailMore.visibility = View.VISIBLE
            }
            "working" -> {
                dayDetailStatusChip.visibility = View.VISIBLE
                dayDetailStatusChip.text = getString(R.string.calendar_status_planned)
                dayDetailStatusChip.setBackgroundResource(R.drawable.bg_chip_planned)
                dayDetailStatusChip.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.md_on_secondary_container)
                )

                dayDetailGhost.text = getString(R.string.calendar_planned_incomplete)
                dayDetailGhost.visibility = View.VISIBLE
                dayDetailMore.visibility = View.VISIBLE
            }
            else -> {
                dayDetailGhost.text = getString(R.string.calendar_empty_day)
                dayDetailGhost.visibility = View.VISIBLE
            }
        }

        if (hasNote) {
            dayDetailNote.text = note
            dayDetailNote.visibility = View.VISIBLE
        }
    }

    private fun saveNoteForDay(date: LocalDate, note: String) {
        val updates = mapOf(
            "note" to note
        )

        viewLifecycleOwner.lifecycleScope.launch {
            calendarRepository.updateDayInfo(
                date.format(keyFormatter),
                updates
            )
            reloadCalendar()
            updateDayDetail(date)
            Toast.makeText(requireContext(), getString(R.string.calendar_note_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditNoteDialog(date: LocalDate) {
        val existingNote = dayInfoFor(date)?.note ?: ""

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val noteEditText = dialogView.findViewById<EditText>(R.id.noteEditText)
        noteEditText.setText(existingNote)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.calendar_note_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_button)) { _, _ ->
                val newNote = noteEditText.text.toString().trim()
                saveNoteForDay(date, newNote)
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prevButton = view.findViewById<ImageView>(R.id.prevMonthButton)
        val nextButton = view.findViewById<ImageView>(R.id.nextMonthButton)
        val monthTitle = view.findViewById<TextView>(R.id.monthText)

        calendarView = view.findViewById(R.id.calendarView)
        dayActionButton = view.findViewById(R.id.dayActionButton)

        monthSummaryHoursText = view.findViewById(R.id.monthSummaryHoursText)
        monthSummaryEarningsText = view.findViewById(R.id.monthSummaryEarningsText)

        dayDetailCard = view.findViewById(R.id.dayDetailCard)
        dayDetailTitle = view.findViewById(R.id.dayDetailTitle)
        dayDetailStatusChip = view.findViewById(R.id.dayDetailStatusChip)
        dayDetailWorked = view.findViewById(R.id.dayDetailWorked)
        dayDetailEarningsRow = view.findViewById(R.id.dayDetailEarningsRow)
        dayDetailEarningsValue = view.findViewById(R.id.dayDetailEarningsValue)
        dayDetailGhost = view.findViewById(R.id.dayDetailGhost)
        dayDetailNote = view.findViewById(R.id.dayDetailNote)
        dayDetailMore = view.findViewById(R.id.dayDetailMore)

        calendarView.setup(
            startMonth = currentMonth.minusMonths(10),
            endMonth = currentMonth.plusMonths(10),
            firstDayOfWeek = DayOfWeek.MONDAY
        )
        calendarView.scrollToMonth(currentMonth)
        updateMonthText(monthTitle, currentMonth)

        selectedDate = LocalDate.now()
        updateActionButton(selectedDate)

        // Fetch the salary rate so the month summary + daily earnings can be shown.
        viewLifecycleOwner.lifecycleScope.launch {
            salaryRate = userRepository.getCurrentUserOnce()?.salaryRate ?: 0.0
            updateMonthSummary()
            updateDayDetail(selectedDate)
        }

        calendarView.notifyCalendarChanged()

        class DayViewContainer(view: View) : ViewContainer(view) {
            lateinit var day: CalendarDay
            val container: View = view.findViewById(R.id.dayCircle)
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            val hoursView: TextView = view.findViewById(R.id.calendarHoursText)
            val dotView: View = view.findViewById(R.id.dotView)

            init {
                view.setOnClickListener {
                    if (day.position == DayPosition.MonthDate) {
                        selectedDate = day.date
                        calendarView.notifyCalendarChanged()
                        updateActionButton(selectedDate)
                        updateDayDetail(selectedDate)
                    }
                }
            }
        }

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.day = day
                container.textView.text = day.date.dayOfMonth.toString()
                container.hoursView.visibility = View.GONE
                container.dotView.visibility = View.GONE
                container.container.background = null
                container.textView.alpha = 1f
                container.textView.setTypeface(null, Typeface.NORMAL)

                val today = LocalDate.now()
                val isToday = day.date == today
                val isSelected = day.date == selectedDate
                val dayInfo = if (day.position == DayPosition.MonthDate) {
                    loadedDays[day.date.format(keyFormatter)]
                } else {
                    null
                }

                if (day.position != DayPosition.MonthDate) {
                    // Out-of-month days: faded plain number.
                    container.textView.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.calendar_day_inactive)
                    )
                    container.textView.alpha = 0.38f
                    return
                }

                when (dayInfo?.status) {
                    "done" -> {
                        container.container.setBackgroundResource(R.drawable.bg_day_done)
                        container.textView.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.white)
                        )
                        container.textView.setTypeface(null, Typeface.BOLD)
                        val hours = dayInfo.hoursWorked ?: 0.0
                        if (hours > 0.0) {
                            container.hoursView.text =
                                getString(R.string.calendar_hours_cell, formatHours(hours))
                            container.hoursView.visibility = View.VISIBLE
                        }
                    }
                    "working" -> {
                        container.container.setBackgroundResource(R.drawable.bg_day_planned)
                        container.textView.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.md_secondary)
                        )
                    }
                    else -> {
                        if (isToday) {
                            container.container.setBackgroundResource(R.drawable.bg_day_today)
                        }
                        container.textView.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.calendar_day_active)
                        )
                    }
                }

                // Selected ring takes visual priority over the today fill (but not over
                // the done/planned fills, which carry status meaning).
                if (isSelected && dayInfo?.status != "done") {
                    container.container.setBackgroundResource(R.drawable.bg_selected_day)
                    if (dayInfo?.status == "working") {
                        container.textView.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.md_secondary)
                        )
                    }
                }

                if (isToday) {
                    container.textView.setTypeface(null, Typeface.BOLD)
                    container.dotView.visibility = View.VISIBLE
                }
            }
        }

        calendarView.monthScrollListener = {
            currentMonth = it.yearMonth
            updateMonthText(monthTitle, currentMonth)

            // Drop a selection that belongs to a now off-screen month so the FAB and
            // detail card can't act on a day the user can no longer see.
            if (selectedDate?.let { date -> YearMonth.from(date) != currentMonth } == true) {
                selectedDate = null
            }

            viewLifecycleOwner.lifecycleScope.launch {
                val days = calendarRepository.getCurrentMonthDates(currentMonth)
                loadedDays = days
                calendarView.notifyCalendarChanged()
                updateMonthSummary()
                updateActionButton(selectedDate)
                updateDayDetail(selectedDate)
            }
        }

        prevButton.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            calendarView.scrollToMonth(currentMonth)
            updateMonthText(monthTitle, currentMonth)
        }

        nextButton.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            calendarView.scrollToMonth(currentMonth)
            updateMonthText(monthTitle, currentMonth)
        }

        dayActionButton.setOnClickListener { onActionButtonClicked() }

        dayDetailMore.setOnClickListener {
            selectedDate?.let { date -> showMoreActionsSheet(date) }
        }
    }
}
