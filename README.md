# MoneyBuddy

Stage 1 of the personal finance organiser for the Samsung Galaxy Tab S6 Lite.

The current build is intentionally local-only. It uses synthetic transactions stored in
Room and does not connect to HSBC or any other banking service.

## Build and install

From PowerShell:

```powershell
Set-Location C:\MoneyBuddy
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Connect the tablet with USB debugging enabled and accept the Android debugging prompt.

Stage 3 adds a free **bank CSV import** (Settings → Import bank CSV). Live bank
connections are not yet available: see STAGE3-OPEN-BANKING.md.

## Stage 1 scope

- Tablet dashboard with persistent navigation
- Adaptive portrait and landscape layouts
- Persisted light and dark themes
- Balance, income, and spending summaries
- Spending breakdown chart
- Recent and all-transaction views
- Example upcoming payments
- Local Room database seeded with clearly labelled demonstration data
- Settings screen showing the future banking connection state
