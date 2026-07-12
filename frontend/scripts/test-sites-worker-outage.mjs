import assert from "node:assert/strict";
import { afterEach, test } from "node:test";

import worker from "../worker/index.ts";

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
});

function backupHealthRequest() {
  return new Request("https://backup.example/__backup/health");
}

const unusedAssets = {
  fetch() {
    throw new Error("backup health must not read static assets");
  },
};

const unusedContext = {
  waitUntil() {},
  passThroughOnException() {},
};

test("Sites 백업 health는 AWS readiness와 DB 상태를 기준으로 UP을 반환한다", async () => {
  let requestedUrl = "";
  globalThis.fetch = async (input, init) => {
    const request = new Request(input, init);
    requestedUrl = request.url;
    assert.equal(request.headers.get("X-CareerTuner-Frontend-Client"), "sites");
    return Response.json({ success: true, data: { status: "UP", db: "UP" } });
  };

  const response = await worker.fetch(backupHealthRequest(), { ASSETS: unusedAssets }, unusedContext);

  assert.equal(requestedUrl, "https://careertuner.kro.kr/api/health/ready");
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    status: "UP",
    frontend: "codex-sites",
    upstreamStatus: 200,
  });
});

test("AWS readiness가 503이면 Sites 백업 health도 DEGRADED로 내려간다", async () => {
  globalThis.fetch = async () => Response.json(
    { success: false, code: "SERVICE_UNAVAILABLE" },
    { status: 503 },
  );

  const response = await worker.fetch(backupHealthRequest(), { ASSETS: unusedAssets }, unusedContext);

  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), {
    status: "DEGRADED",
    frontend: "codex-sites",
    upstreamStatus: null,
  });
});

test("AWS readiness 응답이 비정상이면 200이어도 복구 완료로 오인하지 않는다", async () => {
  globalThis.fetch = async () => Response.json({ success: true, data: { status: "DOWN" } });

  const response = await worker.fetch(backupHealthRequest(), { ASSETS: unusedAssets }, unusedContext);

  assert.equal(response.status, 503);
  assert.equal((await response.json()).status, "DEGRADED");
});
