# MoneyBuddy Cloudflare Worker

This Worker is the HTTPS server-side boundary for Enable Banking Production.
It is Account Information only. It has no payment routes.

Worker URL:

```text
https://moneybuddy-api.neil-butler-1981.workers.dev/
```

## Required Worker secrets

Configure these in Cloudflare Worker **Settings → Variables and Secrets**:

```text
ENABLE_BANKING_APPLICATION_ID
ENABLE_BANKING_PRIVATE_KEY
ENABLE_BANKING_REDIRECT_URL
```

Set `ENABLE_BANKING_REDIRECT_URL` to:

```text
https://moneybuddy-api.neil-butler-1981.workers.dev/enablebanking/callback
```

Never put the RSA private key in this repository or in the Android application.

## Routes

```text
GET /health
GET /aspsps?country=GB
GET /connect?name=<Enable Banking ASPSP name>
GET /enablebanking/callback
```

The callback validates the OAuth state in an HttpOnly, Secure cookie before
exchanging the authorisation code. It returns only the resulting session
identifier, accounts, and consent information. Persistent synchronisation and
Android authentication will be added after the Production provider connection
has been confirmed.
