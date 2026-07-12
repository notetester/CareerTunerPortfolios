import assert from "node:assert/strict";
import test from "node:test";

import { toAppPath } from "../src/platform/deepLinkCore.mjs";
import { initializeDeepLinkRuntime } from "../src/platform/deepLinkRuntimeCore.mjs";
import { bootstrapNativeRuntime } from "../src/platform/nativeBootstrapCore.mjs";

test("공통 네이티브 bootstrap은 플랫폼 준비 뒤 shell/deep-link/push를 모두 초기화한다", async () => {
  let checks = 0;
  const initialized = [];
  const ready = await bootstrapNativeRuntime({
    isNative: () => {
      checks += 1;
      return checks >= 3;
    },
    initializers: [
      () => initialized.push("shell"),
      () => initialized.push("deep-link"),
      () => initialized.push("push"),
    ],
    retryDelaysMs: [0, 1, 1],
    wait: async () => {},
  });

  assert.equal(ready, true);
  assert.equal(checks, 3);
  assert.deepEqual(initialized, ["shell", "deep-link", "push"]);
});

test("한 네이티브 초기화기의 동기 실패가 다음 기능을 막지 않는다", async () => {
  const initialized = [];
  const ready = await bootstrapNativeRuntime({
    isNative: () => true,
    initializers: [
      () => { throw new Error("shell failed"); },
      () => initialized.push("deep-link"),
      () => initialized.push("push"),
    ],
    retryDelaysMs: [0],
  });

  assert.equal(ready, true);
  assert.deepEqual(initialized, ["deep-link", "push"]);
});

test("커스텀 스킴은 WebView의 비표준 URL hostname 해석에 의존하지 않는다", () => {
  assert.equal(toAppPath("careertuner://support"), "/support");
  assert.equal(toAppPath("careertuner://m/sessions"), "/m/sessions");
  assert.equal(toAppPath("careertuner:///m/session/12"), "/m/session/12");
  assert.equal(toAppPath("CAREERTUNER://SUPPORT/faq?from=app#top"), "/support/faq?from=app#top");
  assert.equal(toAppPath("careertuner://user@support"), null);
  assert.equal(toAppPath("careertuner://support:443"), null);
});

test("딥링크 런타임은 bridge 준비 지연을 재시도하고 cold/warm 중복 전달을 막는다", async () => {
  let addAttempts = 0;
  let launchAttempts = 0;
  let eventListener = null;
  let currentTime = 1_000;
  const delivered = [];

  const result = await initializeDeepLinkRuntime({
    appPlugin: {
      async addListener(_eventName, listener) {
        addAttempts += 1;
        if (addAttempts < 3) throw new Error("bridge not ready");
        eventListener = listener;
        return { remove() {} };
      },
      async getLaunchUrl() {
        launchAttempts += 1;
        return launchAttempts < 3 ? undefined : { url: "careertuner://m/sessions" };
      },
    },
    onUrl: (url) => delivered.push(url),
    retryDelaysMs: [0, 1, 1],
    wait: async () => {},
    now: () => currentTime,
  });

  assert.equal(result.listenerRegistered, true);
  assert.equal(result.launchUrlCaptured, true);
  assert.equal(addAttempts, 3);
  assert.equal(launchAttempts, 3);
  assert.deepEqual(delivered, ["careertuner://m/sessions"]);

  eventListener({ url: "careertuner://m/sessions" });
  assert.equal(delivered.length, 1);
  currentTime += 2_500;
  eventListener({ url: "careertuner://m/sessions" });
  assert.equal(delivered.length, 2);
});
