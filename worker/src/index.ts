interface Env {
  ENABLE_BANKING_APPLICATION_ID: string;
  ENABLE_BANKING_PRIVATE_KEY: string;
  ENABLE_BANKING_REDIRECT_URL: string;
  ENABLE_BANKING_API_BASE_URL?: string;
}

const API_BASE_URL = "https://api.enablebanking.com";
const STATE_COOKIE = "moneybuddy_oauth_state";
const STATE_MAX_AGE_SECONDS = 600;

function base64Url(bytes: ArrayBuffer | Uint8Array): string {
  const data = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = "";
  for (const byte of data) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function textBase64Url(value: string): string {
  return base64Url(new TextEncoder().encode(value));
}

function pemToBytes(pem: string): ArrayBuffer {
  const base64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes.buffer;
}

async function createJwt(env: Env): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = textBase64Url(JSON.stringify({
    alg: "RS256",
    kid: env.ENABLE_BANKING_APPLICATION_ID,
    typ: "JWT",
  }));
  const payload = textBase64Url(JSON.stringify({
    iss: "enablebanking.com",
    aud: "api.enablebanking.com",
    iat: now,
    exp: now + 300,
  }));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(env.ENABLE_BANKING_PRIVATE_KEY),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${payload}`),
  );
  return `${header}.${payload}.${base64Url(signature)}`;
}

async function enableBankingRequest(
  env: Env,
  path: string,
  method = "GET",
  body?: unknown,
): Promise<unknown> {
  const token = await createJwt(env);
  const response = await fetch(`${env.ENABLE_BANKING_API_BASE_URL ?? API_BASE_URL}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`Enable Banking returned HTTP ${response.status}.`);
  }
  return response.json();
}

function jsonResponse(body: unknown, status = 200, headers?: HeadersInit): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      ...headers,
    },
  });
}

function htmlResponse(title: string, message: string, status = 200): Response {
  const escaped = message.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  return new Response(
    `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>${title}</title></head>` +
      `<body><h1>${title}</h1><p>${escaped}</p></body></html>`,
    {
      status,
      headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" },
    },
  );
}

function getCookie(request: Request, name: string): string | undefined {
  const cookies = request.headers.get("Cookie")?.split(";") ?? [];
  const prefix = `${name}=`;
  return cookies.map((cookie) => cookie.trim()).find((cookie) => cookie.startsWith(prefix))
    ?.slice(prefix.length);
}

async function startAuthorization(request: Request, env: Env): Promise<Response> {
  const name = new URL(request.url).searchParams.get("name");
  if (!name) return jsonResponse({ error: "Missing ASPSP name." }, 400);

  const state = crypto.randomUUID();
  const response = await enableBankingRequest(env, "/auth", "POST", {
    access: {
      valid_until: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString(),
    },
    aspsp: { country: "GB", name },
    state,
    redirect_url: env.ENABLE_BANKING_REDIRECT_URL,
    psu_type: "personal",
    language: "en",
  }) as { url: string };

  return jsonResponse(
    { authorization_url: response.url },
    200,
    {
      "Set-Cookie": `${STATE_COOKIE}=${state}; Max-Age=${STATE_MAX_AGE_SECONDS}; Path=/; HttpOnly; Secure; SameSite=Lax`,
    },
  );
}

async function handleCallback(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const code = url.searchParams.get("code");
  const state = url.searchParams.get("state");
  const expectedState = getCookie(request, STATE_COOKIE);
  if (!code || !state || !expectedState || state !== expectedState) {
    return htmlResponse("Authorisation failed", "The authorisation response could not be verified.", 400);
  }

  const session = await enableBankingRequest(env, "/sessions", "POST", { code }) as {
    session_id: string;
    accounts: unknown[];
    access: unknown;
  };
  return jsonResponse({
    status: "authorised",
    session_id: session.session_id,
    accounts: session.accounts,
    access: session.access,
  }, 200, {
    "Set-Cookie": `${STATE_COOKIE}=; Max-Age=0; Path=/; HttpOnly; Secure; SameSite=Lax`,
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    try {
      if (request.method !== "GET") return jsonResponse({ error: "Method not allowed." }, 405);
      if (url.pathname === "/health") {
        return jsonResponse({ status: "ok", scope: "account_information" });
      }
      if (url.pathname === "/aspsps") {
        const country = url.searchParams.get("country") ?? "GB";
        return jsonResponse(await enableBankingRequest(
          env,
          `/aspsps?country=${encodeURIComponent(country)}`,
        ));
      }
      if (url.pathname === "/connect") return startAuthorization(request, env);
      if (url.pathname === "/enablebanking/callback") return handleCallback(request, env);
      return jsonResponse({ error: "Not found." }, 404);
    } catch (error) {
      console.error("MoneyBuddy Worker request failed", error instanceof Error ? error.message : "unknown error");
      return jsonResponse({ error: "The banking request could not be completed." }, 502);
    }
  },
};
