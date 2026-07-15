import { CORRECTION_TABS, type CorrectionTab } from "../types/correction.ts";

export const ORIGINAL_TEXT_MAX_LENGTH = 12_000;
export const QUESTION_TEXT_MAX_LENGTH = 1_000;

const STORAGE_PREFIX = "careertuner:correction:draft:v1";

export interface CorrectionDraft {
  originalText: string;
  questionText: string;
}

export type CorrectionDrafts = Record<CorrectionTab, CorrectionDraft>;
export type CorrectionDraftStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

interface StoredCorrectionDraft extends CorrectionDraft {
  version: 1;
}

export function emptyCorrectionDraft(): CorrectionDraft {
  return { originalText: "", questionText: "" };
}

export function emptyCorrectionDrafts(): CorrectionDrafts {
  return {
    answer: emptyCorrectionDraft(),
    cover: emptyCorrectionDraft(),
    resume: emptyCorrectionDraft(),
    portfolio: emptyCorrectionDraft(),
  };
}

export function correctionDraftStorageKey(userId: number, tab: CorrectionTab): string {
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    throw new Error("첨삭 draft 사용자 ID는 양의 정수여야 합니다.");
  }
  return `${STORAGE_PREFIX}:${userId}:${tab}`;
}

export function getCorrectionDraftStorage(): CorrectionDraftStorage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function loadCorrectionDrafts(
  storage: CorrectionDraftStorage | null,
  userId: number | null,
): CorrectionDrafts {
  const drafts = emptyCorrectionDrafts();
  if (!storage || userId == null) return drafts;
  for (const tab of CORRECTION_TABS) {
    drafts[tab] = readCorrectionDraft(storage, userId, tab);
  }
  return drafts;
}

export function readCorrectionDraft(
  storage: CorrectionDraftStorage,
  userId: number,
  tab: CorrectionTab,
): CorrectionDraft {
  try {
    const raw = storage.getItem(correctionDraftStorageKey(userId, tab));
    if (!raw) return emptyCorrectionDraft();
    const value = JSON.parse(raw) as Partial<StoredCorrectionDraft> | null;
    if (!value || value.version !== 1) return emptyCorrectionDraft();
    return {
      originalText: typeof value.originalText === "string"
        ? value.originalText.slice(0, ORIGINAL_TEXT_MAX_LENGTH)
        : "",
      questionText: typeof value.questionText === "string"
        ? value.questionText.slice(0, QUESTION_TEXT_MAX_LENGTH)
        : "",
    };
  } catch {
    return emptyCorrectionDraft();
  }
}

export function persistCorrectionDrafts(
  storage: CorrectionDraftStorage | null,
  userId: number | null,
  drafts: CorrectionDrafts,
): void {
  if (!storage || userId == null) return;
  for (const tab of CORRECTION_TABS) {
    persistCorrectionDraft(storage, userId, tab, drafts[tab]);
  }
}

export function persistCorrectionDraft(
  storage: CorrectionDraftStorage,
  userId: number,
  tab: CorrectionTab,
  draft: CorrectionDraft,
): void {
  const key = correctionDraftStorageKey(userId, tab);
  const normalized = {
    originalText: draft.originalText.slice(0, ORIGINAL_TEXT_MAX_LENGTH),
    questionText: draft.questionText.slice(0, QUESTION_TEXT_MAX_LENGTH),
  };
  try {
    if (!normalized.originalText && !normalized.questionText) {
      storage.removeItem(key);
      return;
    }
    const value: StoredCorrectionDraft = { version: 1, ...normalized };
    storage.setItem(key, JSON.stringify(value));
  } catch {
    // 저장소 차단·용량 초과 시에도 현재 메모리 draft 편집은 계속 허용한다.
  }
}

export function clearCorrectionDraft(
  storage: CorrectionDraftStorage | null,
  userId: number | null,
  tab: CorrectionTab,
): void {
  if (!storage || userId == null) return;
  try {
    storage.removeItem(correctionDraftStorageKey(userId, tab));
  } catch {
    // 저장소가 차단된 환경에는 정리할 수 있는 영속 draft도 없다.
  }
}
