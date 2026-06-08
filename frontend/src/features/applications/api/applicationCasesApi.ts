import { api } from "@/app/lib/api";
import type {
  ApplicationCase,
  CreateApplicationCaseRequest,
  UpdateApplicationCaseRequest,
} from "../types/applicationCase";

export function listApplicationCases(): Promise<ApplicationCase[]> {
  return api<ApplicationCase[]>("/application-cases", { method: "GET" });
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
