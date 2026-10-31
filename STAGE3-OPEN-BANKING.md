# Stage 3 — HSBC Open Banking investigation

Last updated: 30 October 2026

## Scope

Stage 3 is limited to investigating and designing a secure, read-only HSBC UK
connection. No HSBC credentials, provider secrets, payment initiation, or live
banking calls are included in the Android app.

The Enable Banking sandbox application currently exposes both Account
Information and Payment Initiation services. MoneyBuddy will request and
implement Account Information only. No payment endpoint, permission, UI, or
client flow will be added. This sandbox setting can be reviewed before any
production application is created.

## Important conclusion

A production connection cannot safely be implemented as an Android-only feature.
The Open Banking provider secret and authorisation-code exchange must be handled
by a server-side component. Provider secrets must never be embedded in the APK.

HSBC login credentials must also never be collected or stored by MoneyBuddy.
The user must authenticate through the bank/provider consent flow.

## Provider comparison

| Provider | Advantages | Constraints |
|---|---|---|
| Enable Banking | Official documentation describes restricted production access for accounts linked by the developer and explicitly mentions individual non-commercial use; email sign-up and sandbox are available; supports account information APIs | Production use is restricted to linked accounts until an agreement is signed; current commercial pricing is not published as a simple public plan; application private key and JWT authentication must be kept server-side; HSBC UK account support must be checked in the control panel |
| GoCardless Bank Account Data (formerly Nordigen) | Clear account-information flow; read-only account, balance, and transaction data; documented consent journey; historically suitable for low-volume projects | Current pricing and free-tier limits require confirmation; non-verified account limits require confirmation; documented rate limits can be as low as four calls per day per account and endpoint; server-side secret required |
| TrueLayer | Established UK Open Banking provider; regulated provider-hosted consent flow; account and transaction APIs | Current production pricing/free usage and individual-developer eligibility require confirmation; server-side credentials required |
| Yapily | Account-information APIs; Yapily Connect can provide the regulated-provider route | Current pricing/free usage and individual-developer eligibility require confirmation; native-app flow and server-side credentials need careful implementation |

## Enable Banking findings

The official Enable Banking documentation provides the strongest evidence found
so far for a no-subscription, single-user evaluation:

- The API documentation supports account information, balances, and
  transactions.
- New accounts can be created through the Control Panel with email
  authentication.
- A production application can be activated by linking the developer's own
  account.
- The documentation explicitly says restricted production mode supports
  individual non-commercial use.
- Restricted mode only permits data access for accounts linked to the
  application. It is not approval for a public multi-user product.
- The application uses a generated private key and JWT authentication. This key
  must not be put in the Android APK.

  The sandbox private key is currently stored at
  `C:\MoneyBuddy\secret\023f6c23-1c2e-4fe0-b377-56bf26f4c8e9.pem`, at the user's
  request. The `secret` directory and PEM files are excluded by
  [.gitignore](./.gitignore). The key contents have not been read or copied by
  MoneyBuddy tooling. Keeping secrets outside the project remains preferable.

Official references:

- [Enable Banking home](https://enablebanking.com/)
- [Quick Start](https://enablebanking.com/docs/api/quick-start/)
- [Linked accounts and restricted production access](https://enablebanking.com/docs/api/linked-accounts/)
- [API reference](https://enablebanking.com/docs/api/reference/)
- [Enable Banking terms](https://enablebanking.com/terms/)

The documentation does not provide a simple public subscription price for this
restricted individual mode. Therefore, "free" should be treated as
"potentially free for this personal non-commercial use case, subject to the
Control Panel terms and any bank/provider charges", not as a guaranteed
unlimited free plan.

## Recommended candidate

Enable Banking is now the leading candidate for MoneyBuddy's personal,
single-user, read-only use case because it explicitly documents restricted
production access for linked own accounts and individual non-commercial use.
GoCardless Bank Account Data remains the fallback candidate if Enable Banking
does not support the specific HSBC UK account or if its terms do not meet the
required cost.

This is a candidate, not an implementation approval. Before writing provider
code, confirm:

1. Sign in to the Enable Banking Control Panel and confirm that HSBC UK is
   listed as an available UK ASPSP.
2. Confirm that an HSBC UK personal current account can be linked in restricted
   production mode.
3. Confirm that restricted individual non-commercial use has no subscription
   fee or unexpected per-account charge.
4. Confirm the terms for storing transaction data locally.
5. Confirm the consent duration and re-authentication behaviour.
6. Confirm whether Enable Banking requires any additional company, regulatory,
   or identity verification for this restricted personal use.

## Proposed secure architecture

```text
MoneyBuddy Android app
        |
        | HTTPS, app session authentication
        v
Small MoneyBuddy integration service
        |
        | Provider secret stored as server-side secret
        v
Open Banking provider
        |
        | Redirected bank consent and SCA
        v
HSBC UK
```

### Android responsibilities

- Start a connection request through the MoneyBuddy service.
- Open the provider's HTTPS consent URL in a browser/custom tab.
- Receive only a short-lived app callback or completed-connection result.
- Request account, balance, and transaction data from the MoneyBuddy service.
- Store only the minimum required local data.
- Show connection status and last successful synchronisation.
- Allow the user to disconnect and delete locally stored financial data.

### Server responsibilities

- Store provider secrets outside source control and the APK.
- Create and manage provider requisitions/consent sessions.
- Handle provider OAuth-style callbacks.
- Exchange authorisation codes and refresh tokens.
- Retrieve read-only account, balance, and transaction data.
- Normalise provider responses into MoneyBuddy's internal data model.
- Avoid logging account numbers, tokens, transaction descriptions, or other
  personal financial information.

## Deliberately excluded

- Payment initiation or money transfers.
- Direct HSBC website scraping.
- Storing HSBC passwords.
- Embedding provider secrets in Android resources or BuildConfig.
- Adding a cloud backend before provider eligibility and pricing are confirmed.
- Automatic categorisation; that remains a later stage.

## Next decision

## Sandbox test result

The local connector was tested on 31 October 2026:

- `/health` responded successfully.
- Enable Banking JWT authentication succeeded.
- `/application` returned the active `MoneyBuddy Sandbox` application.
- The application reports Account Information (`AIS`) and Payment Initiation
  (`PIS`) services.
- The application reports no `GB` country support.
- `/aspsps?country=GB` returned an empty ASPSP list.

Therefore the sandbox cannot currently be used to test an HSBC UK connection.
This is a provider/environment limitation, not an Android or connector failure.

Use Enable Banking's Control Panel to confirm whether HSBC UK is available for a
restricted production application, link the intended personal account only if
the displayed terms and cost are acceptable, and review the consent scope.
If HSBC UK is unavailable or a fee is required, evaluate GoCardless Bank
Account Data next rather than adding provider-specific Android code prematurely.

## Cloudflare Worker

A Cloudflare Worker has been created at:

`https://moneybuddy-api.neil-butler-1981.workers.dev/`

The Worker source is under [worker/](./worker). It is designed to replace the
local callback with this public HTTPS callback:

`https://moneybuddy-api.neil-butler-1981.workers.dev/enablebanking/callback`

The Worker has not been configured with Production secrets yet. The Production
private key must be added through Cloudflare encrypted Worker secrets and must
not be committed to this repository.
