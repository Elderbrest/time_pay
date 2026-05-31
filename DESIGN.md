# TimePay — Design Direction

> Single-user personal time-tracking app. Primary user: working professional logging hourly shifts (PLN). Goal of this doc: move from "default Android Studio template" to a focused, intentional product.

---

## 1. Diagnosis — what's wrong today

- **It's literally the Material 2 starter theme.** `colors.xml` still ships `purple_500`/`purple_700`/`teal_200`. `themes.xml` parent is `Theme.MaterialComponents.DayNight.DarkActionBar`. There is no brand.
- **Home is a stack of 3 visually identical cards.** "Next working day", "This Week", "This Month" all use the same 16dp-radius elevated card with the same title typography — nothing reads as primary. The most emotionally important number (earnings) has no visual hierarchy.
- **Calendar has 4 FABs stacked on top of each other** (`addDayButton`, `completeDayButton`, `removeDayButton`, `editNoteButton`) with manual visibility toggling. Two of them sit in the exact same end-aligned position. This is the single ugliest piece of the app.
- **Three AlertDialogs in CalendarFragment** for confirm-add, complete-day-with-time-pickers, and edit-note. AlertDialog with custom views inside a system frame looks generic; bottom sheets would feel native to the calendar interaction.
- **Hardcoded `@android:color/holo_red_dark`** for logout/destructive actions, hardcoded `@android:color/darker_gray` for secondary text on Home, raw hex `#757575` and `#AAAAAA` in calendar cell logic — no semantic color tokens.
- **Russian + English mixed in source** (`"Заметка"` literal in `CalendarFragment.showEditNoteDialog`, Russian placeholders in `dialog_work_hours.xml`). Not a visual issue per se but it screams "vibe-coded".
- **What works:** information architecture is genuinely good — 3 tabs, clear mental model, the calendar dot-status pattern (`dot_working`/`dot_done`) is the right idea. The bones are fine; the skin is wrong.

---

## 2. Brand & visual identity

**Mood.** Calm, competent, slightly warm. This is a daily-use tool for someone tracking real work and real money — it should feel like a well-kept ledger, not a productivity-bro app. No gradients, no hype, no gamification. Confidence comes from generous whitespace, one strong accent color, and numbers that are easy to read at a glance. Think Things 3 meets a Scandinavian banking app.

**Color palette** (light mode; dark variants in parens):

| Token | Hex | Use |
|---|---|---|
| `primary` | `#2E5D4F` (dark: `#A7D7C5`) | Deep forest green. Brand accent, primary buttons, selected calendar day. Picked because money + calm + zero overlap with Material default. |
| `on-primary` | `#FFFFFF` (dark: `#0F2620`) | Text/icons on primary surfaces. |
| `secondary` | `#C97B3D` (dark: `#E8A875`) | Warm amber. Reserved for *earnings* numbers and the "Today" indicator — gives money a single consistent visual signature. |
| `surface` | `#FAFAF7` (dark: `#16181A`) | App background. Off-white (not pure white) on light, near-black neutral on dark. |
| `surface-elevated` | `#FFFFFF` (dark: `#1F2326`) | Card backgrounds. |
| `on-surface` | `#1A1F1D` (dark: `#ECEDEB`) | Primary text. |
| `on-surface-muted` | `#5B6360` (dark: `#9AA09D`) | Secondary text, captions, calendar inactive days. Replaces every `darker_gray` usage. |
| `success` | `#4A8B6F` | Completed work day (replace `dot_done`). |
| `error` | `#B3261E` (dark: `#F2B8B5`) | Destructive only — logout, remove day. Replaces `holo_red_dark`. |

**Typography pair.** Headings: **Inter** (700/600). Body & numbers: **Inter** (400/500). One family, two weights — keeps the bundle small and avoids the "two random Google fonts" template smell. For tabular numbers (stat cards, calendar) use Inter's `tnum` feature so digits don't dance. If a personality lift is wanted later, swap headings to **Fraunces** (semi-serif, 600) — but ship with Inter-only first.

**Iconography & shape.** Rounded geometry throughout. Corner radii: 12dp for cards, 16dp for sheets/dialogs, 20dp for the FAB, 999dp (pill) for selected calendar day background. Icon set: Material Symbols Rounded, 24dp, 1.5dp stroke weight. No mixing of filled and outlined in the same surface.

**Spacing scale.** `4 / 8 / 12 / 16 / 24 / 32 / 48`. Default screen padding: 20dp horizontal (between 16 and 24, more breathing room than current 16dp without feeling sparse). Card internal padding: 20dp. Section gaps on Home: 16dp (down from current 32dp — feels less stranded).

---

## 3. Material 3 migration plan

**Theme parent.** Change both `Theme.TimePay` and `Theme.TimePay.Login` from `Theme.MaterialComponents.DayNight.*` to `Theme.Material3.DayNight.NoActionBar`. Drop the action-bar variant entirely — the app doesn't use one. Replace `colorPrimary`/`colorPrimaryVariant`/`colorOnPrimary`/`colorSecondary` with the M3 tokens: `colorPrimary`, `colorOnPrimary`, `colorPrimaryContainer`, `colorOnPrimaryContainer`, `colorSecondary`, `colorTertiary`, `colorSurface`, `colorSurfaceVariant`, `colorOnSurface`, `colorError`. Delete `purple_*`/`teal_*` from `colors.xml`.

**Widget-by-widget audit** (everything currently in layouts):

| Current | Action |
|---|---|
| `MaterialCardView` (Home x3) | Keep. Drop `cardElevation` to 0dp, use `app:strokeColor`/`strokeWidth=1dp` or `surfaceVariant` background — M3 prefers tonal over shadow. |
| `MaterialButton` (login, save, logout) | Keep widget, restyle: default M3 style is filled-tonal; use `Widget.Material3.Button` for primary and `Widget.Material3.Button.TextButton` for "Sign Up" link. |
| `TextInputLayout` w/ `OutlinedBox` (login, settings) | Keep `OutlinedBox` but explicit style becomes `Widget.Material3.TextInputLayout.OutlinedBox`. Corner radius tokens change — re-verify visually. |
| `ShapeableImageView` + `CircularImageView` style | Keep as-is, works under M3. |
| `FloatingActionButton` (Calendar x4) | **Replace entire pattern** — see Move #1. Single M3 `ExtendedFloatingActionButton` that morphs by state. |
| `AlertDialog` (3 in CalendarFragment, 1 in HomeFragment image picker) | Migrate two to `BottomSheetDialogFragment` (see Move #2). Keep "remove day" confirm as M3 `MaterialAlertDialogBuilder`. |
| `TimePicker` spinner in `dialog_time_picker.xml` | Replace with `MaterialTimePicker` (M3 native, clock-face input). |
| `CalendarView` (kizitonwose) | Keep library, restyle `calendar_day.xml` — see Move #3. |
| Raw `EditText` in `dialog_edit_note.xml` | Wrap in `TextInputLayout` for floating label + M3 styling consistency. |

**Material You / dynamic color.** In `TimePayApplication.onCreate()`, call `DynamicColors.applyToActivitiesIfAvailable(this)`. This opts every Activity into Android 12+ wallpaper-derived theming while preserving our forest-green brand as the fallback on Android 11 and below. Test that our `secondary` amber doesn't get steamrolled — may need to lock `colorTertiary` to a fixed hex.

**Risks.** (1) `TextInputLayout` OutlinedBox in M3 has different default corner radius and label sizing — every form will need a visual pass. (2) FAB shape token changed; the current circular FAB becomes a squircle by default. (3) `?attr/textAppearanceHeadline5`/`Headline6` used in `fragment_home.xml` are M2 tokens — they still resolve under M3 but should be migrated to `?attr/textAppearanceHeadlineSmall`/`TitleMedium`. (4) `android.R.color.holo_red_dark` and `android.R.color.darker_gray` references in layouts need to become `?attr/colorError` and `?attr/colorOnSurfaceVariant`.

---

## 4. Component inventory

**Stat cards.** Two variants. *Hero stat* (full width, large 32sp tabular number, label below, optional delta chip "+4h vs last week") used once per screen for the most important figure. *Compact stat* (half width, 20sp number) used in pairs for supporting metrics. Cards: 12dp radius, 1dp stroke, no shadow, surface-elevated background.

**Calendar day cell** — six states, all must be visually distinct:
1. *Default (this month, no data)* — `on-surface` text, no background.
2. *Other month* — `on-surface-muted`, 40% alpha.
3. *Today* — `secondary` amber dot under number, regular weight (not bold — bold is for selection).
4. *Selected* — `primary` pill background (999dp), `on-primary` text, 600 weight.
5. *Working (planned)* — small `primary` outlined ring around the number.
6. *Done (completed)* — `success` filled dot under number (replaces current `dot_done`).
7. *Has note* — tiny corner indicator (3dp square, top-right of cell) in `on-surface-muted`. Stacks with states 3–6.

**FAB.** Single `ExtendedFloatingActionButton`, contextual label by state: "Plan day" / "Mark done" / "Edit note". Long-press opens a small menu for secondary actions (remove). See Move #1.

**Empty states.** Three needed. (a) *No salary set* — inline banner on Home pointing at Settings, dismissible. (b) *No work days this month* — centered icon + one line + "Plan a day" button, replaces the empty Home stat cards. (c) *No profile photo* — current initials fallback is fine, just restyle background to `primaryContainer`.

**Loading.** Per-screen skeleton (3 shimmer cards on Home, calendar greyed at 60% opacity) — NOT a centered spinner. Per-component: button shows inline `CircularProgressIndicator` (M3) when saving, never the current `loading_button` text.

**Error.** Inline `Snackbar` at the bottom (M3 default) for transient failures — replaces all current `Toast` calls. Inline field errors via `TextInputLayout.setError`. Top-level network failure banner only on Home with a Retry action.

**Buttons.** Primary: filled, `primary` bg. Secondary: filled-tonal, `secondaryContainer` bg. Tertiary: text-only. Destructive: filled with `error` bg, used exactly twice (Logout, Remove day).

**Dialogs vs bottom sheets — recommendation.**
- *Bottom sheet*: Complete-day (time pickers + form fields), Edit note (textarea), Profile photo picker. These are content-heavy and the calendar context behind should stay visible.
- *Dialog (M3 alert)*: Confirm add work day, Confirm remove day. Short yes/no — dialogs are correct here.
- *MaterialTimePicker*: Time selection inside complete-day sheet.

---

## 5. Three moves to make it feel intentional

**Move 1 — Collapse the 4-FAB stack into one contextual ExtendedFAB.** *(Highest impact.)* The single biggest eyesore today is `fragment_calendar.xml` lines 64–111: four FABs piled at the same anchor point with manual visibility juggling in `updateActionButtons`. Replace with a single bottom-end ExtendedFloatingActionButton that animates label + icon based on the selected day's state — "Plan day" (plus icon) for empty days, "Mark done" (check icon) for working days, hidden for done days. The note action moves to a small icon button next to the day's notes text below the calendar, where it belongs contextually. Imagine the calendar with a single, clearly-labeled green pill button at the bottom-right that reads what it does — no guessing what each circle means.

**Move 2 — Replace AlertDialog stack with bottom sheets that rise from the calendar.** The complete-day flow currently fires an AlertDialog containing a custom view with two clickable rows that each fire a *second* AlertDialog containing a TimePicker. That's dialog-on-dialog hell. Replace with a single `BottomSheetDialogFragment` titled "Mark as worked" that contains both time fields inline (using `MaterialTimePicker` for selection), an optional hours-worked auto-calculation display, and the note field promoted into the same sheet — so completing a day and writing a note become one gesture instead of two. Imagine tapping a planned day and a sheet sliding up from below the calendar showing "Start: 09:00 · End: 18:00 · 9.0h · PLN 450 · [note field]" with a single Save action.

**Move 3 — Redesign Home around one hero number, not three equal cards.** Current Home shows three stacked cards of identical visual weight. Make "This month's earnings" the hero — one 48sp tabular figure in `secondary` amber, with a small `on-surface-muted` "PLN" prefix and a delta chip showing change vs. last month. Below it, a two-column row of compact cards: "Hours this month" (left, 24sp) and "Next work day" (right, with relative phrasing like "Tomorrow" or "In 3 days"). The "This week" row drops to a single muted line at the bottom: "This week: 12.5h · PLN 625". Profile avatar and name shrink from 120dp to a 56dp circle in a top-app-bar-style header, freeing the screen for the numbers that actually matter daily. Imagine opening Home and the first thing you see is **PLN 4,250** in amber — the money you've earned this month — instead of a generic dashboard.

---

## 6. Decisions

1. **Currency:** PLN hardcoded. Single user, single currency — amber PLN amount on Home gets first-class typographic treatment.
2. **Week start:** Monday, not user-configurable.
3. **Goals:** Out of scope. No progress ring on the hero, no goal field in Settings — passive tracking only.
4. **Primary language:** Russian. Cyrillic in Inter renders well — no font swap needed. Hardcoded Russian literals (`dialog_work_hours.xml`, `CalendarFragment.showEditNoteDialog`) can become normal `strings.xml` entries during the implementation pass; no broader i18n work needed.
5. **Profile photo on Home:** Shrink to 56dp top-bar avatar. Move 3 stands — the earnings hero number is the focal point.
