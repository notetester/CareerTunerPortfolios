import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceRoot = path.join(frontendRoot, "src");

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) return sourceFiles(target);
    return /\.(?:ts|tsx)$/.test(entry.name) ? [target] : [];
  }));
  return nested.flat();
}

test("frontend source does not reference legacy immediate-grant billing paths", async () => {
  const files = await sourceFiles(sourceRoot);
  const references = [];

  for (const file of files) {
    const source = (await readFile(file, "utf8")).replaceAll("\\/", "/");
    if (source.includes("/billing/subscribe") || source.includes("/billing/credits/purchase")) {
      references.push(path.relative(frontendRoot, file));
    }
  }

  assert.deepEqual(references, []);
});

test("FREE selection uses cancellation while paid flows keep Toss approval routes", async () => {
  const billingPage = await readFile(path.join(sourceRoot, "app/pages/Billing.tsx"), "utf8");
  const billingApi = await readFile(path.join(sourceRoot, "features/billing/api/billingApi.ts"), "utf8");
  const paymentApi = await readFile(path.join(sourceRoot, "features/billing/api/paymentApi.ts"), "utf8");

  assert.match(
    billingPage,
    /if \(planCode === "FREE"\) \{[\s\S]*?await cancelSubscription\(\)/,
  );
  assert.match(billingApi, /"\/billing\/subscription\/cancel"/);
  assert.match(paymentApi, /"\/payments\/toss\/ready"/);
  assert.match(paymentApi, /"\/payments\/toss\/confirm"/);
  assert.match(paymentApi, /"\/payments\/toss\/cancel"/);
});

test("billing sections isolate unrelated API failures", async () => {
  const billingPage = await readFile(path.join(sourceRoot, "app/pages/Billing.tsx"), "utf8");

  assert.doesNotMatch(
    billingPage,
    /Promise\.all\(\[getMyBilling\(\), getMonthlyUsage\(\), getMyPayments\(\)\]\)/,
    "사용량 실패가 결제 내역까지 버리는 결합 요청을 다시 도입하면 안 된다",
  );
  assert.match(
    billingPage,
    /activeTab === "overview" \|\| activeTab === "history"[\s\S]*loadSafely\(getMyPayments, setPayments/,
    "결제 내역 페이지는 결제 API를 독립적으로 반영해야 한다",
  );
  assert.match(
    billingPage,
    /activeTab === "overview" \|\| activeTab === "plans"[\s\S]*loadSafely\(getPlans, setPlans/,
    "요금제 페이지는 크레딧 상품 요청과 분리되어야 한다",
  );
});
