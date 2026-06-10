import { api } from "@/app/lib/api";
import type {
  ApplicationCase,
  ApplicationCaseListView,
  CreateApplicationCaseRequest,
  UpdateApplicationCaseRequest,
} from "../types/applicationCase";

type ApplicationCaseListOptions = boolean | {
  includeArchived?: boolean;
  view?: ApplicationCaseListView;
};

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
