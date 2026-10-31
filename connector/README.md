# Enable Banking connector

This is a local, sandbox-oriented Account Information connector for MoneyBuddy.
It has no payment routes.

## Setup

1. Copy `config.example.json` to `config.json`.
2. Verify that `private_key_path` points to the local Enable Banking PEM file.
3. Install the one dependency:

```powershell
Set-Location C:\MoneyBuddy\connector
python -m pip install -r requirements.txt
```

4. Start the connector:

```powershell
python .\server.py
```

The private key is read only by this local process. It is never sent to the
Android app or logged.

## Sandbox flow

Check the service:

```text
http://localhost:8080/health
```

List UK Account Information providers:

```text
http://localhost:8080/aspsps
```

Inspect the registered application (non-secret metadata):

```text
http://localhost:8080/application
```

Start authorisation after choosing an ASPSP name from that response:

```text
http://localhost:8080/connect?name=ASPSP_NAME
```

Open the returned `authorization_url` in a browser. Enable Banking will redirect
back to the configured callback. After authorisation, use `/session` and then
`/account?id=...` with an account ID from the session response.

The connector does not accept credentials, tokens, or payment requests through
its own HTTP interface.
