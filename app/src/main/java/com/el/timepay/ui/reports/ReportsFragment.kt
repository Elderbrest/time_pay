package com.el.timepay.ui.reports

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.el.timepay.R
import com.el.timepay.databinding.FragmentReportsBinding
import com.el.timepay.models.CalendarDayInfo
import com.el.timepay.repository.CalendarDayRepository
import com.el.timepay.repository.UserRepository
import com.el.timepay.ui.widget.BarDatum
import com.el.timepay.util.MonthlyReportPdf
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reports tab: a month picker drives an animated daily bar chart, a summary stats
 * card, and a tap-to-inspect day detail card. All earnings are derived from
 * hours × salaryRate and never persisted.
 */
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private var currentMonth = YearMonth.now()
    private val calendarRepository = CalendarDayRepository()
    private val userRepository = UserRepository()
    private var loadedDays: Map<String, CalendarDayInfo> = emptyMap()
    private var salaryRate: Double = 0.0
    private var loadJob: Job? = null

    private val keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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

    private fun updateMonthText(monthText: android.widget.TextView, month: YearMonth) {
        val context = monthText.context
        val monthName = month.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
        monthText.text = context.getString(R.string.month_year_format, monthName, month.year)
    }

    // endregion

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateMonthText(binding.monthText, currentMonth)

        binding.prevMonthButton.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            updateMonthText(binding.monthText, currentMonth)
            loadMonth()
        }
        binding.nextMonthButton.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            updateMonthText(binding.monthText, currentMonth)
            loadMonth()
        }

        binding.chart.onBarSelected = { day -> showDayDetail(day) }

        binding.exportPdfButton.setOnClickListener { openExportChooser() }

        // The export chooser sheet reports back which variant to build + share.
        childFragmentManager.setFragmentResultListener(
            ExportOptionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val includeEarnings = bundle.getBoolean(ExportOptionsBottomSheet.RESULT_INCLUDE_EARNINGS, true)
            exportPdf(includeEarnings)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            salaryRate = userRepository.getCurrentUserOnce()?.salaryRate ?: 0.0
            loadMonth()
        }
    }

    private fun loadMonth() {
        // Cancel any in-flight load so rapid month switching can't paint a stale month
        // (a slow earlier response resolving after a faster later one).
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            loadedDays = calendarRepository.getCurrentMonthDates(currentMonth)

            val bars = (1..currentMonth.lengthOfMonth()).map { day ->
                val info = loadedDays[currentMonth.atDay(day).format(keyFormatter)]
                val isDone = info?.status == "done"
                BarDatum(
                    day = day,
                    hours = if (isDone) info?.hoursWorked ?: 0.0 else 0.0,
                    isDone = isDone,
                )
            }
            val maxHours = maxOf(10.0, bars.maxOfOrNull { it.hours } ?: 0.0)

            binding.chart.setData(bars, maxHours)
            updateMonthText(binding.monthText, currentMonth)
            updateSummary()
            showDayDetail(null)
        }
    }

    private fun updateSummary() {
        val done = loadedDays.values.filter { it.status == "done" }
        val totalHours = done.sumOf { it.hoursWorked ?: 0.0 }
        val daysWorked = done.size
        val avg = if (daysWorked > 0) totalHours / daysWorked else 0.0
        val earnings = totalHours * salaryRate

        binding.statTotalHoursValue.text =
            getString(R.string.calendar_hours_cell, formatHours(totalHours))
        binding.statDaysWorkedValue.text = daysWorked.toString()
        binding.statAvgValue.text =
            getString(R.string.calendar_hours_cell, formatHours(avg))

        val monthLabel = currentMonth.month.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }

        if (salaryRate > 0.0) {
            binding.statEarnedValue.text = formatEarnings(earnings)
            binding.setRateHint.visibility = View.GONE
            if (totalHours > 0.0) {
                binding.monthSummaryEarningsText.text = "· " + formatEarnings(earnings)
                binding.monthSummaryEarningsText.visibility = View.VISIBLE
            } else {
                binding.monthSummaryEarningsText.visibility = View.GONE
            }
        } else {
            binding.statEarnedValue.text = "—"
            binding.setRateHint.visibility = View.VISIBLE
            binding.monthSummaryEarningsText.visibility = View.GONE
        }

        binding.monthSummaryHoursText.text = getString(
            R.string.calendar_summary_hours,
            monthLabel,
            formatHours(totalHours),
        )

        if (daysWorked == 0) {
            binding.emptyStateText.text = getString(R.string.reports_empty_month, monthLabel)
            binding.emptyStateText.visibility = View.VISIBLE
        } else {
            binding.emptyStateText.visibility = View.GONE
        }
    }

    private fun showDayDetail(day: Int?) {
        binding.chart.setSelectedDay(day)

        if (day == null) {
            binding.dayDetailCard.visibility = View.GONE
            return
        }
        binding.dayDetailCard.visibility = View.VISIBLE

        val date = currentMonth.atDay(day)
        binding.dayDetailTitle.text = date.format(
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH),
        )

        val info = loadedDays[date.format(keyFormatter)]

        binding.dayDetailWorked.visibility = View.GONE
        binding.dayDetailEarningsRow.visibility = View.GONE
        binding.dayDetailGhost.visibility = View.GONE

        if (info?.status == "done") {
            val hoursValue = info.hoursWorked ?: 0.0
            val hoursLabel = getString(R.string.calendar_hours_cell, formatHours(hoursValue))
            val start = info.startTime
            val end = info.endTime

            val workedText = if (start != null && end != null) {
                getString(R.string.calendar_worked_summary, hoursLabel, start, end)
            } else {
                hoursLabel
            }
            val spannable = SpannableString(workedText)
            val hoursStart = workedText.indexOf(hoursLabel)
            if (hoursStart >= 0) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    hoursStart,
                    hoursStart + hoursLabel.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            binding.dayDetailWorked.text = spannable
            binding.dayDetailWorked.visibility = View.VISIBLE

            if (salaryRate > 0.0) {
                binding.dayDetailEarningsValue.text = formatEarnings(hoursValue * salaryRate)
                binding.dayDetailEarningsRow.visibility = View.VISIBLE
            }
        } else {
            binding.dayDetailGhost.text = getString(R.string.reports_no_hours_logged)
            binding.dayDetailGhost.visibility = View.VISIBLE
        }
    }

    /** Guards against an empty month, then opens the full/hours-only chooser sheet. */
    private fun openExportChooser() {
        val hasDoneDays = loadedDays.values.any { it.status == "done" }
        if (!hasDoneDays) {
            val monthLabel = currentMonth.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
            Toast.makeText(
                requireContext(),
                getString(R.string.reports_export_empty, monthLabel),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        ExportOptionsBottomSheet.newInstance(hasRate = salaryRate > 0.0)
            .show(childFragmentManager, "export_options")
    }

    private fun exportPdf(includeEarnings: Boolean) {
        binding.exportPdfButton.isEnabled = false
        val month = currentMonth
        val days = loadedDays
        val rate = salaryRate

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    MonthlyReportPdf.build(
                        requireContext(),
                        month,
                        days,
                        rate,
                        getString(R.string.currency_code),
                        includeEarnings = includeEarnings,
                    )
                }
                if (_binding == null) return@launch
                sharePdf(file)
                binding.exportPdfButton.isEnabled = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(
                    requireContext(),
                    getString(R.string.reports_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                binding.exportPdfButton.isEnabled = true
            }
        }
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(intent, getString(R.string.reports_export_share_title)),
        )
        com.el.timepay.util.Analytics.logReportExported()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
