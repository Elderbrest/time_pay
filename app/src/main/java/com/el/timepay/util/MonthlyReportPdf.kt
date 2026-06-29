package com.el.timepay.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.el.timepay.models.CalendarDayInfo
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a one-page A4 monthly report to a PDF using the platform's built-in
 * [PdfDocument] — no external dependencies. Mirrors the Reports screen's
 * hours/earnings formatting (Locale.US) so the printout matches the UI.
 */
object MonthlyReportPdf {

    private const val PAGE_WIDTH = 595 // A4 portrait, 72dpi points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private const val GREEN = 0xFF2E5D4F.toInt()
    private const val AMBER = 0xFFC97B3D.toInt()
    private const val INK = 0xFF1A1C1B.toInt()
    private const val GREY = 0xFF707873.toInt()
    private const val HAIRLINE = 0xFFC0C8C4.toInt()

    /** Worked hours as H:MM with a small unit label (8.25 -> "8:15h"). */
    private fun formatHours(hours: Double): String = "${TimeFormat.hoursToHm(hours)}h"

    /** "PLN 160" / "PLN 160.50". */
    private fun formatEarnings(earnings: Double, currencyCode: String): String =
        if (earnings % 1.0 == 0.0) "$currencyCode ${earnings.toInt()}"
        else "$currencyCode " + String.format(Locale.US, "%.2f", earnings)

    /**
     * @param includeEarnings when false, renders an hours-only ("payment-agnostic")
     *   report: no rate, no per-day earnings column, no money totals, no currency.
     *   The remaining date/hours/time columns widen to fill the page and the file
     *   gets a `-hours` suffix so it never collides with the full payslip variant.
     */
    fun build(
        context: Context,
        month: YearMonth,
        days: Map<String, CalendarDayInfo>,
        salaryRate: Double,
        currencyCode: String,
        includeEarnings: Boolean = true,
    ): File {
        val keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val rowDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        // Worked (done) days only, sorted ascending by date.
        val worked: List<Pair<LocalDate, CalendarDayInfo>> = days
            .filter { it.value.status == "done" }
            .mapNotNull { (key, info) ->
                runCatching { LocalDate.parse(key, keyFormatter) }.getOrNull()?.let { it to info }
            }
            .filter { YearMonth.from(it.first) == month }
            .sortedBy { it.first }

        val totalHours = worked.sumOf { it.second.hoursWorked ?: 0.0 }
        val daysWorked = worked.size
        val avg = if (daysWorked > 0) totalHours / daysWorked else 0.0
        val earnings = totalHours * salaryRate
        // Money is shown only in the full report AND only when a rate is set.
        val hasRate = includeEarnings && salaryRate > 0.0

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val regular = Typeface.DEFAULT

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREEN
            textSize = 22f
            typeface = bold
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREY
            textSize = 9f
            typeface = bold
        }
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 15f
            typeface = bold
        }
        val earnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER
            textSize = 15f
            typeface = bold
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREY
            textSize = 9f
            typeface = bold
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 11f
            typeface = regular
        }
        val cellEarnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER
            textSize = 11f
            typeface = regular
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HAIRLINE
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GREY
            textSize = 9f
            typeface = regular
        }

        // Title
        val monthName = month.month.name.lowercase().replaceFirstChar { it.uppercase() }
        var y = MARGIN + 22f
        canvas.drawText("TimePay — $monthName ${month.year}", MARGIN, y, titlePaint)

        // Summary row: stat blocks across the page. The "Total earned" block only
        // appears in the full report; the hours-only report drops it (3 blocks).
        y += 38f
        val contentWidth = PAGE_WIDTH - MARGIN * 2
        data class Stat(val label: String, val value: String, val amber: Boolean)
        val stats = buildList {
            add(Stat("TOTAL HOURS", formatHours(totalHours), false))
            if (includeEarnings) {
                add(Stat("TOTAL EARNED", if (hasRate) formatEarnings(earnings, currencyCode) else "—", true))
            }
            add(Stat("DAYS WORKED", daysWorked.toString(), false))
            add(Stat("AVG / DAY", formatHours(avg), false))
        }
        val colW = contentWidth / stats.size
        stats.forEachIndexed { i, s ->
            val x = MARGIN + colW * i
            canvas.drawText(s.label, x, y, labelPaint)
            canvas.drawText(s.value, x, y + 18f, if (s.amber && hasRate) earnPaint else statPaint)
        }

        // Table
        y += 44f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f

        // Column x positions. Date left; remaining columns right-aligned. Without
        // earnings the Hours/time columns spread out to fill the page width.
        val xDate = MARGIN
        val xHours: Float
        val xTime: Float
        val xEarn = PAGE_WIDTH - MARGIN
        if (includeEarnings) {
            xHours = MARGIN + contentWidth * 0.42f
            xTime = MARGIN + contentWidth * 0.70f
        } else {
            xHours = MARGIN + contentWidth * 0.50f
            xTime = PAGE_WIDTH - MARGIN
        }

        headerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("DATE", xDate, y, headerPaint)
        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("HOURS", xHours, y, headerPaint)
        canvas.drawText("START → END", xTime, y, headerPaint)
        if (includeEarnings) {
            canvas.drawText("EARNINGS", xEarn, y, headerPaint)
        }

        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)

        val rowHeight = 22f
        val bottomLimit = PAGE_HEIGHT - MARGIN - 24f
        for ((date, info) in worked) {
            if (y + rowHeight > bottomLimit) break // v1: clip overflow.
            y += rowHeight

            cellPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(date.format(rowDateFormatter), xDate, y, cellPaint)

            val hoursValue = info.hoursWorked ?: 0.0
            cellPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatHours(hoursValue), xHours, y, cellPaint)

            val start = info.startTime
            val end = info.endTime
            val timeText = if (start != null && end != null) "$start → $end" else "—"
            canvas.drawText(timeText, xTime, y, cellPaint)

            if (includeEarnings) {
                if (hasRate) {
                    cellEarnPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(formatEarnings(hoursValue * salaryRate, currencyCode), xEarn, y, cellEarnPaint)
                } else {
                    cellPaint.color = GREY
                    canvas.drawText("—", xEarn, y, cellPaint)
                    cellPaint.color = INK
                }
            }

            canvas.drawLine(MARGIN, y + 6f, PAGE_WIDTH - MARGIN, y + 6f, linePaint)
        }

        // Footer
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        footerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Generated $today", MARGIN, PAGE_HEIGHT - MARGIN, footerPaint)

        document.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val monthKey = month.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val suffix = if (includeEarnings) "" else "-hours"
        val file = File(dir, "TimePay-$monthKey$suffix.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()

        return file
    }
}
