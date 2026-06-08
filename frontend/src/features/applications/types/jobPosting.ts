import type { ApplicationSourceType } from "./applicationCase";

export interface JobPosting {
  id: number;
  applicationCaseId: number;
  originalText: string | null;
  uploadedFileUrl: string | null;
  extractedText: string | null;
  sourceType: ApplicationSourceType;
  createdAt: string;
}

export interface JobPostingRequest {
  originalText?: string | null;
  uploadedFileUrl?: string | null;
  extractedText?: string | null;
  sourceType?: ApplicationSourceType;
}
