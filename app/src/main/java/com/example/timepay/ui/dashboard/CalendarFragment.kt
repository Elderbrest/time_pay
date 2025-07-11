package com.example.timepay.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.timepay.R
import com.example.timepay.models.CalendarDayInfo
import com.example.timepay.repository.CalendarDayRepository
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.core.DayPosition
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private var currentMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null
    private val calendarRepository = CalendarDayRepository()
    private var loadedDays: Map<String, CalendarDayInfo> = emptyMap()

    private fun updateMonthText(monthText: TextView, month: YearMonth) {
        val context = monthText.context
        val monthName = month.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
        monthText.text = context.getString(R.string.month_year_format, monthName, month.year)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prevButton = view.findViewById<ImageView>(R.id.prevMonthButton)
        val nextButton = view.findViewById<ImageView>(R.id.nextMonthButton)
        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        val monthTitle = view.findViewById<TextView>(R.id.monthText)
        val notesText = view.findViewById<TextView>(R.id.notesText)

        calendarView.setup(
            startMonth = currentMonth.minusMonths(10),
            endMonth = currentMonth.plusMonths(10),
            firstDayOfWeek = DayOfWeek.MONDAY
        )
        calendarView.scrollToMonth(currentMonth)
        updateMonthText(monthTitle, currentMonth)

        class DayViewContainer(view: View) : ViewContainer(view) {
            lateinit var day: CalendarDay
            val textView: TextView = view.findViewById(R.id.calendarDayText)

            init {
                view.setOnClickListener {
                    if (day.position == DayPosition.MonthDate) {
                        selectedDate = day.date
                        calendarView.notifyCalendarChanged()
                        notesText.visibility = View.VISIBLE
                        notesText.text = "Заметки для ${day.date}"
                    }
                }
            }
        }

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            fun updateDayView(container: DayViewContainer, day: CalendarDay) {
                val dateKey = day.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val dayInfo = loadedDays[dateKey]

                if (dayInfo != null && day.position == DayPosition.MonthDate) {
                    val dotDrawable = when (dayInfo.status) {
                        "planned" -> ContextCompat.getDrawable(requireContext(), R.drawable.dot_working)
                        "done" -> ContextCompat.getDrawable(requireContext(), R.drawable.dot_done)
                        else -> null
                    }

                    container.textView.setCompoundDrawablesWithIntrinsicBounds(
                        null, null, null, dotDrawable
                    )
                } else {
                    container.textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                }
            }

            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.text = day.date.dayOfMonth.toString()
                container.day = day

                when {
                    day.date == selectedDate -> {
                        container.textView.setTextColor(resources.getColor(R.color.black, null))
                        container.textView.setBackgroundResource(R.drawable.bg_selected_day)
                    }
                    day.date == LocalDate.now() -> {
                        container.textView.setTextColor(resources.getColor(R.color.black, null))
                        container.textView.setTypeface(null, android.graphics.Typeface.BOLD)
                        container.textView.background = null
                    }
                    day.position == DayPosition.MonthDate -> {
                        container.textView.setTextColor(resources.getColor(R.color.calendar_day_active, null))
                        container.textView.background = null
                    }
                    else -> {
                        container.textView.setTextColor(resources.getColor(R.color.calendar_day_inactive, null))
                        container.textView.background = null
                    }
                }

                updateDayView(container, day)
            }
        }

        calendarView.monthScrollListener = {
            currentMonth = it.yearMonth
            updateMonthText(monthTitle, currentMonth)

            lifecycleScope.launch {
                val days = calendarRepository.getCurrentMonthDates(currentMonth)
                loadedDays = days
                Log.d("CalendarFragment", "Загружены дни: $days")
                calendarView.notifyCalendarChanged()
            }
        }

        Log.d("CalendarFragment", "Общие дни: $loadedDays")

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
    }
}
