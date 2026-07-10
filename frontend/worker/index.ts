const AWS_ORIGIN = "https://careertuner.kro.kr";

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

  return fetch(upstreamRequest);
}

const worker = {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (isApiPath(url.pathname)) {
      return proxyToAws(request);
    }

    if (url.pathname === "/__backup/health") {
      const upstream = await fetch(`${AWS_ORIGIN}/api/health`, {
        headers: { Accept: "application/json" },
      }).catch(() => null);

      return Response.json(
        {
          status: upstream?.ok ? "UP" : "DEGRADED",
          frontend: "codex-sites",
          upstreamStatus: upstream?.status ?? null,
        },
        { status: upstream?.ok ? 200 : 503 },
      );
    }

    return env.ASSETS.fetch(request);
  },
};

export default worker;
