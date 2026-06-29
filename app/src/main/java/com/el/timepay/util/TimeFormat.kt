package com.el.timepay.util

import java.util.Locale

/**
 * Single source of truth for rendering a worked-hours [Double] (e.g. 8.25) as a
 * human-readable clock string `H:MM` (e.g. "8:15"). Used everywhere hours are
 * shown — per-shift, per-day, totals, and averages — so the format never drifts
 * between screens.
 *
 * The stored value stays a [Double]; this is display-only. Minutes are derived
 * via [Math.round] of `hours * 60` to avoid float drift (8.25 → 495 min → "8:15").
 *
 * The result carries NO unit suffix on purpose: callers wrap it in their own
 * string template (e.g. `calendar_hours_cell` "%1$sh", `home_hours_short`
 * "%1$s h") so a small "h" label sits next to it — otherwise "164:30" could be
 * misread as a time-of-day.
 */
object TimeFormat {

    /** 8.25 → "8:15", 8.0 → "8:00", 164.5 → "164:30". Negatives are clamped to 0. */
    fun hoursToHm(hours: Double): String {
        val totalMinutes = Math.round(hours.coerceAtLeast(0.0) * 60.0)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format(Locale.US, "%d:%02d", h, m)
    }
}
