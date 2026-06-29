package com.el.timepay.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.el.timepay.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * "Export report" chooser. Offers the two PDF variants (full payslip vs hours-only)
 * as tappable rows; reports the pick back to [ReportsFragment] via [setFragmentResult]
 * under [REQUEST_KEY] (the actual PDF build + share lives there). Styled to match the
 * log-hours sheet — rounded top, surface background.
 */
class ExportOptionsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_export, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hasRate = requireArguments().getBoolean(ARG_HAS_RATE, false)

        val fullRow = view.findViewById<LinearLayout>(R.id.exportFullRow)
        val fullSub = view.findViewById<TextView>(R.id.exportFullSub)
        val fullTitle = view.findViewById<TextView>(R.id.exportFullTitle)
        val fullIcon = view.findViewById<ImageView>(R.id.exportFullIcon)
        val hoursRow = view.findViewById<LinearLayout>(R.id.exportHoursRow)

        // Without a rate a "full" report has no money to add — keep it tappable but
        // hint that earnings need a rate, and de-emphasize it vs the hours-only row.
        if (!hasRate) {
            fullSub.text = getString(R.string.reports_export_full_sub_no_rate)
            val muted = 0.5f
            fullTitle.alpha = muted
            fullIcon.alpha = muted
        }

        fullRow.setOnClickListener { deliver(includeEarnings = true) }
        hoursRow.setOnClickListener { deliver(includeEarnings = false) }
    }

    private fun deliver(includeEarnings: Boolean) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_INCLUDE_EARNINGS to includeEarnings))
        dismiss()
    }

    companion object {
        const val REQUEST_KEY = "export_options_result"
        const val RESULT_INCLUDE_EARNINGS = "include_earnings"

        private const val ARG_HAS_RATE = "arg_has_rate"

        fun newInstance(hasRate: Boolean): ExportOptionsBottomSheet =
            ExportOptionsBottomSheet().apply {
                arguments = bundleOf(ARG_HAS_RATE to hasRate)
            }
    }
}
