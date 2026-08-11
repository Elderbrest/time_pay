package com.el.timepay.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.el.timepay.R
import com.el.timepay.util.MoneyFormat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import java.util.Currency
import java.util.Locale

/**
 * Searchable currency chooser for Settings. Lists every tender currency the platform
 * knows and reports the picked alpha code back to [SettingsFragment] via
 * [setFragmentResult] under [REQUEST_KEY] (the Firestore write lives there). Styled
 * to match [com.el.timepay.ui.reports.ExportOptionsBottomSheet] — rounded top,
 * surface background, tappable rows.
 *
 * The currency is a *label only*: picking a new one relabels the hourly rate and
 * leaves every stored amount untouched. There is no FX conversion anywhere in the
 * app, and historical entries must never be silently rewritten — the sheet says so
 * in a helper line above the list so the choice isn't mistaken for a converter.
 */
class CurrencyPickerBottomSheet : BottomSheetDialogFragment() {

    /** One row: the alpha code plus the pre-rendered "Name (symbol)" subtitle. */
    private data class CurrencyRow(
        val code: String,
        val displayName: String,
        val subtitle: String,
    )

    private lateinit var allRows: List<CurrencyRow>
    private lateinit var adapter: CurrencyAdapter
    private lateinit var emptyText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_currency, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedCode = requireArguments().getString(ARG_SELECTED).orEmpty()
        allRows = buildRows()

        emptyText = view.findViewById(R.id.currencyEmptyText)
        val list = view.findViewById<RecyclerView>(R.id.currencyList)
        adapter = CurrencyAdapter(selectedCode) { code -> deliver(code) }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        adapter.submit(allRows)

        // Scroll straight to the current pick so the sheet opens showing where you are
        // rather than at "AED" — most users only ever change this once.
        val selectedIndex = allRows.indexOfFirst { it.code == selectedCode }
        if (selectedIndex >= 0) list.scrollToPosition(selectedIndex)

        view.findViewById<TextInputEditText>(R.id.currencySearchInput)
            .doAfterTextChanged { text -> applyFilter(text?.toString().orEmpty()) }
    }

    /**
     * Every currency the platform can render, minus the non-tender codes.
     *
     * `defaultFractionDigits < 0` marks the ISO 4217 "not a real currency" entries —
     * XXX (no currency), XAU/XAG (precious metals), IMF special drawing rights and
     * friends. Nobody is paid an hourly rate in gold, so they're only noise here.
     * Sorted by alpha code, which is also what the search matches against first.
     */
    private fun buildRows(): List<CurrencyRow> =
        Currency.getAvailableCurrencies()
            .filter { it.defaultFractionDigits >= 0 }
            .sortedBy { it.currencyCode }
            .map { currency ->
                val code = currency.currencyCode
                val name = currency.getDisplayName(Locale.US)
                // getSymbol() returns the alpha code itself when no symbol is known;
                // showing "PLN — Polish Zloty (PLN)" would be noise, so only append a
                // symbol when it actually differs from the code.
                val symbol = currency.getSymbol(Locale.US)
                CurrencyRow(
                    code = code,
                    displayName = name,
                    subtitle = if (symbol == code) name else getString(
                        R.string.currency_name_with_symbol,
                        name,
                        symbol,
                    ),
                )
            }

    /**
     * Case-insensitive filter over both fields, matching how people actually search:
     * a code is typed from the start ("NG" → NGN), while a name is recalled by any
     * word in it ("naira"/"nigeria" → NGN), so the code matches on PREFIX and the
     * display name on SUBSTRING.
     */
    private fun applyFilter(query: String) {
        val q = query.trim().lowercase(Locale.US)
        val filtered = if (q.isEmpty()) {
            allRows
        } else {
            allRows.filter { row ->
                row.code.lowercase(Locale.US).startsWith(q) ||
                    row.displayName.lowercase(Locale.US).contains(q)
            }
        }
        adapter.submit(filtered)
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun deliver(code: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CURRENCY_CODE to code))
        dismiss()
    }

    private class CurrencyAdapter(
        private val selectedCode: String,
        private val onPick: (String) -> Unit,
    ) : RecyclerView.Adapter<CurrencyAdapter.RowHolder>() {

        private var rows: List<CurrencyRow> = emptyList()

        fun submit(newRows: List<CurrencyRow>) {
            rows = newRows
            // ~150 static rows, rebuilt only on a keystroke — a full invalidate is
            // cheaper to reason about here than DiffUtil, and visually identical.
            notifyDataSetChanged()
        }

        class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
            val code: TextView = view.findViewById(R.id.currencyCodeText)
            val name: TextView = view.findViewById(R.id.currencyNameText)
            val mark: TextView = view.findViewById(R.id.currencySelectedMark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
            RowHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_currency, parent, false)
            )

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = rows[position]
            holder.code.text = row.code
            holder.name.text = row.subtitle
            holder.mark.visibility =
                if (row.code == selectedCode) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onPick(row.code) }
        }
    }

    companion object {
        const val REQUEST_KEY = "currency_picker_result"
        const val RESULT_CURRENCY_CODE = "currency_code"

        private const val ARG_SELECTED = "arg_selected"

        fun newInstance(selectedCode: String): CurrencyPickerBottomSheet =
            CurrencyPickerBottomSheet().apply {
                arguments = bundleOf(
                    ARG_SELECTED to selectedCode.ifBlank { MoneyFormat.DEFAULT_CODE }
                )
            }
    }
}
