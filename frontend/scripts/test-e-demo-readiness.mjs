import { readFile } from "node:fs/promises";
import test from "node:test";
import assert from "node:assert/strict";

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), "utf8");

test("첨삭은 자기소개서, 면접 답변, 이력서, 포트폴리오 네 유형을 노출한다", async () => {
  const types = await read("src/features/correction/types/correction.ts");
  for (const value of ["SELF_INTRO", "INTERVIEW_ANSWER", "RESUME", "PORTFOLIO"]) {
    assert.match(types, new RegExp(`\\b${value}\\b`));
  }
});

test("면접 답변에서 첨삭으로 이동할 때 소유권 검증된 원문 ID와 지원 건을 전달한다", async () => {
  const interview = await read("src/features/interview/components/CorrectionInfoTab.tsx");
  const correction = await read("src/app/pages/Correction.tsx");
  assert.match(interview, /sourceRefId=\$\{selected\.answerId\}/);
  assert.match(correction, /requestedSourceRefId/);
  assert.match(correction, /sourceType: linkedInterviewSource \? "INTERVIEW_ANSWER" : "DIRECT_INPUT"/);
  assert.match(correction, /sourceRefId: linkedInterviewSource\?\.sourceRefId/);
});

test("첨삭은 모델 선택, 출처 provenance, 소프트 삭제 UI를 제공한다", async () => {
  const page = await read("src/app/pages/Correction.tsx");
  const result = await read("src/features/correction/components/CorrectionResultCard.tsx");
  const hook = await read("src/features/correction/hooks/useCorrections.ts");
  assert.match(page, /<ModelPicker/);
  assert.match(result, /parseSourceSnapshot/);
  assert.match(page, /onDelete=\{\(id\) => void remove\(id\)\}/);
  assert.match(hook, /await deleteCorrection\(id\)/);
});

test("첨삭 요청은 선택 모델과 멱등키를 API에 전달한다", async () => {
  const api = await read("src/features/correction/api/correctionApi.ts");
  const hook = await read("src/features/correction/hooks/useCorrections.ts");
  assert.match(api, /\?model=\$\{model\}/);
  assert.match(hook, /requestKey/);
  assert.match(hook, /policyAcknowledgementKey/);
});
