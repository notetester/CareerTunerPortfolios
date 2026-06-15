import type { ApplicationSourceType } from "./applicationCase";

export interface JobPosting {
  id: number;
  applicationCaseId: number;
  revision: number;
  originalText: string | null;
  uploadedFileUrl: string | null;
  extractedText: string | null;
  sourceType: ApplicationSourceType;
  createdAt: string;
}

export interface JobPostingMetadata {
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  deadlineDate: string | null;
}

export interface JobPostingRequest {
  originalText?: string | null;
  uploadedFileUrl?: string | null;
  extractedText?: string | null;
  sourceType?: ApplicationSourceType;
}
