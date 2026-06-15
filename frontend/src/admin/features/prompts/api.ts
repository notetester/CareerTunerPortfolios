import { api } from "@/app/lib/api";
import type {
  AdminBPromptEndpoint,
  AdminBPromptResponseByPath,
  AdminBPromptPath,
  AdminBPromptViewsSettledResponse,
  AdminBPromptView,
  AdminPromptViewRejected,
  AdminPromptViewSettledResult,
} from "./types";

const B_PROMPT_ENDPOINTS: AdminBPromptEndpoint[] = [
  { feature: "job-analysis", path: "/admin/prompts/job-analysis" },
  { feature: "company-analysis", path: "/admin/prompts/company-analysis" },
];

function failureMessage(reason: unknown): string {
  if (reason instanceof Error) return reason.message;
  if (typeof reason === "string" && reason.trim()) return reason.trim();
  return "Prompt request failed";
}

function toRejectedPromptView(
  endpoint: AdminBPromptEndpoint,
  reason: unknown,
): AdminPromptViewRejected {
  return {
    feature: endpoint.feature,
    path: endpoint.path,
    status: "rejected",
    reason,
    message: failureMessage(reason),
  };
}

function getBPromptView<P extends AdminBPromptPath>(path: P): Promise<AdminBPromptResponseByPath[P]> {
  return api<AdminBPromptResponseByPath[P]>(path, { method: "GET" });
}

export async function getBPromptViewsSettled(): Promise<AdminBPromptViewsSettledResponse> {
  const settled = await Promise.allSettled(
    B_PROMPT_ENDPOINTS.map((endpoint) => getBPromptView(endpoint.path)),
  );

  const results: AdminPromptViewSettledResult[] = settled.map((result, index) => {
    const endpoint = B_PROMPT_ENDPOINTS[index];
    if (result.status === "fulfilled") {
      return {
        feature: endpoint.feature,
        path: endpoint.path,
        status: "fulfilled",
        value: result.value,
      };
    }
    return toRejectedPromptView(endpoint, result.reason);
  });

  return {
    views: results.flatMap((result) => (result.status === "fulfilled" ? [result.value] : [])),
    failures: results.flatMap((result) => (result.status === "rejected" ? [result] : [])),
    results,
  };
}

export async function getBPromptViews(): Promise<AdminBPromptView[]> {
  const { views, failures } = await getBPromptViewsSettled();
  if (failures.length > 0) {
    throw failures[0].reason instanceof Error ? failures[0].reason : new Error(failures[0].message);
  }
  return views;
}
