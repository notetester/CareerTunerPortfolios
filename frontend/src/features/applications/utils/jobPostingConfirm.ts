import type { ApplicationCaseExtraction } from "../types/applicationCase";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

// 상세 페이지 저장이 "텍스트 보정(confirm)"인지 "소스/주소 변경(재추출)"인지 판정하는 순수 헬퍼.
// JobPostingPanel(저장 버튼 disabled)과 ApplicationDetailPage(실제 라우팅)가 동일 로직을 공유한다.

function normalizeText(value: string | null | undefined): string {
  return (value ?? "").trim();
}

function normalizeRef(value: string | null | undefined): string | null {
  return (value ?? "").trim() || null;
}

export function currentPostingText(jobPosting: JobPosting): string {
  return normalizeText(jobPosting.extractedText ?? jobPosting.originalText);
}

export function requestPostingText(request: JobPostingRequest): string {
  return normalizeText(request.extractedText ?? request.originalText);
}

// 소스 종류 또는 URL/파일 참조가 바뀌면 새 추출이 필요한 변경으로 본다.
export function hasPostingSourceChange(request: JobPostingRequest, jobPosting: JobPosting): boolean {
  const nextSourceType = request.sourceType ?? jobPosting.sourceType;
  return (
    nextSourceType !== jobPosting.sourceType ||
    normalizeRef(request.uploadedFileUrl) !== normalizeRef(jobPosting.uploadedFileUrl)
  );
}

interface ConfirmJudgeInput {
  request: JobPostingRequest;
  jobPosting: JobPosting;
  extraction: ApplicationCaseExtraction | null | undefined;
}

// PASS 상태에서 소스/URL 변경 없이 본문만 수정된 경우 → confirm(OCR 없이 분석만 갱신).
export function isConfirmableTextCorrection({ request, jobPosting, extraction }: ConfirmJudgeInput): boolean {
  if (!extraction) {
    return false;
  }
  return (
    extraction.status === "SUCCEEDED" &&
    extraction.qualityStatus === "PASS" &&
    !hasPostingSourceChange(request, jobPosting) &&
    requestPostingText(request) !== currentPostingText(jobPosting)
  );
}

// REVIEW_REQUIRED 상태에서 소스/URL 변경이 없으면 일반 저장을 막고 "검수 확정" 버튼으로 유도한다.
export function shouldDisableSaveForReview({ request, jobPosting, extraction }: ConfirmJudgeInput): boolean {
  if (!extraction) {
    return false;
  }
  return (
    extraction.status === "SUCCEEDED" &&
    extraction.qualityStatus === "REVIEW_REQUIRED" &&
    !hasPostingSourceChange(request, jobPosting)
  );
}
