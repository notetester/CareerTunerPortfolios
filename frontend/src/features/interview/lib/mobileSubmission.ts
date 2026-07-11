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
