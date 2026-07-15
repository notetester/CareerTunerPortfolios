import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { createServer } from "vite";

const vite = await createServer({ server: { middlewareMode: true }, appType: "custom" });

try {
  const mock = await vite.ssrLoadModule("/src/app/lib/mock/index.ts");

  const roadmap = await mock.resolveMock("/fit-analyses/career-roadmap?months=24", { method: "GET" });
  assert.equal(roadmap.horizonMonths, 24);
  assert.ok(roadmap.items.some((item) => item.type === "SKILL_LEARNING"));
  assert.ok(roadmap.items.some((item) => item.type === "APPLICATION_DEADLINE"));

  const certificates = await mock.resolveMock("/certificates/search?q=SQLD", { method: "GET" });
  assert.equal(certificates.resolvedAlias, "SQL");
  assert.ok(certificates.privateMatches.some((item) => item.name.includes("SQLD")));
  assert.equal(certificates.privateLookupFailed, false);

  const before = await mock.resolveMock("/fit-analyses/application-cases/101", { method: "GET" });
  const regenerated = await mock.resolveMock("/fit-analyses/application-cases/101?model=CLAUDE", { method: "POST" });
  assert.notEqual(regenerated.id, before.id);
  assert.equal(regenerated.model, "mock-demo:CLAUDE");

  const latest = await mock.resolveMock("/fit-analyses/application-cases/101", { method: "GET" });
  assert.equal(latest.id, regenerated.id);
  const list = await mock.resolveMock("/fit-analyses", { method: "GET" });
  assert.equal(list.filter((analysis) => analysis.applicationCaseId === 101).length, 1);
  const history = await mock.resolveMock("/fit-analyses/application-cases/101/history", { method: "GET" });
  assert.equal(history[0].id, regenerated.id);
  assert.equal(JSON.parse(regenerated.sourceSnapshot).profileVersionId, 90001);

  const moreSheet = await readFile(resolve("src/app/components/layout/MobileMoreSheet.tsx"), "utf8");
  const analysisPage = await readFile(resolve("src/features/analysis/pages/AnalysisPage.tsx"), "utf8");
  const fitPanel = await readFile(resolve("src/features/applications/components/FitAnalysisPanel.tsx"), "utf8");
  const applicationDetail = await readFile(resolve("src/features/applications/pages/ApplicationDetailPage.tsx"), "utf8");
  const fitApi = await readFile(resolve("src/features/analysis/api/fitAnalysisApi.ts"), "utf8");
  const modelPicker = await readFile(resolve("src/app/components/ai/ModelPicker.tsx"), "utf8");
  const routes = await readFile(resolve("src/app/routes.ts"), "utf8");
  const homePage = await readFile(resolve("src/features/home/pages/HomePage.tsx"), "utf8");
  const theme = await readFile(resolve("src/styles/theme.css"), "utf8");
  assert.ok(moreSheet.includes('href: "/analysis"') && moreSheet.includes('href: "/planner"'));
  assert.ok(analysisPage.includes('to="/career-roadmap"'));
  assert.ok(fitPanel.includes('label: "프로필 스냅샷"'));
  assert.ok(applicationDetail.includes("<ModelPicker value={fitModel}") && applicationDetail.includes("generateFit(false, fitModel)"));
  assert.ok(fitApi.includes('params.set("model", model)'));
  assert.ok(modelPicker.includes("bg-card") && modelPicker.includes("text-foreground"));
  assert.ok(routes.includes('withConsentGate(DashboardPage, ["AI_DATA"])'));
  assert.ok(routes.includes('withConsentGate(CareerRoadmapPage, ["AI_DATA"])'));
  assert.ok(
    routes.includes("withAuthGate(PlannerHubPage)")
      && routes.includes("withAuthGate(PlannerSchedulePage)")
      && routes.includes("withAuthGate(PlannerMemosPage)")
      && routes.includes("withAuthGate(PlannerOverlaysPage)")
      && routes.includes("withAuthGate(CertificateSearchPage)"),
  );
  assert.ok(homePage.includes('consentStatus?.aiDataAgreed !== true'));
  assert.ok(homePage.includes('<ConsentGate requirements={["AI_DATA"]}>'));
  assert.ok(theme.includes("--color-slate-50: var(--surface-2)"));
  assert.ok(theme.includes("--color-slate-900: var(--foreground)"));

  console.log("PASS C mock/web/mobile readiness tests");
} finally {
  await vite.close();
}
