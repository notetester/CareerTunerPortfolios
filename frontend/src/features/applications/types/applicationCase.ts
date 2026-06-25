export type ApplicationSourceType = "TEXT" | "PDF" | "IMAGE" | "URL" | "MANUAL";

export type ApplicationStatus = "DRAFT" | "ANALYZING" | "READY" | "APPLIED" | "CLOSED";

export type ApplicationCaseListView = "ACTIVE" | "ARCHIVED" | "DELETED";

export type ApplicationCaseExtractionStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";

export type ApplicationCaseExtractionQualityStatus = "PASS" | "REVIEW_REQUIRED" | "FAILED";

export interface ApplicationCase {
  id: number;
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  deadlineDate: string | null;
  sourceType: ApplicationSourceType;
  status: ApplicationStatus;
  favorite: boolean;
  archived: boolean;
  archivedAt: string | null;
  deletedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateApplicationCaseRequest {
  companyName: string;
  jobTitle: string;
  postingDate?: string | null;
  deadlineDate?: string | null;
  sourceType?: ApplicationSourceType;
  status?: ApplicationStatus;
  favorite?: boolean;
  archived?: boolean;
}

export interface ApplicationCaseExtraction {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  sourceType: ApplicationSourceType;
  status: ApplicationCaseExtractionStatus;
  errorMessage: string | null;
  extractionStrategy: string | null;
  qualityScore: number | null;
  qualityStatus: ApplicationCaseExtractionQualityStatus | null;
  qualityReportJson: string | null;
  modelVersionsJson: string | null;
  fallbackEligible: boolean;
  fallbackReason: string | null;
  reviewedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateApplicationCaseRequest {
  companyName?: string;
  jobTitle?: string;
  postingDate?: string | null;
  clearPostingDate?: boolean;
  deadlineDate?: string | null;
  clearDeadlineDate?: boolean;
  sourceType?: ApplicationSourceType;
  status?: ApplicationStatus;
  favorite?: boolean;
  archived?: boolean;
}

export const APPLICATION_STATUS_OPTIONS: { value: ApplicationStatus; label: string }[] = [
  { value: "DRAFT", label: "초안" },
  { value: "ANALYZING", label: "분석중" },
  { value: "READY", label: "준비중" },
  { value: "APPLIED", label: "지원완료" },
  { value: "CLOSED", label: "마감" },
];

export const APPLICATION_SOURCE_OPTIONS: { value: ApplicationSourceType; label: string }[] = [
  { value: "TEXT", label: "텍스트" },
  { value: "PDF", label: "PDF" },
  { value: "IMAGE", label: "이미지" },
  { value: "URL", label: "URL" },
  { value: "MANUAL", label: "수동 입력" },
];

export function getApplicationStatusLabel(status: ApplicationStatus): string {
  return APPLICATION_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status;
}

export function getApplicationSourceLabel(sourceType: ApplicationSourceType): string {
  return APPLICATION_SOURCE_OPTIONS.find((item) => item.value === sourceType)?.label ?? sourceType;
}

export function isApplicationCaseExtractionActive(status: ApplicationCaseExtractionStatus): boolean {
  return status === "QUEUED" || status === "RUNNING";
}

export function isApplicationCaseExtractionTerminal(status: ApplicationCaseExtractionStatus): boolean {
  return status === "SUCCEEDED" || status === "FAILED";
}

export function isApplicationCaseExtractionReviewRequired(
  extraction: ApplicationCaseExtraction | null | undefined,
): boolean {
  return extraction?.status === "SUCCEEDED" && extraction.qualityStatus === "REVIEW_REQUIRED";
}
