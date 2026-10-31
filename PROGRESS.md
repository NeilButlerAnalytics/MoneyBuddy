# MoneyBuddy Progress

Last updated: 30 October 2026

## Current status

Stage 2 is implemented, installed, and launching on the Samsung Galaxy Tab S6
Lite. Stage 3 provider investigation and secure architecture design are now in
progress.

The application is local-only and uses synthetic demonstration data. It does not
connect to HSBC or any other banking service yet.

## Completed

### Project foundation

- Created a separate Android project at `C:\MoneyBuddy`.
- Configured Kotlin, Jetpack Compose, Room, and the Gradle wrapper.
- Configured package name `com.moneybuddy.app`.
- Set the minimum Android version to API 29 for Galaxy Tab S6 Lite compatibility.
- Added a tablet-focused dark navy, orange, green, and red visual style.

### Stage 1 application

- Added a Home dashboard.
- Added a Transactions screen.
- Added a Settings screen.
- Added persistent navigation.
- Added Room storage for transactions.
- Added realistic synthetic transactions.
- Added balance, income, and spending summaries.
- Added a spending breakdown chart.
- Added recent transactions.
- Added example upcoming payments.
- Clearly labelled demonstration data so it cannot be confused with real bank data.

### Themes and orientation

- Added dark and light themes.
- Added a theme switch under Settings.
- Persisted the selected theme between app launches.
- Removed the fixed landscape orientation.
- Added adaptive portrait layout with bottom navigation.
- Kept the left navigation rail for landscape layout.

### Tablet validation

- Detected the connected Samsung Galaxy Tab S6 Lite (`SM-P615`).
- Built the debug APK successfully.
- Installed the APK on the physical tablet.
- Launched `MainActivity` successfully.
- Confirmed no fatal runtime errors after installation.

## Stage 2 — Transaction management

**Status: Implemented and installed; awaiting user review**

Added:

- Search by merchant, category, or note.
- Income, spending, and all transaction filters.
- Category filter chips.
- Transaction count for the active filters.
- Clickable transaction rows.
- Transaction details dialog.
- Category editing using the Stage 2 category list.
- Note editing and local persistence through Room.

The Stage 2 debug build completed successfully, installed successfully on the
tablet, and launched without fatal runtime errors.

## Current build commands

Build the debug APK:

```powershell
Set-Location C:\MoneyBuddy
.\gradlew.bat :app:assembleDebug
```

Install on a connected tablet with USB debugging enabled:

```powershell
.\gradlew.bat :app:installDebug
```

The generated APK is:

`C:\MoneyBuddy\app\build\outputs\apk\debug\app-debug.apk`

## Current limitations

- HSBC integration has not started.
- No Open Banking provider has been selected.
- The latest Stage 2 transaction controls are awaiting user review.
- Upcoming payments are fixed example data.
- There is no live account connection status.
- There is no payment initiation or money-transfer functionality.
- The app does not use a backend or cloud synchronisation.
- Synthetic data is seeded into local Room storage on first launch.

## Development stages

### Stage 1 — Application foundation

**Status: Complete, awaiting user review**

The dashboard, navigation, local data, settings, themes, and tablet layouts are
available for testing.

### Stage 2 — Transaction management

**Status: Implemented; awaiting physical review**

The feature list above is now available in the Transactions screen. Stage 3
should wait until the Stage 2 controls have been reviewed on the tablet.

### Stage 3 — HSBC banking integration

**Status: Provider investigation and architecture in progress**

The investigation is recorded in [STAGE3-OPEN-BANKING.md](./STAGE3-OPEN-BANKING.md).
Enable Banking is now the leading candidate because its official documentation
explicitly describes restricted production access for linked own accounts and
individual non-commercial use. This may suit a personal app without a
subscription, but the Control Panel terms and HSBC UK support still need to be
confirmed.

All provider secrets and authorisation-code exchanges must be server-side. The
Android app must remain read-only and must never handle HSBC passwords.

No live provider code has been added yet because the provider account terms and
eligibility still need confirmation.

## Stage 3 — bank statement import (replaces the live Enable Banking plan)

**Status: Implemented and unit tested; tablet install pending reconnection**

Enable Banking does not cover the UK, so Stage 3 now starts with importing a CSV
downloaded from HSBC online banking. This is free and needs no hosting.

- Settings → **Import bank CSV** opens the Android file picker.
- Recognises HSBC-style `Date, Type, Description, Paid out, Paid in, Balance`,
  `Date, Description, Amount` (with or without a header) and Monzo-style exports.
  The exact HSBC UK column layout has not been confirmed against a real file, so
  the first real import is the test.
- Re-importing an overlapping file never duplicates transactions and never
  overwrites categories or notes you have edited.
- The latest balance in the file is shown on the dashboard.
- The first import removes the demo data.
- The dashboard now summarises the most recent month in the data instead of all
  transactions.
- Settings → **Delete all local transactions** (with confirmation) satisfies the
  requirement to delete local financial data. Demo data is not re-seeded.
- Imported files are read in memory only, are never uploaded, and nothing from
  them is logged.
- A synthetic test file is in [samples/sample-statement.csv](./samples/sample-statement.csv).
- Light theme fix: balance and spending amounts were hard-coded white.

Still open: a live connection (needs a UK-capable provider, see
[STAGE3-OPEN-BANKING.md](./STAGE3-OPEN-BANKING.md)), connection status and last
sync display, and a Monzo option.

## Spending chart and HSBC MiData import (31 October 2026)

**Status: Implemented and installed on the tablet; awaiting review**

Interactive spending chart:

- The donut is larger and thicker, with a gap before the legend and a colour
  swatch per category.
- Tapping a section blurs the screen behind (Android 12+; older versions get a
  dimmed backdrop), grows the chart out of its card, detaches and enlarges the
  chosen section, and lists that category's transactions beside it (merchant,
  date, payment type, note, amount and share of the category).
- Tapping another section switches the details with an animation. Tapping the
  blurred background, or Back, returns the chart to its card.
- Code: `ui/SpendingChart.kt`, tap detection covered by `SpendingChartTest`.

HSBC MiData export (`Date, Type, Merchant/Description, Debit/Credit, Balance`):

- A real export (1,398 transactions, Oct 2025 to Sep 2026) parses with no
  skipped rows, no duplicate ids and the correct closing balance. The file's
  running balance reconciles exactly, so nothing is missing from the export.
- Fixed: headings containing a slash were not recognised; `+£` / `-£` amounts;
  the trailing "Arranged overdraft limit" line is ignored; `****` masking is
  removed from descriptions.
- New: the Type column (contactless, direct debit, standing order and so on) is
  now stored and shown. This needed a database upgrade (version 2).
- The file has no categories, no time of day, no account name and no reference,
  so every imported spend is "Other" until Stage 4 (automatic categorisation).
  Own-account transfers (`TFR`) also count as spending and income until Stage 4.

## Spending periods and editable categories (31 October 2026)

**Status: Implemented and installed on the tablet; awaiting review**

Spending chart period (saved between launches):

- **Week**: last 7 days, or this week so far (Monday to today).
- **Month**: last 30 days, or this month so far (the 1st to today).
- **Custom range**: a date-range picker.
- The chart, legend and transaction lists recalculate as soon as a choice is made.
- If the period has no spending the card says so, shows the date of your latest
  transaction and offers one tap to the month that has data. This matters when
  a bank export is a few weeks old.
- The three summary cards above the chart still show the latest month in the
  data; they do not follow the chart's period.

Categories (Settings, Categories):

- Add, rename, recolour and delete. Stored in the database (version 3); the
  default list is created on the first launch after upgrading.
- Renaming updates every transaction in the category.
- Deleting moves its transactions to "Other", after a confirmation that says
  how many.
- "Other" cannot be renamed or deleted. "Income" is reserved for money in.
  Names are checked for duplicates ignoring case.
- The transaction editor, the Transactions filter and the chart colours all use
  the edited list.
- Verified on the tablet: the period selector, the date picker, adding and
  deleting a category, and that "Other" stays last. Renaming a category that
  already has transactions, and deleting one that has, are covered by the
  database logic and unit-tested name rules but were not exercised on the
  tablet.

## Next checkpoint

The next action is to use Enable Banking's Control Panel to confirm HSBC UK
support, link the intended personal account in restricted mode, and confirm the
actual cost. Only then should the smallest required server-side integration be
created.

The current Enable Banking sandbox application exposes Payment Initiation as
well as Account Information. MoneyBuddy will use Account Information only and
will not implement or request payment functionality.

The Enable Banking sandbox private key has been located at
`C:\MoneyBuddy\secret\023f6c23-1c2e-4fe0-b377-56bf26f4c8e9.pem`. It remains
outside Android source files and is excluded from version control by
`.gitignore`. Its contents must never be pasted into chat or bundled into the
APK.

The local Account Information connector has now been added under
`C:\MoneyBuddy\connector`. It is sandbox-oriented, reads the private key only
server-side, and deliberately has no payment routes. It still needs to be
started and tested against the Enable Banking sandbox.

The connector was tested successfully against Enable Banking on 31 October
2026. JWT authentication and application metadata work, but the sandbox
application reports no GB support and returns no ASPSPs for GB. HSBC UK cannot
therefore be tested through this sandbox. Production restricted-mode support
and pricing must be confirmed before using a real account.

For a complete restart checklist, see
[HANDOFF-2026-10-31.md](./HANDOFF-2026-10-31.md).

Update (31 October 2026): Enable Banking does not cover the UK, so it cannot
connect to HSBC UK. Alternatives and costs are in
[STAGE3-OPEN-BANKING.md](./STAGE3-OPEN-BANKING.md). A provider decision is
needed before more Stage 3 work.

A Cloudflare Worker has also been created at
`https://moneybuddy-api.neil-butler-1981.workers.dev/`. Its source is in
`worker/`; it needs the Production Enable Banking secrets before deployment.
