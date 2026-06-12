import { ApiError, api } from "@/app/lib/api";
import type {
  ApplicationCase,
  ApplicationCaseExtraction,
  ApplicationCaseListView,
  ApplicationSourceType,
  CreateApplicationCaseRequest,
  UpdateApplicationCaseRequest,
} from "../types/applicationCase";
import type { JobPosting, JobPostingMetadata } from "../types/jobPosting";

type ApplicationCaseListOptions = boolean | {
  includeArchived?: boolean;
  view?: ApplicationCaseListView;
};

export interface CreateApplicationCaseFromJobPostingRequest {
  originalText?: string | null;
  uploadedFileUrl?: string | null;
  extractedText?: string | null;
  sourceType?: ApplicationSourceType;
  favorite?: boolean;
}

export interface CreateApplicationCaseFromJobPostingResponse {
  applicationCase: ApplicationCase;
  jobPosting: JobPosting;
  metadata: JobPostingMetadata;
  extractionJob: ApplicationCaseExtraction;
}

export function listApplicationCases(options: ApplicationCaseListOptions = false): Promise<ApplicationCase[]> {
  const params = new URLSearchParams();

  if (typeof options === "boolean") {
    params.set("includeArchived", String(options));
  } else {
    if (options.view) {
      params.set("view", options.view);
    }
    if (!options.view || options.includeArchived !== undefined) {
      params.set("includeArchived", String(Boolean(options.includeArchived)));
    }
  }

  const query = params.toString();
  return api<ApplicationCase[]>(`/application-cases${query ? `?${query}` : ""}`, { method: "GET" });
}

export function getApplicationCase(id: number): Promise<ApplicationCase> {
  return api<ApplicationCase>(`/application-cases/${id}`, { method: "GET" });
}

export function createApplicationCase(request: CreateApplicationCaseRequest): Promise<ApplicationCase> {
  return api<ApplicationCase>("/application-cases", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function createApplicationCaseFromJobPosting(
  request: CreateApplicationCaseFromJobPostingRequest,
): Promise<CreateApplicationCaseFromJobPostingResponse> {
  return api<CreateApplicationCaseFromJobPostingResponse>("/application-cases/from-job-posting", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function uploadApplicationCaseFromJobPosting(
  file: File,
  sourceType: Extract<ApplicationSourceType, "PDF" | "IMAGE">,
  favorite = false,
): Promise<CreateApplicationCaseFromJobPostingResponse> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("sourceType", sourceType);
  formData.append("favorite", String(favorite));

  return api<CreateApplicationCaseFromJobPostingResponse>("/application-cases/from-job-posting/upload", {
    method: "POST",
    body: formData,
  });
}

export function listActiveApplicationCaseExtractions(): Promise<ApplicationCaseExtraction[]> {
  return api<ApplicationCaseExtraction[]>("/application-cases/extractions/active", { method: "GET" });
}

export async function getLatestApplicationCaseExtraction(
  applicationCaseId: number,
): Promise<ApplicationCaseExtraction | null> {
  try {
    const extraction = await api<ApplicationCaseExtraction | null>(
      `/application-cases/${applicationCaseId}/job-posting/extraction`,
      { method: "GET" },
    );
    return extraction ?? null;
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.code === "NOT_FOUND")) {
      return null;
    }
    throw error;
  }
}

export function retryApplicationCaseExtraction(applicationCaseId: number): Promise<ApplicationCaseExtraction> {
  return api<ApplicationCaseExtraction>(`/application-cases/${applicationCaseId}/job-posting/extraction/retry`, {
    method: "POST",
  });
}

export function updateApplicationCase(
  id: number,
  request: UpdateApplicationCaseRequest,
): Promise<ApplicationCase> {
  return api<ApplicationCase>(`/application-cases/${id}`, {
    method: "PATCH",
    body: JSON.stringify(request),
  });
}

export function deleteApplicationCase(id: number): Promise<void> {
  return api<void>(`/application-cases/${id}`, { method: "DELETE" });
}

export function restoreApplicationCase(id: number): Promise<void> {
  return api<void>(`/application-cases/${id}/restore`, { method: "PATCH" });
}
