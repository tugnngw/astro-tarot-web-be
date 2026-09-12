/**
 * AstroTarot Edge Gateway — Cloudflare Worker
 *
 * Proxies traffic to the Render API origin with:
 * - IP rate limits (general API + stricter auth)
 * - scanner / junk path blocking
 * - WebSocket upgrade passthrough (/ws)
 * - security response headers
 * - no caching of authenticated API responses
 *
 * True multi-origin Load Balancing needs a custom domain + Cloudflare LB (paid).
 * Unmetered DDoS mitigation for HTTP kicks in when the hostname is proxied
 * (orange cloud) on a Cloudflare zone — see ../docs/CLOUDFLARE.md.
 */

export interface Env {
  ORIGIN_API: string;
  API_RATE_LIMITER: RateLimit;
  AUTH_RATE_LIMITER: RateLimit;
}

interface RateLimit {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

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

/** Webhooks must not be rate-limited the same as browsers (retries / bursts). */
const BYPASS_RATE_LIMIT_PREFIXES = [
  "/api/v1/payments/payos/webhook",
  "/api/v1/payos/webhook",
];

const SECURITY_HEADERS: Record<string, string> = {
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
  "Cross-Origin-Resource-Policy": "cross-origin",
};

function clientIp(request: Request): string {
  return (
    request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
    "unknown"
  );
}

function normalizePath(pathname: string): string {
  try {
    return decodeURIComponent(pathname).toLowerCase();
  } catch {
    return pathname.toLowerCase();
  }
}

function isBlockedPath(pathname: string): boolean {
  const p = normalizePath(pathname);
  return BLOCKED_PATH_PREFIXES.some(
    (prefix) => p === prefix || p.startsWith(`${prefix}/`) || p.startsWith(prefix),
  );
}

function isAuthPath(pathname: string): boolean {
  const p = pathname.toLowerCase();
  return AUTH_PATH_PREFIXES.some((prefix) => p.startsWith(prefix));
}

function bypassRateLimit(pathname: string): boolean {
  const p = pathname.toLowerCase();
  return BYPASS_RATE_LIMIT_PREFIXES.some((prefix) => p.startsWith(prefix));
}

function withSecurityHeaders(response: Response): Response {
  const headers = new Headers(response.headers);
  for (const [k, v] of Object.entries(SECURITY_HEADERS)) {
    headers.set(k, v);
  }
  // Never let CDNs cache authenticated API by accident
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

function json(status: number, body: Record<string, unknown>): Response {
  return withSecurityHeaders(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    }),
  );
}

async function enforceRateLimit(
  env: Env,
  request: Request,
  pathname: string,
): Promise<Response | null> {
  if (bypassRateLimit(pathname) || request.method === "OPTIONS") {
    return null;
  }

  const ip = clientIp(request);
  const limiter = isAuthPath(pathname) ? env.AUTH_RATE_LIMITER : env.API_RATE_LIMITER;
  const key = `${isAuthPath(pathname) ? "auth" : "api"}:${ip}`;
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

function buildOriginRequest(request: Request, originBase: string): Request {
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
  // Avoid sending workers.dev Host to Spring (breaks virtual hosting / redirects)
  headers.set("Host", origin.host);

  return new Request(target.toString(), {
    method: request.method,
    headers,
    body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
    redirect: "manual",
    // @ts-expect-error duplex required for streaming body in Workers
    duplex: request.body ? "half" : undefined,
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
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

    const originReq = buildOriginRequest(request, env.ORIGIN_API.replace(/\/$/, ""));

    try {
      const upstream = await fetch(originReq);
      // WebSocket / streaming: pass through as-is with security headers where safe
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
