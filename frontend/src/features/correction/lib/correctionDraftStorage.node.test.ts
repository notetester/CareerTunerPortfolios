import assert from "node:assert/strict";
import test from "node:test";
import {
  ORIGINAL_TEXT_MAX_LENGTH,
  QUESTION_TEXT_MAX_LENGTH,
  clearCorrectionDraft,
  correctionDraftStorageKey,
  loadCorrectionDrafts,
  persistCorrectionDraft,
  readCorrectionDraft,
  type CorrectionDraftStorage,
} from "./correctionDraftStorage.ts";

function memoryStorage(): CorrectionDraftStorage & { values: Map<string, string> } {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => { values.set(key, value); },
    removeItem: (key) => { values.delete(key); },
  };
}

test("draft는 사용자와 첨삭 유형별 session key로 분리된다", () => {
  assert.notEqual(correctionDraftStorageKey(7, "answer"), correctionDraftStorageKey(7, "resume"));
  assert.notEqual(correctionDraftStorageKey(7, "answer"), correctionDraftStorageKey(8, "answer"));
  assert.throws(() => correctionDraftStorageKey(0, "answer"), /양의 정수/);
});

test("작성 중인 탭 draft를 저장하고 새 화면에서 복원한다", () => {
  const storage = memoryStorage();
  persistCorrectionDraft(storage, 7, "cover", {
    questionText: "지원 동기를 작성해 주세요.",
    originalText: "작성 중인 자기소개서",
  });

  const restored = loadCorrectionDrafts(storage, 7);
  assert.deepEqual(restored.cover, {
    questionText: "지원 동기를 작성해 주세요.",
    originalText: "작성 중인 자기소개서",
  });
  assert.deepEqual(restored.answer, { questionText: "", originalText: "" });
});

test("성공 또는 명시 초기화는 선택한 탭 draft만 제거한다", () => {
  const storage = memoryStorage();
  persistCorrectionDraft(storage, 7, "answer", { questionText: "질문", originalText: "답변" });
  persistCorrectionDraft(storage, 7, "resume", { questionText: "", originalText: "이력서" });

  clearCorrectionDraft(storage, 7, "answer");

  assert.deepEqual(readCorrectionDraft(storage, 7, "answer"), { questionText: "", originalText: "" });
  assert.equal(readCorrectionDraft(storage, 7, "resume").originalText, "이력서");
});

test("손상되거나 과도한 저장 값은 안전하게 무시·절단한다", () => {
  const storage = memoryStorage();
  const key = correctionDraftStorageKey(7, "portfolio");
  storage.setItem(key, "{broken");
  assert.deepEqual(readCorrectionDraft(storage, 7, "portfolio"), { questionText: "", originalText: "" });

  storage.setItem(key, JSON.stringify({
    version: 1,
    originalText: "가".repeat(ORIGINAL_TEXT_MAX_LENGTH + 10),
    questionText: "나".repeat(QUESTION_TEXT_MAX_LENGTH + 10),
  }));
  const restored = readCorrectionDraft(storage, 7, "portfolio");
  assert.equal(restored.originalText.length, ORIGINAL_TEXT_MAX_LENGTH);
  assert.equal(restored.questionText.length, QUESTION_TEXT_MAX_LENGTH);
});
