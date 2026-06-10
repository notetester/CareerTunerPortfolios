import { ApiError, api } from "@/app/lib/api";
import type { JobPosting, JobPostingRequest } from "../types/jobPosting";

export async function getJobPosting(applicationCaseId: number): Promise<JobPosting | null> {
  try {
    return await api<JobPosting>(`/application-cases/${applicationCaseId}/job-posting`, { method: "GET" });
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.code === "NOT_FOUND")) {
      return null;
    }
    throw error;
  }
}

export function getJobPostingRevisions(applicationCaseId: number): Promise<JobPosting[]> {
  return api<JobPosting[]>(`/application-cases/${applicationCaseId}/job-posting/revisions`, { method: "GET" });
}

export function saveJobPosting(applicationCaseId: number, request: JobPostingRequest): Promise<JobPosting> {
  return api<JobPosting>(`/application-cases/${applicationCaseId}/job-posting`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function uploadJobPostingFile(
  applicationCaseId: number,
  sourceType: "PDF" | "IMAGE",
  file: File,
): Promise<JobPosting> {
  const formData = new FormData();
  formData.append("sourceType", sourceType);
  formData.append("file", file);

  return api<JobPosting>(`/application-cases/${applicationCaseId}/job-posting/upload`, {
    method: "POST",
    body: formData,
  });
}
