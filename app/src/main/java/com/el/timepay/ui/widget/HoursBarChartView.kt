package com.el.timepay.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.el.timepay.R

/** One day's bar: [day] of month, [hours] worked, and whether the day is logged as done. */
data class BarDatum(val day: Int, val hours: Double, val isDone: Boolean)

/**
 * A lightweight Canvas bar chart — one bar per day of the month, height proportional to
 * hours worked. Bars grow up on [setData]. Tapping a bar highlights it and notifies
 * [onBarSelected]; tapping it again (or outside the band) clears the selection.
 */
class HoursBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var onBarSelected: ((day: Int?) -> Unit)? = null

    private var data: List<BarDatum> = emptyList()
    private var maxHours: Double = 10.0
    private var selectedIndex: Int? = null
    private var animProgress: Float = 1f
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    // Bars/labels use SP sizing derived from font scale; density is a fine approximation here.
    private fun sp(value: Float) =
        value * resources.displayMetrics.density * resources.configuration.fontScale

    private val labelStrip = dp(22f)
    private val rect = RectF()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_primary)
    }
    private val barSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_secondary)
    }
    private val barEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_surface_container_high)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = ContextCompat.getColor(context, R.color.md_outline_variant)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_on_surface_variant)
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.md_secondary)
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textSize = sp(11f)
        textAlign = Paint.Align.CENTER
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            // Claim the gesture on DOWN so the view (inside a ScrollView) keeps the
            // event stream and the tap-up below actually fires.
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleTap(e.x)
                return true
            }
        },
    )

    init {
        isClickable = true
    }

    /** Replaces the data set, clears the selection and restarts the grow-up animation. */
    fun setData(data: List<BarDatum>, maxHours: Double) {
        this.data = data
        this.maxHours = if (maxHours <= 0.0) 10.0 else maxHours
        this.selectedIndex = null
        startGrowAnimation()
    }

    /** Programmatically highlights [day] (or clears with null). Does NOT fire [onBarSelected]. */
    fun setSelectedDay(day: Int?) {
        selectedIndex = day?.let { d -> data.indexOfFirst { it.day == d }.takeIf { it >= 0 } }
        invalidate()
    }

    private fun startGrowAnimation() {
        animator?.cancel()
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        if (scale == 0f) {
            animProgress = 1f
            invalidate()
            return
        }
        animProgress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = data.size
        if (count == 0) return

        val chartLeft = paddingLeft.toFloat()
        val chartRight = (width - paddingRight).toFloat()
        val chartTop = paddingTop.toFloat()
        val baselineY = (height - paddingBottom).toFloat() - labelStrip
        val chartWidth = chartRight - chartLeft
        val chartHeight = baselineY - chartTop
        if (chartWidth <= 0f || chartHeight <= 0f) return

        // Gridlines at maxHours and maxHours/2, with tiny right-edge labels.
        labelPaint.textAlign = Paint.Align.RIGHT
        for (fraction in listOf(1f, 0.5f)) {
            val y = baselineY - chartHeight * fraction
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val hoursLabel = context.getString(
                R.string.calendar_hours_cell,
                (maxHours * fraction).toInt().toString(),
            )
            canvas.drawText(hoursLabel, chartRight, y - dp(2f), labelPaint)
        }
        labelPaint.textAlign = Paint.Align.CENTER

        val slot = chartWidth / count
        val barWidth = slot * 0.55f
        val radius = barWidth / 2f
        val labelIndices = setOf(0, count / 4, count / 2, 3 * count / 4, count - 1)

        for (i in data.indices) {
            val datum = data[i]
            val centerX = chartLeft + slot * i + slot / 2f
            val left = centerX - barWidth / 2f
            val right = centerX + barWidth / 2f

            // Every day gets a bar: worked days scale with hours; days off get a
            // short grey placeholder so the row keeps a steady rhythm.
            if (datum.hours > 0.0) {
                val fraction = (datum.hours / maxHours).coerceIn(0.0, 1.0).toFloat()
                val barHeight = fraction * chartHeight * animProgress
                val top = baselineY - barHeight
                // Extend below the baseline so the rounded bottom is clipped flat.
                rect.set(left, top, right, baselineY + radius)
                val paint = if (i == selectedIndex) barSelectedPaint else barPaint
                canvas.drawRoundRect(rect, radius, radius, paint)

                if (i == selectedIndex) {
                    drawValueChip(canvas, datum.hours, centerX, top)
                }
            } else {
                val barHeight = (chartHeight * 0.10f * animProgress).coerceAtLeast(dp(6f))
                val top = baselineY - barHeight
                rect.set(left, top, right, baselineY + radius)
                canvas.drawRoundRect(rect, radius, radius, barEmptyPaint)
            }

            if (i in labelIndices) {
                canvas.drawText(
                    datum.day.toString(),
                    centerX,
                    baselineY + labelStrip - dp(6f),
                    labelPaint,
                )
            }
        }
    }

    private fun drawValueChip(canvas: Canvas, hours: Double, centerX: Float, barTop: Float) {
        val text = context.getString(R.string.calendar_hours_cell, formatHours(hours))
        val padH = dp(6f)
        val padV = dp(3f)
        val textWidth = chipTextPaint.measureText(text)
        val fm = chipTextPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val chipWidth = textWidth + padH * 2
        val chipHeight = textHeight + padV * 2

        var chipLeft = centerX - chipWidth / 2f
        chipLeft = chipLeft.coerceIn(paddingLeft.toFloat(), width - paddingRight - chipWidth)
        val chipBottom = (barTop - dp(4f)).coerceAtLeast(paddingTop + chipHeight)
        val chipTop = chipBottom - chipHeight

        rect.set(chipLeft, chipTop, chipLeft + chipWidth, chipBottom)
        val r = dp(4f)
        canvas.drawRoundRect(rect, r, r, chipBgPaint)
        canvas.drawText(
            text,
            chipLeft + chipWidth / 2f,
            chipBottom - padV - fm.descent,
            chipTextPaint,
        )
    }

    private fun formatHours(hours: Double): String =
        if (hours % 1.0 == 0.0) hours.toInt().toString()
        else String.format(java.util.Locale.US, "%.1f", hours)

    private fun handleTap(x: Float) {
        val count = data.size
        if (count == 0) return
        val chartLeft = paddingLeft.toFloat()
        val chartRight = (width - paddingRight).toFloat()
        if (x < chartLeft || x > chartRight) {
            clearSelection()
            return
        }
        val slot = (chartRight - chartLeft) / count
        val index = ((x - chartLeft) / slot).toInt()
        // Only worked days have a bar to select; a tap on a day-off slot clears.
        if (index in data.indices && data[index].hours > 0.0) {
            selectedIndex = if (index == selectedIndex) null else index
            invalidate()
            onBarSelected?.invoke(selectedIndex?.let { data[it].day })
        } else {
            clearSelection()
        }
    }

    private fun clearSelection() {
        if (selectedIndex != null) {
            selectedIndex = null
            invalidate()
            onBarSelected?.invoke(null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun performClick(): Boolean = super.performClick()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val defaultHeight = dp(200f).toInt()
        val height = resolveSize(defaultHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
