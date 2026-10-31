"""Local Enable Banking Account Information connector for MoneyBuddy.

This service is deliberately sandbox-oriented and read-only. It has no payment
routes and never logs tokens, private keys, account numbers, or transactions.
"""

from __future__ import annotations

import json
import os
import secrets
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

import jwt

CONFIG_PATH = Path(__file__).with_name("config.json")
DEFAULT_PORT = 8080
JWT_LIFETIME_SECONDS = 300


class ConnectorError(RuntimeError):
    """Raised when an Enable Banking request cannot be completed."""


class EnableBankingClient:
    def __init__(self, config: dict[str, str]) -> None:
        self.application_id = config["application_id"]
        self.private_key_path = Path(config["private_key_path"])
        self.redirect_url = config["redirect_url"]
        self.api_base_url = config.get("api_base_url", "https://api.enablebanking.com").rstrip("/")
        if not self.private_key_path.is_file():
            raise ConnectorError(f"Private key file not found: {self.private_key_path}")
        self._session_id: str | None = None
        self._session_lock = threading.Lock()

    def _token(self) -> str:
        try:
            private_key = self.private_key_path.read_bytes()
            now = int(time.time())
            return jwt.encode(
                {
                    "iss": "enablebanking.com",
                    "aud": "api.enablebanking.com",
                    "iat": now,
                    "exp": now + JWT_LIFETIME_SECONDS,
                },
                private_key,
                algorithm="RS256",
                headers={"kid": self.application_id},
            )
        except OSError as exc:
            raise ConnectorError("Unable to read the Enable Banking private key.") from exc

    def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        body = None
        headers = {
            "Authorization": f"Bearer {self._token()}",
            "Accept": "application/json",
        }
        if payload is not None:
            body = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self.api_base_url}{path}",
            data=body,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise ConnectorError(
                f"Enable Banking returned HTTP {exc.code}: {exc.reason}."
            ) from exc
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            raise ConnectorError("Enable Banking request failed.") from exc

    def list_aspsps(self, country: str = "GB") -> Any:
        query = urllib.parse.urlencode({"country": country})
        return self.request("GET", f"/aspsps?{query}")

    def application(self) -> Any:
        return self.request("GET", "/application")

    def start_authorization(self, aspsp_name: str, psu_type: str = "PERSONAL") -> str:
        state = secrets.token_urlsafe(32)
        response = self.request(
            "POST",
            "/auth",
            {
                "access": {
                    "valid_until": (
                        datetime.now(timezone.utc) + timedelta(days=90)
                    ).isoformat(),
                },
                "aspsp": {"country": "GB", "name": aspsp_name},
                "state": f"{state}-{uuid.uuid4()}",
                "redirect_url": self.redirect_url,
                "psu_type": psu_type.lower(),
                "language": "en",
            },
        )
        with self._session_lock:
            self._session_id = None
        return response["url"]

    def authorize_callback(self, code: str) -> Any:
        response = self.request("POST", "/sessions", {"code": code})
        with self._session_lock:
            self._session_id = response["session_id"]
        return response

    def session_data(self) -> Any:
        with self._session_lock:
            session_id = self._session_id
        if not session_id:
            raise ConnectorError("No authorised session is available.")
        return self.request("GET", f"/sessions/{session_id}")

    def account_data(self, account_id: str) -> dict[str, Any]:
        return {
            "details": self.request("GET", f"/accounts/{account_id}/details"),
            "balances": self.request("GET", f"/accounts/{account_id}/balances"),
            "transactions": self.request("GET", f"/accounts/{account_id}/transactions"),
        }


def load_config() -> dict[str, str]:
    path = Path(os.environ.get("MONEYBUDDY_ENABLE_CONFIG", str(CONFIG_PATH)))
    try:
        with path.open(encoding="utf-8") as file:
            config = json.load(file)
    except (OSError, json.JSONDecodeError) as exc:
        raise ConnectorError(f"Unable to load connector configuration: {path}") from exc
    for field in ("application_id", "private_key_path", "redirect_url"):
        if not config.get(field):
            raise ConnectorError(f"Missing connector configuration field: {field}")
    return config


class RequestHandler(BaseHTTPRequestHandler):
    client: EnableBankingClient

    def log_message(self, format: str, *args: object) -> None:
        print(f"[connector] {format % args}")

    def send_json(self, status: HTTPStatus, body: dict[str, Any]) -> None:
        encoded = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        try:
            if parsed.path == "/health":
                self.send_json(HTTPStatus.OK, {"status": "ok", "scope": "account_information"})
            elif parsed.path == "/application":
                self.send_json(HTTPStatus.OK, self.client.application())
            elif parsed.path == "/aspsps":
                self.send_json(HTTPStatus.OK, {"aspsps": self.client.list_aspsps()})
            elif parsed.path == "/connect":
                name = query.get("name", [None])[0]
                if not name:
                    self.send_json(HTTPStatus.BAD_REQUEST, {"error": "Missing bank name."})
                    return
                self.send_json(
                    HTTPStatus.OK,
                    {"authorization_url": self.client.start_authorization(name)},
                )
            elif parsed.path == "/enablebanking/callback":
                code = query.get("code", [None])[0]
                if not code:
                    self.send_json(HTTPStatus.BAD_REQUEST, {"error": "Missing authorization code."})
                    return
                session = self.client.authorize_callback(code)
                self.send_json(
                    HTTPStatus.OK,
                    {
                        "status": "authorised",
                        "session_id": session["session_id"],
                        "accounts": session.get("accounts", []),
                    },
                )
            elif parsed.path == "/session":
                self.send_json(HTTPStatus.OK, self.client.session_data())
            elif parsed.path == "/account":
                account_id = query.get("id", [None])[0]
                if not account_id:
                    self.send_json(HTTPStatus.BAD_REQUEST, {"error": "Missing account id."})
                    return
                self.send_json(HTTPStatus.OK, self.client.account_data(account_id))
            else:
                self.send_json(HTTPStatus.NOT_FOUND, {"error": "Not found."})
        except ConnectorError as exc:
            self.send_json(HTTPStatus.BAD_GATEWAY, {"error": str(exc)})


def main() -> None:
    client = EnableBankingClient(load_config())
    RequestHandler.client = client
    port = int(os.environ.get("MONEYBUDDY_CONNECTOR_PORT", DEFAULT_PORT))
    server = ThreadingHTTPServer(("127.0.0.1", port), RequestHandler)
    print(f"MoneyBuddy connector listening on http://127.0.0.1:{port}")
    server.serve_forever()


if __name__ == "__main__":
    main()
