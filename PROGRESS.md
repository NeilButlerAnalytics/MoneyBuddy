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

A Cloudflare Worker has also been created at
`https://moneybuddy-api.neil-butler-1981.workers.dev/`. Its source is in
`worker/`; it needs the Production Enable Banking secrets before deployment.
