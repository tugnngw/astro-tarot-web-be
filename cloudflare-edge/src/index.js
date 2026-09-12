const BLOCKED_PATH_PREFIXES = [
  "/wp-admin",
  "/wp-login",
  "/wordpress",
  "/phpmyadmin",
  "/.env",
  "/.git",
  "/actuator",
  "/server-status",
  "/cgi-bin",
];

const AUTH_PATH_PREFIXES = ["/auth/", "/oauth2/", "/api/v1/auth/"];
const BYPASS_RATE_LIMIT_PREFIXES = ["/api/v1/payments/payos/webhook"];

const SECURITY_HEADERS = {
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
  "Cross-Origin-Resource-Policy": "cross-origin",
};

function clientIp(request) {
  return (
    request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
    "unknown"
  );
}

function normalizePath(pathname) {
  try {
    return decodeURIComponent(pathname).toLowerCase();
  } catch {
    return pathname.toLowerCase();
  }
}

function isBlockedPath(pathname) {
  const p = normalizePath(pathname);
  return BLOCKED_PATH_PREFIXES.some(
    (prefix) => p === prefix || p.startsWith(prefix + "/") || p.startsWith(prefix),
  );
}

function isAuthPath(pathname) {
  const p = pathname.toLowerCase();
  return AUTH_PATH_PREFIXES.some((prefix) => p.startsWith(prefix));
}

function bypassRateLimit(pathname) {
  const p = pathname.toLowerCase();
  return BYPASS_RATE_LIMIT_PREFIXES.some((prefix) => p.startsWith(prefix));
}

function withSecurityHeaders(response) {
  const headers = new Headers(response.headers);
  for (const [k, v] of Object.entries(SECURITY_HEADERS)) {
    headers.set(k, v);
  }
  if (!headers.has("Cache-Control")) {
    headers.set("Cache-Control", "private, no-store");
  }
  headers.set("X-AstroTarot-Edge", "cf-worker");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

function json(status, body) {
  return withSecurityHeaders(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    }),
  );
}

async function enforceRateLimit(env, request, pathname) {
  if (bypassRateLimit(pathname) || request.method === "OPTIONS") {
    return null;
  }
  const ip = clientIp(request);
  const limiter = isAuthPath(pathname) ? env.AUTH_RATE_LIMITER : env.API_RATE_LIMITER;
  const key = (isAuthPath(pathname) ? "auth:" : "api:") + ip;
  const { success } = await limiter.limit({ key });
  if (!success) {
    return json(429, {
      success: false,
      message: "Too many requests. Slow down and try again.",
      code: "EDGE_RATE_LIMITED",
    });
  }
  return null;
}

function stripTrailingSlash(value) {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}

function buildOriginRequest(request, originBase) {
  const incoming = new URL(request.url);
  const origin = new URL(originBase);
  const target = new URL(incoming.pathname + incoming.search, origin);
  const headers = new Headers(request.headers);
  headers.set("X-Forwarded-Proto", incoming.protocol.replace(":", ""));
  headers.set("X-Forwarded-Host", incoming.host);
  if (!headers.has("X-Forwarded-For")) {
    const ip = clientIp(request);
    if (ip !== "unknown") headers.set("X-Forwarded-For", ip);
  }
  headers.set("Host", origin.host);
  const init = {
    method: request.method,
    headers,
    body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
    redirect: "manual",
  };
  if (request.body) init.duplex = "half";
  return new Request(target.toString(), init);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/__edge/health") {
      return json(200, {
        ok: true,
        edge: "astrotarot-edge",
        origin: env.ORIGIN_API,
        ts: new Date().toISOString(),
      });
    }

    if (isBlockedPath(url.pathname)) {
      return json(403, {
        success: false,
        message: "Forbidden",
        code: "EDGE_BLOCKED_PATH",
      });
    }

    const limited = await enforceRateLimit(env, request, url.pathname);
    if (limited) return limited;

    const originReq = buildOriginRequest(request, stripTrailingSlash(String(env.ORIGIN_API)));
    try {
      const upstream = await fetch(originReq);
      if (request.headers.get("Upgrade")?.toLowerCase() === "websocket") {
        return upstream;
      }
      return withSecurityHeaders(upstream);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Upstream fetch failed";
      return json(502, {
        success: false,
        message: "Origin unreachable",
        code: "EDGE_ORIGIN_DOWN",
        detail: message,
      });
    }
  },
};
