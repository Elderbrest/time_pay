package com.el.timepay.util

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin wrapper over Firebase Analytics for TimePay's core, privacy-light events.
 *
 * We deliberately log only a handful of meaningful actions (plus the SDK's automatic
 * screen_view / session / retention data) — enough to answer "are people using it and
 * coming back?" without tracking everything. No PII is ever sent: events carry only
 * coarse, non-identifying parameters (e.g. which entry point, whole-number hours).
 */
object Analytics {

    private var fa: FirebaseAnalytics? = null

    /** Call once from Application.onCreate after Firebase is initialized. */
    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    /** A day's hours were logged (status -> "done"). [source] = "home" | "calendar". */
    fun logHoursLogged(source: String, hours: Double) {
        fa?.logEvent(
            EVENT_HOURS_LOGGED,
            bundleOf(
                PARAM_SOURCE to source,
                // Coarse bucket only — never the exact value tied to a person.
                PARAM_HOURS to hours.toInt().toLong(),
            ),
        )
    }

    /** A future day was marked as planned (status -> "working"). */
    fun logDayPlanned(source: String) {
        fa?.logEvent(EVENT_DAY_PLANNED, bundleOf(PARAM_SOURCE to source))
    }

    /** A logged day was removed. */
    fun logDayRemoved() {
        fa?.logEvent(EVENT_DAY_REMOVED, null)
    }

    /** The monthly report PDF was exported/shared. */
    fun logReportExported() {
        fa?.logEvent(EVENT_REPORT_EXPORTED, null)
    }

    /** The salary rate was set or changed in Settings. */
    fun logRateSet() {
        fa?.logEvent(EVENT_RATE_SET, null)
    }

    private const val EVENT_HOURS_LOGGED = "hours_logged"
    private const val EVENT_DAY_PLANNED = "day_planned"
    private const val EVENT_DAY_REMOVED = "day_removed"
    private const val EVENT_REPORT_EXPORTED = "report_exported"
    private const val EVENT_RATE_SET = "rate_set"

    private const val PARAM_SOURCE = "source"
    private const val PARAM_HOURS = "hours"

    const val SOURCE_HOME = "home"
    const val SOURCE_CALENDAR = "calendar"
}
