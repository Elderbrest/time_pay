# TimePay — Future Improvements & Backlog

Running list of fixes/enhancements to tackle after the 2.0.x launch.
Ordered loosely by user-visible value.

---

## UX fixes (reported by user, post-launch)

### 1. Show worked hours as H:MM, not a decimal  ⭐ user-requested
**Problem:** A shift of 09:45–17:15 is shown as `8.3h`, which isn't intuitive — you can't tell it's 8h15m.
**Want:** human-readable clock format, e.g. **`8:15`** (and `8:45`, `8:00`, etc.).
**Where it shows (all need the change):**
- `ui/widget/HoursBarChartView.kt:276` `formatHours()` — bar value chip
- `ui/shared/LogHoursBottomSheet.kt:391` `formatHours()` — "Log Nh" button, detail
- `ui/home/HomeFragment.kt:136` `formatHours()` — stat cards
- `ui/reports/ReportsFragment.kt:55` `formatHours()` — stats + day detail
- `ui/dashboard/CalendarFragment.kt:64` `formatHours()` — day cell "8h", detail card
- `util/MonthlyReportPdf.kt:32` `formatHours()` — PDF table
**Approach:** `formatHours` currently does `hours.toInt()` / `"%.1f"`. Replace with a minutes-based format: take total minutes, render `H:MM` (e.g. `495 min → "8:15"`). Decide whether the unit suffix changes — strings like `calendar_hours_cell` = `"%1$sh"` would become just `"%1$s"` or keep an `h`. Consider showing `8:15` (no suffix) vs `8h 15m`. **User chose `H:MM` (e.g. `8:15`).**
**DRY note:** `formatHours` is copy-pasted in 6 files (flagged in the code-quality audit). Fix is a good moment to extract a single shared `util/HoursFormat` / `util/TimeFormat` object and update all call sites — fix once, not six times. Tabular figures already used for alignment.
**Caveat:** `hoursWorked` is stored as a `Double` (e.g. 8.25). This is a DISPLAY-only change — keep storing the `Double` in Firestore; only the *shown* string changes. Convert via `Math.round(hours * 60)` minutes to avoid float drift (8.25 → 495 min → "8:15").
**Scope — apply to ALL displayed hours, including aggregates (user confirmed):**
- **Per-shift / per-day** values (e.g. calendar cell `8H`, day-detail "Worked 8.5", the "Log Nh" button) → `H:MM`.
- **TOTAL hours** — e.g. Reports `statTotalHoursValue` (currently "164.5h"), Home "164.5h logged", the calendar month-summary `calendar_summary_hours` "%1$s · %2$s H", "This Week"/"This Month" stat cards (`home_hours_short` "%1$s h") → all `H:MM` (e.g. **164:30**, not 164.5h).
- **AVG / day** — Reports `statAvgValue` (currently "8.2h") → `H:MM` (e.g. **8:13**). Note averaging may give odd minutes (164:30 ÷ 20 = 8.225h → 8:14); round minutes sensibly.
**Strings affected:** `home_hours_short`, `home_hours_logged_separator` (" h logged · "), `calendar_summary_hours`, `calendar_hours_cell`, `reports_stat_*` value bindings, plus the bare numbers set in code (ReportsFragment `updateSummary`, HomeFragment stat cards). Decide on a consistent suffix — e.g. drop the trailing "h"/"H" once it's clock-format, OR keep a small "h"/"hrs" label next to "164:30" for clarity. (Suggestion: show "164:30" with a tiny "h" unit label, since "164:30" alone could be misread as a time-of-day.)

### 2. Reset the log sheet times to 09:00–18:00 for each new day  ⭐ user-requested
**Problem:** Opening the log sheet for a NEW day pre-fills Start/End from your **last logged shift** (`LogHoursBottomSheet.kt:100-101`: `existingStart → lastStart → 09:00`). Leftover values from a previous day are confusing.
**Want:** every NEW day opens at a clean **09:00–18:00** default (ignore last-shift). Existing/edit days still load their own saved times.
**Where:** `ui/shared/LogHoursBottomSheet.kt:96-101` — drop the `lastStart`/`lastEnd` fallback for new days; keep `existingStart`/`existingEnd` for editing. Also remove the now-unused `lastShift()` helpers in `HomeFragment.kt` and `CalendarFragment.kt` + the `ARG_LAST_START`/`ARG_LAST_END` plumbing if no longer used.
**Note:** "remember last shift" was an earlier deliberate choice (save typing for regular hours) — user has decided a fixed 09:00–18:00 default is clearer. Straightforward change.

---

## Deferred from the pre-launch audit (Tier 2 — "professional hardening")

3. **Shared month cache** — Home/Calendar/Reports each refetch the current month independently; tab-switching multiplies Firestore reads (~150–220/session vs ~22). A `YearMonth→days` cache invalidated on log-sheet writes ~4× the free-tier user ceiling (≈250 → ≈1,100 DAU). (cost audit P2)
4. **Enable R8 + model keep-rules** — `isMinifyEnabled=false` today. Turn on `isMinifyEnabled`+`isShrinkResources` for release AND add `-keep class com.el.timepay.models.** { *; }` (or `@Keep`) or R8 silently breaks Firestore `toObject()` reflection. Must be done + tested together. Upload the mapping file to Play after (resolves the "no deobfuscation file" warning seen at upload). (security M2 / arch H5+H6)
5. **Money-math tests** — zero tests on earnings/aggregation/overnight-duration. Extract pure fns (`formatHours`/`formatEarnings`, week aggregation, `recomputeHours`) to a testable util + JUnit tests. The overnight rollover (`09:00→09:00` = 24h) is untested. (arch M7) — pairs naturally with #1's util extraction.
6. **Firestore rules field validation** — rules allow any shape on a user's own docs. Add type/range/key-allowlist (`status in ['working','done']`, `hoursWorked` 0–24, `note` size cap) and forbid client writes to `role`. (security M1)
7. **Avatar compression** — `PhotoRepository.uploadProfilePhoto` uploads full-size (3–8MB). Downscale ~512px/80% JPEG before upload + Glide `.signature()` to fix stale-avatar-after-change. (cost P5)
8. **Error handling + retry** — Calendar/Reports month loads lack try/catch; failures show raw `e.message` toasts with no retry. Standardize generic copy + inline "Couldn't load — Retry" + funnel to Crashlytics. (arch H4)

---

## App Check (operational, not code)
9. **Enforce Firebase App Check** — the Play Integrity provider is installed in the app, but enforcement is intentionally OFF in the Firebase console (so it can't lock users out at launch). Turn enforcement ON for Firestore/Storage/Auth a few days after 2.0.x is live and verified stable. Register the release SHA-256 (`3D:AE:...` debug / the real key's SHA) + a debug token for emulator testing. (security H1)

---

## Smaller polish / cleanup (low priority)
10. **DRY** — beyond `formatHours`: `formatEarnings`, `updateMonthText`, the done-day aggregation are duplicated across fragments → shared util.
11. **`status` as enum** — replace the stringly-typed `"working"`/`"done"` (≈30 literal occurrences) with `enum class DayStatus`. (quality M6)
12. **Dead strings/dimens** — a few now-unused strings remain (e.g. `reports_activity_title`/`subtitle` after the bare-chart change, `login_reset_*` after the forgot-password screen, `settings_title`/`company`/`version`). Sweep when `isShrinkResources` is on (#4) or manually.
13. **CalendarFragment → ViewBinding** — it still uses ~13 `findViewById` calls vs ViewBinding everywhere else. (quality L5)
14. **Multi-company support** (user's stated future feature) — the `company` field exists on the model but is hidden in the UI; a future version could support working at multiple workplaces with per-workplace rates.
