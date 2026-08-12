package com.el.timepay.ui.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.el.timepay.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

/**
 * Language chooser for Settings. Lists "System default" plus every locale TimePay
 * ships (kept in lockstep with `res/xml/locales_config.xml`) and reports the picked
 * BCP-47 tag back to [SettingsFragment] via [setFragmentResult] under [REQUEST_KEY];
 * the actual [AppCompatDelegate.setApplicationLocales] call lives there. Styled to
 * match [CurrencyPickerBottomSheet] — rounded top, surface background, tappable rows.
 *
 * Two deliberate constraints:
 *  - Every row is labelled with its own **endonym** ("Русский", not "Russian"). Someone
 *    stuck in a language they cannot read must still be able to find their way out, so
 *    the list must never be rendered in the current UI language.
 *  - No search field. Fourteen rows do not need one.
 *
 * The picked language is a *device* preference, not user data: it is never written to
 * Firestore, so the same account can run in different languages on different phones.
 */
class LanguagePickerBottomSheet : BottomSheetDialogFragment() {

    /**
     * One row. [tag] is the BCP-47 tag, or empty for "System default" — empty doubles
     * as the sentinel for "no app locale set", matching what an empty
     * [AppCompatDelegate.getApplicationLocales] means.
     */
    private data class LanguageRow(
        val tag: String,
        val label: String,
        val isSystemDefault: Boolean,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_language, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedTag = requireArguments().getString(ARG_SELECTED).orEmpty()

        val list = view.findViewById<RecyclerView>(R.id.languageList)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = LanguageAdapter(buildRows(), selectedTag) { tag -> deliver(tag) }

        // Note: unlike the currency sheet, this list is deliberately NOT scrolled to the
        // current selection. With ~158 currencies that jump saves a long scroll; with 14
        // languages it only pushes "System default" off the top, hiding the row that has
        // to be the most visible one. The whole list is a flick away regardless.
    }

    /**
     * "System default" first — it is the recommended choice, because it follows the
     * phone and keeps working if the user changes the device language later — then the
     * shipped locales in [SUPPORTED_TAGS] order.
     */
    private fun buildRows(): List<LanguageRow> = buildList {
        add(
            LanguageRow(
                tag = "",
                label = getString(R.string.language_system_default),
                isSystemDefault = true,
            )
        )
        SUPPORTED_TAGS.mapTo(this) { tag ->
            LanguageRow(tag = tag, label = endonymOf(tag), isSystemDefault = false)
        }
    }

    private fun deliver(tag: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_LANGUAGE_TAG to tag))
        dismiss()
    }

    private class LanguageAdapter(
        private val rows: List<LanguageRow>,
        private val selectedTag: String,
        private val onPick: (String) -> Unit,
    ) : RecyclerView.Adapter<LanguageAdapter.RowHolder>() {

        class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
            val content: View = view.findViewById(R.id.languageRowContent)
            val name: TextView = view.findViewById(R.id.languageNameText)
            val mark: TextView = view.findViewById(R.id.languageSelectedMark)
            val divider: View = view.findViewById(R.id.languageDivider)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
            RowHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_language, parent, false)
            )

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = rows[position]
            holder.name.text = row.label

            // "System default" is set apart as the recommended row: bold and tinted,
            // with a rule under it separating it from the explicit languages. Both
            // properties are reset on the else branch — holders are recycled.
            if (row.isSystemDefault) {
                holder.name.setTypeface(null, Typeface.BOLD)
                holder.divider.visibility = View.VISIBLE
            } else {
                holder.name.setTypeface(null, Typeface.NORMAL)
                holder.divider.visibility = View.GONE
            }

            holder.mark.visibility =
                if (row.tag == selectedTag) View.VISIBLE else View.GONE
            holder.content.setOnClickListener { onPick(row.tag) }
        }
    }

    companion object {
        const val REQUEST_KEY = "language_picker_result"
        const val RESULT_LANGUAGE_TAG = "language_tag"

        private const val ARG_SELECTED = "arg_selected"

        /**
         * Every locale with a `values-xx/strings.xml`, plus `en` for `values/`. Must
         * stay in sync with `res/xml/locales_config.xml`; a tag listed here without a
         * translation folder would silently show English under a foreign label.
         */
        val SUPPORTED_TAGS: List<String> = listOf(
            "en", "es", "pt", "fr", "de", "it", "ru", "pl", "uk", "tr", "id", "vi", "uz",
        )

        /**
         * ICU's own name for a language, in that language, title-cased the way the
         * language itself would write it (many locales return a lowercase name because
         * they do not capitalise languages mid-sentence; standing alone in a list it
         * reads as a typo).
         *
         * [OVERRIDES] carries the two ICU gets wrong for a picker — see there. Uppercasing
         * uses the row's *own* locale so Turkish dotted/dotless "i" survives, which
         * `uppercase()` with the default locale would mangle.
         */
        fun endonymOf(tag: String): String {
            OVERRIDES[tag]?.let { return it }
            val locale = Locale.forLanguageTag(tag)
            val name = locale.getDisplayLanguage(locale)
            if (name.isEmpty()) return tag
            return name.substring(0, 1).uppercase(locale) + name.substring(1)
        }

        /**
         * Hardcoded where ICU's endonym is not what a user scanning a language list
         * expects. Verified against the platform output on API 35 and JDK 25:
         *
         *  - `id` → ICU says "Indonesia", which is the *country* in English and reads as
         *    a place, not a language. Indonesians name the language "Bahasa Indonesia".
         *  - `uz` → ICU says "o‘zbek" (the bare adjective). "Oʻzbekcha" is the form used
         *    for the language itself, and is what Android's own language list shows.
         *
         * The other 11 come out correct after title-casing and are left to ICU, so they
         * keep improving with the platform instead of rotting in a table here.
         */
        private val OVERRIDES = mapOf(
            "id" to "Bahasa Indonesia",
            "uz" to "Oʻzbekcha",
        )

        fun newInstance(selectedTag: String): LanguagePickerBottomSheet =
            LanguagePickerBottomSheet().apply {
                arguments = bundleOf(ARG_SELECTED to selectedTag)
            }
    }
}
