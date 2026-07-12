import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createServer } from "vite";

const applicationListSource = await readFile(
  new URL("../src/features/applications/pages/ApplicationListPage.tsx", import.meta.url),
  "utf8",
);
const adminApplicationCasesSource = await readFile(
  new URL("../src/admin/features/application-cases/pages/AdminApplicationCasesPage.tsx", import.meta.url),
  "utf8",
);
const responsiveGridContract = "grid min-w-0 grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3";
assert.equal(
  applicationListSource.split(responsiveGridContract).length - 1,
  2,
  "지원 건 로딩·카드 그리드는 모바일에서 명시적 1열과 min-width 축소 경계를 가져야 한다",
);
assert.match(
  applicationListSource,
  /<Card className="h-full min-w-0 /,
  "지원 건 카드는 grid min-content 폭을 키우지 않아야 한다",
);
assert.doesNotMatch(
  adminApplicationCasesSource,
  /B AI/,
  "관리자 지원 건 화면에 내부 담당 영역명이 노출되면 안 된다",
);
assert.match(
  applicationListSource,
  /min-h-44 min-w-0 w-full flex-col/,
  "새 지원 건 카드도 모바일 열 너비를 넘지 않아야 한다",
);

const viteServer = await createServer({ server: { middlewareMode: true }, appType: "custom" });

try {
  const mock = await viteServer.ssrLoadModule("/src/app/lib/mock/index.ts");
  const analysisTypes = await viteServer.ssrLoadModule("/src/features/applications/types/analysis.ts");

  const textOptions = await mock.resolveMock(
    "/application-cases/model-options?sourceType=TEXT",
    { method: "GET" },
  );
  assert.equal(textOptions.ocr, null, "텍스트 공고에는 OCR 선택지를 노출하지 않아야 한다");
  assert.equal(textOptions.jobAnalysis.recommendedDefault, "LOCAL");
  assert.equal(textOptions.companyAnalysis.recommendedDefault, "OPENAI");
  assert.ok(textOptions.jobAnalysis.options.every((option) => option.selectable));
  assert.ok(textOptions.companyAnalysis.options.every((option) => option.selectable));

  const jobAnalysis = await mock.resolveMock("/application-cases/101/job-analysis", { method: "GET" });
  assert.equal(jobAnalysis.employmentType, "FULL_TIME");
  assert.equal(jobAnalysis.experienceLevel, "JUNIOR");
  assert.equal(analysisTypes.getEmploymentTypeLabel(jobAnalysis.employmentType), "정규직");
  assert.equal(analysisTypes.getExperienceLevelLabel(jobAnalysis.experienceLevel), "주니어");
  assert.doesNotMatch(jobAnalysis.duties, /^\s*\[/, "주요 업무 mock이 JSON 배열 문자열을 노출하면 안 된다");
  assert.doesNotMatch(jobAnalysis.qualifications, /^\s*\[/, "자격 요건 mock이 JSON 배열 문자열을 노출하면 안 된다");

  const companyAnalysis = await mock.resolveMock("/application-cases/101/company-analysis", { method: "GET" });
  assert.equal(companyAnalysis.sourceType, "WEB");
  assert.equal(analysisTypes.getCompanySourceTypeLabel(companyAnalysis.sourceType), "웹 조사");

  for (const sourceType of ["PDF", "IMAGE"]) {
    const options = await mock.resolveMock(
      `/application-cases/model-options?sourceType=${sourceType}`,
      { method: "GET" },
    );
    assert.equal(options.ocr.recommendedDefault, "CLAUDE");
    assert.deepEqual(
      options.ocr.options.map((option) => option.provider),
      ["CLAUDE", "OPENAI", "SELF_OCR"],
    );
    assert.ok(options.ocr.options.every((option) => option.selectable));
  }

  console.log("application demo model-options contract: ok");
} finally {
  await viteServer.close();
}
