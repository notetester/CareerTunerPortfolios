const AWS_ORIGIN = "https://careertuner.example.com";
const FRONTEND_CLIENT_HEADER = "X-CareerTuner-Frontend-Client";
const FRONTEND_ORIGIN_HEADER = "X-CareerTuner-Frontend-Origin";
const HEALTH_TIMEOUT_MS = 5_000;
const MAX_PATH_DECODE_PASSES = 8;

interface Env {
  ASSETS: {
    fetch(request: Request): Promise<Response>;
  };
}

interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
  passThroughOnException(): void;
}

function isApiPath(pathname: string): boolean {
  return pathname === "/api" || pathname.startsWith("/api/");
}

/** 인코딩·중복 슬래시·dot segment를 정규화해 보안 경로 검사가 원문 표기에 우회되지 않게 한다. */
function canonicalizeApiPath(pathname: string): string | null {
  let decoded = pathname;
  let stable = false;

  for (let pass = 0; pass < MAX_PATH_DECODE_PASSES; pass += 1) {
    let next: string;
    try {
      next = decodeURIComponent(decoded);
    } catch {
      return null;
    }
    if (next === decoded) {
      stable = true;
      break;
    }
    decoded = next;
  }
  if (!stable || !decoded.startsWith("/")) return null;

  // 디코딩된 구분자·matrix parameter·제어문자는 upstream별 해석 차이가 생기므로 거부한다.
  if (
    decoded.includes("?") ||
    decoded.includes("#") ||
    decoded.includes("\\") ||
    decoded.includes(";") ||
    [...decoded].some((char) => {
      const code = char.charCodeAt(0);
      return code < 0x20 || code === 0x7f;
    })
  ) {
    return null;
  }

  const segments: string[] = [];
  for (const segment of decoded.split("/")) {
    if (!segment || segment === ".") continue;
    if (segment === "..") {
      if (segments.length === 0) return null;
      segments.pop();
      continue;
    }
    segments.push(segment);
  }
  return `/${segments.join("/")}`;
}

function isBlockedFinancialMutation(request: Request, pathname: string): boolean {
  const method = request.method.toUpperCase();
  if (method === "GET" || method === "HEAD" || method === "OPTIONS") return false;
  return (
    /^\/api\/payments\/toss\/(?:ready|confirm|cancel)$/.test(pathname) ||
    /^\/api\/billing\/(?:subscribe|credits\/purchase|subscription\/cancel)$/.test(pathname) ||
    pathname === "/api/billing/refunds" ||
    /^\/api\/admin\/refunds\/[^/]+\/(?:approve|reject)$/.test(pathname) ||
    pathname === "/api/admin/credits/adjust" ||
    pathname === "/api/coupons/redeem" ||
    /^\/api\/admin\/rewards(?:\/|$)/.test(pathname) ||
    (
      pathname === "/api/admin/plans/policy-changes" ||
      /^\/api\/admin\/plans\/policy-changes\/[^/]+\/cancel$/.test(pathname)
    ) ||
    pathname === "/api/admin/refund-policies/draft" ||
    /^\/api\/admin\/refund-policies\/[^/]+\/publish$/.test(pathname)
  );
}

function noStoreJson(body: unknown, status: number): Response {
  return Response.json(body, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

function blockedPaymentResponse(): Response {
  return noStoreJson(
    {
      success: false,
      code: "SITES_PAYMENT_BLOCKED",
      message: "Sites 백업 화면에서는 결제·환불·크레딧·리워드 등 금융성 변경을 진행할 수 없습니다. 운영 웹을 이용해 주세요.",
    },
    403,
  );
}

function invalidApiPathResponse(): Response {
  return noStoreJson(
    {
      success: false,
      code: "INVALID_API_PATH",
      message: "요청 경로 형식이 올바르지 않습니다.",
    },
    400,
  );
}

function upstreamUnavailableResponse(): Response {
  return noStoreJson(
    {
      success: false,
      code: "UPSTREAM_UNAVAILABLE",
      message: "운영 API에 연결할 수 없습니다.",
    },
    503,
  );
}

async function proxyToAws(request: Request): Promise<Response> {
  const sourceUrl = new URL(request.url);
  const targetUrl = new URL(sourceUrl.pathname + sourceUrl.search, AWS_ORIGIN);
  const headers = new Headers(request.headers);
  const clientIp = headers.get("cf-connecting-ip");

  // 브라우저 Origin을 AWS Spring CORS에 전달하지 않고, 고정된 upstream만 호출한다.
  headers.delete("origin");
  headers.delete("host");
  headers.delete("forwarded");
  headers.delete("x-forwarded-for");
  headers.delete("x-real-ip");
  headers.delete("x-forwarded-host");
  headers.delete("x-forwarded-proto");
  headers.delete("cf-connecting-ip");
  // 브라우저가 임의로 보낸 식별값은 전달하지 않고 Sites라는 named client만 고정한다.
  headers.delete(FRONTEND_CLIENT_HEADER);
  headers.delete(FRONTEND_ORIGIN_HEADER);
  headers.set(FRONTEND_CLIENT_HEADER, "sites");

  if (clientIp) {
    headers.set("x-forwarded-for", clientIp);
    headers.set("x-real-ip", clientIp);
  }
  headers.set("x-forwarded-host", sourceUrl.host);
  headers.set("x-forwarded-proto", "https");

  const init: RequestInit & { duplex?: "half" } = {
    method: request.method,
    headers,
    body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
    redirect: "manual",
  };
  if (init.body) init.duplex = "half";

  const upstreamRequest = new Request(targetUrl, init);

  try {
    return await fetch(upstreamRequest);
  } catch {
    // Worker 런타임 예외(500)로 숨기지 않고 클라이언트 failover가 판별 가능한 503으로 정규화한다.
    return upstreamUnavailableResponse();
  }
}

async function checkAwsHealth(): Promise<{ up: boolean; upstreamStatus: number | null }> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), HEALTH_TIMEOUT_MS);
  try {
    // readiness(DB 왕복 포함)로 확인한다 — liveness(/api/health)는 백엔드가 DB 없이도 부팅하도록
    // 설계돼(initialization-fail-timeout:-1) DB 가 끊겨도 UP 이라, DB-only 장애를 놓친다.
    // ready 는 DB 불가 시 503 을 반환하므로 이 확인이 outage 폴백을 올바르게 발동시킨다.
    const upstream = await fetch(`${AWS_ORIGIN}/api/health/ready`, {
      headers: { Accept: "application/json", [FRONTEND_CLIENT_HEADER]: "sites" },
      cache: "no-store",
      signal: controller.signal,
    });
    const payload = (await upstream.clone().json().catch(() => null)) as {
      data?: { status?: unknown };
    } | null;
    return {
      up: upstream.ok && payload?.data?.status === "UP",
      upstreamStatus: upstream.status,
    };
  } catch {
    return { up: false, upstreamStatus: null };
  } finally {
    clearTimeout(timeout);
  }
}

const worker = {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (isApiPath(url.pathname)) {
      const canonicalPath = canonicalizeApiPath(url.pathname);
      if (!canonicalPath || !isApiPath(canonicalPath)) {
        return invalidApiPathResponse();
      }
      if (isBlockedFinancialMutation(request, canonicalPath)) {
        return blockedPaymentResponse();
      }
      return proxyToAws(request);
    }

    if (url.pathname === "/__backup/health") {
      const health = await checkAwsHealth();

      return noStoreJson(
        {
          status: health.up ? "UP" : "DEGRADED",
          frontend: "codex-sites",
          upstreamStatus: health.up ? health.upstreamStatus : null,
        },
        health.up ? 200 : 503,
      );
    }

    const assetResponse = await env.ASSETS.fetch(request);
    const acceptsHtml = request.headers.get("accept")?.includes("text/html") ?? false;

    if (assetResponse.status === 404 && request.method === "GET" && acceptsHtml) {
      // Assets는 /index.html을 /로 307 정규화한다. 그 응답을 그대로 돌려주면 브라우저의
      // /login 같은 SPA 딥링크가 /로 바뀌므로, 루트 문서를 내부에서 직접 가져온다.
      const indexUrl = new URL("/", request.url);
      return env.ASSETS.fetch(new Request(indexUrl, request));
    }

    return assetResponse;
  },
};

export default worker;
