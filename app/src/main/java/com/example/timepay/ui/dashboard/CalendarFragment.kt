package com.example.timepay.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.example.timepay.R
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.core.DayPosition
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.LocalDate

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private var currentMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null

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
                    }
                }
            }
        }

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

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
            }
        }

        calendarView.monthScrollListener = {
            currentMonth = it.yearMonth
            updateMonthText(monthTitle, currentMonth)
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
    }
}
