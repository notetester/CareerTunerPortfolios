import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import {
  PUBLIC_DEMO_PASSWORD,
  consumePublicDemoAutoEntry,
  getPublicDemoAccounts,
  resolvePublicDemoDestination,
  safePublicDemoReturnTo,
} from "../src/app/pages/publicDemoLogin.ts";

test("공개 데모 계정은 목 모드에서만 노출된다", () => {
  assert.deepEqual(getPublicDemoAccounts(false), []);

  const accounts = getPublicDemoAccounts(true);
  assert.deepEqual(accounts.map(({ role }) => role), ["user", "admin"]);
  assert.deepEqual(accounts.map(({ email }) => email), [
    "demo@careertuner.dev",
    "admin@careertuner.dev",
  ]);
  assert.ok(accounts.every(({ password }) => password === PUBLIC_DEMO_PASSWORD));
});

test("demo 쿼리는 목 모드에서 역할과 복귀 경로를 한 번만 소비한다", () => {
  assert.deepEqual(
    consumePublicDemoAutoEntry("?demo=user&returnTo=%2Fapplications%2F42", true, false),
    {
      role: "user",
      returnTo: "/applications/42",
      searchAfterConsumption: "?returnTo=%2Fapplications%2F42",
    },
  );
  assert.deepEqual(
    consumePublicDemoAutoEntry("?demo=admin", true, false),
    { role: "admin", returnTo: null, searchAfterConsumption: "" },
  );
  assert.equal(consumePublicDemoAutoEntry("?demo=user", false, false), null);
  assert.equal(consumePublicDemoAutoEntry("?demo=user", true, true), null);
  assert.equal(consumePublicDemoAutoEntry("?demo=super-admin", true, false), null);
});

test("역할별 기본 목적지는 사용자 대시보드와 관리자 콘솔이다", () => {
  assert.equal(resolvePublicDemoDestination("user", null), "/dashboard");
  assert.equal(resolvePublicDemoDestination("admin", null), "/admin");
  assert.equal(
    resolvePublicDemoDestination("user", "/applications/42?tab=analysis#result"),
    "/applications/42?tab=analysis#result",
  );
  assert.equal(resolvePublicDemoDestination("admin", "/admin/users?page=2"), "/admin/users?page=2");
});

test("외부·프로토콜 상대·역슬래시·인코딩 우회 복귀 경로를 거부한다", () => {
  const unsafeValues = [
    "https://evil.example/steal",
    "//evil.example/steal",
    "/\\evil.example/steal",
    "/%5Cevil.example/steal",
    "/%2F%2Fevil.example/steal",
    "/dashboard\n/admin",
  ];

  for (const value of unsafeValues) {
    assert.equal(safePublicDemoReturnTo(value, "/dashboard"), "/dashboard", value);
  }
  assert.equal(resolvePublicDemoDestination("admin", "//evil.example"), "/admin");
});

test("로그인 화면은 목 모드 계정 목록과 일회성 자동 진입을 실제 UI에 연결한다", async () => {
  const source = await readFile(new URL("../src/app/pages/Login.tsx", import.meta.url), "utf8");

  assert.match(source, /const demoAccounts = getPublicDemoAccounts\(USE_MOCK\)/);
  assert.match(source, /USE_MOCK && mode === "login" && demoAccounts\.length > 0/);
  assert.match(source, /data-testid="public-demo-login"/);
  assert.match(source, /data-testid=\{`public-demo-\$\{account\.role\}`\}/);
  assert.match(source, /consumePublicDemoAutoEntry\(/);
  assert.match(source, /autoDemoEntryAttempted\.current = true/);
  assert.match(source, /handlePublicDemoLogin\(autoEntry\.role, autoEntry\.returnTo\)/);
});

test("PWA는 공개 설명서와 지식 지도를 SPA 폴백으로 가로채지 않는다", async () => {
  const viteConfig = await readFile(new URL("../vite.config.ts", import.meta.url), "utf8");

  assert.match(viteConfig, /navigateFallbackDenylist:/);
  assert.match(viteConfig, /\/\\\/docs\(\?:\\\/\|\$\)\//);
  assert.match(viteConfig, /\/\\\/Obsidian\(\?:\\\/\|\$\)\//);
});
