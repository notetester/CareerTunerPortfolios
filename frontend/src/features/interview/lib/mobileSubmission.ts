/** 실패한 요청이 추가한 임시 답변/채점 항목만 제거한다. */
export function rollbackOptimisticSubmission<T extends object>(
  items: T[],
  submissionId: string,
): T[] {
  return items.filter((item) => !("submissionId" in item && item.submissionId === submissionId));
}

/** 사용자가 실패 직후 새로 입력한 내용이 없다면 전송했던 초안을 복구한다. */
export function restoreFailedDraft(currentDraft: string, submittedText: string): string {
  return currentDraft.trim() ? currentDraft : submittedText;
}

export type PendingInterviewMediaKind = "AUDIO" | "VIDEO";

/** 서버 10 MiB 실효 한도보다 여유를 둔 모바일 원본 상한. */
export const MOBILE_INTERVIEW_MEDIA_MAX_BYTES = 9 * 1024 * 1024;
/** 명시 bitrate 기준 9 MiB 아래에 머물도록 화상 답변을 45초로 제한한다. */
export const MOBILE_INTERVIEW_VIDEO_MAX_SECONDS = 45;
export const MOBILE_INTERVIEW_AUDIO_MAX_SECONDS = 180;
export const MOBILE_INTERVIEW_VIDEO_BITS_PER_SECOND = 1_200_000;
export const MOBILE_INTERVIEW_AUDIO_BITS_PER_SECOND = 64_000;
export const SUBMISSION_RECONCILE_DELAYS_MS = [0, 500, 1_500] as const;

export function concludeMissingSubmissionReconciliation(
  authoritativeMisses: number,
  totalAttempts: number,
  hadUntrustedRead: boolean,
): "NOT_SAVED" | "UNKNOWN" {
  return !hadUntrustedRead && totalAttempts > 0 && authoritativeMisses === totalAttempts
    ? "NOT_SAVED"
    : "UNKNOWN";
}

export type CapturedMediaValidation =
  | { ok: true }
  | { ok: false; reason: "EMPTY" | "TOO_LARGE" };

/** 업로드 전에 브라우저가 실제로 만든 Blob 크기를 서버 안전 상한과 대조한다. */
export function validateCapturedMediaSize(sizeBytes: number): CapturedMediaValidation {
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0) return { ok: false, reason: "EMPTY" };
  if (sizeBytes >= MOBILE_INTERVIEW_MEDIA_MAX_BYTES) return { ok: false, reason: "TOO_LARGE" };
  return { ok: true };
}

/** 새 원본과 함께 교체할 전사. 빈 STT면 기존 초안을 재사용하지 않고 직접 입력을 요구한다. */
export function capturedDraft(transcript: string): string {
  return transcript.trim();
}

/** 제출 중/결과 불명 파일은 pending cleanup에서 격리한다. */
export function cleanupEligiblePendingFileIds(
  pendingFileIds: Iterable<number>,
  protectedFileIds: Iterable<number>,
): number[] {
  const protectedIds = new Set(protectedFileIds);
  return [...new Set(pendingFileIds)].filter((id) =>
    Number.isSafeInteger(id) && id > 0 && !protectedIds.has(id));
}

/** 완료한 세대만 upload indicator를 내린다. 오래된 완료가 새 업로드 상태를 지우지 않는다. */
export function settleMediaUploadGeneration(
  activeGeneration: number | null,
  completedGeneration: number,
): number | null {
  return activeGeneration === completedGeneration ? null : activeGeneration;
}

export type SubmissionFailureKind = "DEFINITE" | "UNCERTAIN";

/** 4xx 명시 거부 외에는 응답 유실 가능성이 있어 review reconciliation을 거친다. */
export function classifySubmissionFailure(error: unknown): SubmissionFailureKind {
  if (!error || typeof error !== "object") return "UNCERTAIN";
  const candidate = error as { code?: unknown; status?: unknown };
  if (candidate.code === "OUTAGE_MUTATION_UNCERTAIN") return "UNCERTAIN";
  const status = typeof candidate.status === "number" ? candidate.status : null;
  return status != null && status >= 400 && status < 500 && status !== 408
    ? "DEFINITE"
    : "UNCERTAIN";
}

export interface ReviewAnswerCandidate {
  questionId: number;
  answerId: number | null;
  answerText: string | null;
  audioUrl: string | null;
  videoUrl: string | null;
}

function sameMediaUrl(actual: string | null, expectedUrl: string, fileId: number): boolean {
  if (!actual) return false;
  return actual === expectedUrl || actual.endsWith(`/file/${fileId}/content`);
}

/** 응답이 유실됐을 때 review의 최신 답변이 방금 제출한 본문/원본과 일치하는지 판정한다. */
export function findReconciledAnswer<T extends ReviewAnswerCandidate>(
  items: readonly T[],
  expected: {
    questionId: number;
    answerText: string;
    mediaKind?: PendingInterviewMediaKind;
    fileId?: number;
    contentUrl?: string;
  },
): T | null {
  const item = items.find((candidate) =>
    candidate.questionId === expected.questionId
    && candidate.answerId != null
    && candidate.answerText?.trim() === expected.answerText.trim());
  if (!item) return null;
  if (!expected.mediaKind || expected.fileId == null || !expected.contentUrl) return item;
  return expected.mediaKind === "AUDIO"
    ? sameMediaUrl(item.audioUrl, expected.contentUrl, expected.fileId) ? item : null
    : sameMediaUrl(item.videoUrl, expected.contentUrl, expected.fileId) ? item : null;
}

/** pending file_asset을 표준 answers 계약의 ID+하위호환 URL 필드로 변환한다. */
export function buildPendingMediaAnswerFields(
  kind: PendingInterviewMediaKind,
  fileId: number,
  contentUrl: string,
): {
  audioFileId?: number;
  videoFileId?: number;
  audioUrl?: string;
  videoUrl?: string;
} {
  return kind === "AUDIO"
    ? { audioFileId: fileId, audioUrl: contentUrl }
    : { videoFileId: fileId, videoUrl: contentUrl };
}
