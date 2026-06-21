package com.el.timepay.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.el.timepay.R

/**
 * Placeholder destination for the upcoming Reports tab.
 *
 * Renders a static "coming soon" empty state; no data, binding, or state is
 * required yet, so the layout is inflated directly.
 */
class ReportsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_reports, container, false)
}
