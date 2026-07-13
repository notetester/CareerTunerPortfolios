// 순수함수(validator·formatter) 런타임 테스트. 훅/DOM 러너(vitest 등)는 없지만
// 이 모듈은 import type 만 있어 런타임 의존성이 없으므로 Node 22 type-strip 로 직접 실행한다.
//   실행: node --test --experimental-strip-types src/features/applications/utils/jobPostingUpload.node.test.ts
import assert from "node:assert/strict";
import { test } from "node:test";
import { validateJobPostingFile, formatUploadLimitLabel } from "./jobPostingUpload.ts";

function fileOf(size: number, type: string): File {
  return new File([new Uint8Array(size)], "posting", { type });
}

test("maxBytes 초과 → 크기 오류 반환", () => {
  const err = validateJobPostingFile("PDF", fileOf(2000, "application/pdf"), 1000);
  assert.ok(err && err.includes("이하만"));
});

test("maxBytes 이하 → 통과(null 반환)", () => {
  assert.equal(validateJobPostingFile("PDF", fileOf(500, "application/pdf"), 1000), null);
});

test("maxBytes=null → 크기 검사 생략(같은 파일이 숫자 한도면 거부되지만 null이면 통과)", () => {
  const big = fileOf(2000, "application/pdf");
  assert.ok(validateJobPostingFile("PDF", big, 1000)); // 숫자 한도: 거부
  assert.equal(validateJobPostingFile("PDF", big, null), null); // null: 통과
});

test("maxBytes=null 이어도 MIME 검사는 유지된다", () => {
  const err = validateJobPostingFile("PDF", fileOf(100, "image/png"), null);
  assert.ok(err && err.includes("application/pdf"));
});

test("이미지 방식의 잘못된 MIME 은 거부한다", () => {
  const err = validateJobPostingFile("IMAGE", fileOf(100, "application/pdf"), 1_000_000);
  assert.ok(err && err.includes("PNG"));
});

test("formatUploadLimitLabel — 정수 MB", () => {
  assert.equal(formatUploadLimitLabel(10 * 1024 * 1024), "10MB");
});

test("formatUploadLimitLabel — 소수 MB 정확 표기(후행 0 제거)", () => {
  assert.equal(formatUploadLimitLabel(7.5 * 1024 * 1024), "7.5MB");
  assert.equal(formatUploadLimitLabel(2.25 * 1024 * 1024), "2.25MB");
});
