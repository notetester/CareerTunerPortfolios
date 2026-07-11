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
