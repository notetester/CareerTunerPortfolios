export type JobPostingFallbackStage = "JOB_POSTING_PDF_OCR" | "JOB_POSTING_IMAGE_OCR";

export interface AdminJobPostingFallbackSetting {
  enabled: boolean;
  allowedStages: JobPostingFallbackStage[];
  availableStages: JobPostingFallbackStage[];
  source: "DEFAULT" | "PROPERTIES" | "DATABASE" | string;
}

export interface AdminJobPostingFallbackSettingRequest {
  enabled: boolean;
  allowedStages: JobPostingFallbackStage[];
}
