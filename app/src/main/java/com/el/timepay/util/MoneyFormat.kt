package com.el.timepay.util

import java.util.Currency
import java.util.Locale

/**
 * Single source of truth for rendering a money [Double] (e.g. 920.5) next to the
 * user's chosen currency (e.g. "PLN 920.50"). Used everywhere earnings are shown —
 * Home, Calendar, Reports, the log sheet, and the PDF — so the format never drifts
 * between screens.
 *
 * Earnings are always derived (`hours × salaryRate`) and never persisted, so this is
 * display-only. Two rules define the shape:
 *
 * - **Whole amounts drop the decimals** ("PLN 920", not "PLN 920.00"). Most shifts
 *   land on round numbers and the shorter string reads better in the small stat
 *   cards; anything with a fraction gets exactly 2 decimals ("PLN 920.50").
 * - **[Locale.US] is deliberate**, not an oversight. The grouping/decimal separators
 *   must stay stable no matter what locale the phone is set to, because the salary
 *   rate is *parsed* back as a dot-decimal in Settings and the same string is baked
 *   into shared PDFs. A locale-aware format would render "920,50" on a Polish device
 *   and silently break that round-trip.
 *
 * ### Symbol vs alpha code — why there are two entry points
 * [format] prefers the currency's symbol when the platform knows one ($, €, £, ₹),
 * falling back to the 3-letter alpha code when it doesn't (PLN and NGN both render as
 * their code). That is what we want on screen, where the system font resolves those
 * glyphs — and note PLN has no symbol, so the existing Polish user sees no change.
 *
 * [formatWithCode] always uses the alpha code and exists for [MonthlyReportPdf]:
 * the PDF is drawn onto a [android.graphics.Canvas] with the default typeface, which
 * does NOT guarantee coverage of every currency glyph. A missing glyph there becomes
 * a tofu box in a document the user shares with an employer — so the PDF trades the
 * nicer symbol for a code that is guaranteed to be plain ASCII.
 *
 * Note the currency code is a pure *label*: switching currencies relabels the rate
 * and never converts historical amounts (there is no FX in this app).
 */
object MoneyFormat {

    /** Fallback when a user doc predates the currency field, or holds a corrupt code. */
    const val DEFAULT_CODE: String = "PLN"

    /**
     * Money for on-screen UI, symbol-preferred: 920.0 + "PLN" → "PLN 920",
     * 920.5 + "INR" → "₹ 920.50".
     */
    fun format(amount: Double, currencyCode: String): String =
        "${symbolFor(currencyCode)} ${amountOnly(amount)}"

    /**
     * Money for the PDF, alpha-code only: 920.5 + "INR" → "INR 920.50". Never emits a
     * symbol glyph — see the class KDoc for why the printout can't risk one.
     */
    fun formatWithCode(amount: Double, currencyCode: String): String =
        "${currencyCode.ifBlank { DEFAULT_CODE }} ${amountOnly(amount)}"

    /**
     * The display symbol for an alpha code, e.g. "INR" → "₹", "PLN" → "PLN".
     *
     * [Currency.getSymbol] already returns the alpha code itself when the platform
     * knows no symbol for it, which is exactly the fallback we want — so there is no
     * special-casing here. Only a code the platform doesn't recognise at all (a
     * corrupt or hand-edited Firestore value) throws, and that falls back to the raw
     * string so the UI shows *something* rather than crashing.
     */
    fun symbolFor(currencyCode: String): String {
        val code = currencyCode.ifBlank { DEFAULT_CODE }
        return try {
            Currency.getInstance(code).getSymbol(Locale.US)
        } catch (e: IllegalArgumentException) {
            code
        }
    }

    /** The bare number, no currency: 920.0 → "920", 920.5 → "920.50". */
    private fun amountOnly(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toInt().toString()
        else String.format(Locale.US, "%.2f", amount)
}
