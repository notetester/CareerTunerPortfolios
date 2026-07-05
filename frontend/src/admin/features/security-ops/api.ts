import { api } from "@/app/lib/api";
import type {
  BlockCacheStatus,
  IpBlockBatch,
  PolicyFeedImportResult,
  SecurityAppeal,
  SecurityAppealPolicy,
  SecurityBlockRule,
  SecurityOpsSummary,
  SecurityProviderConfig,
  SecurityProviderHealthHistory,
  SecurityReview,
  WafSyncEvent,
} from "./types";

export function getSecuritySummary(): Promise<SecurityOpsSummary> {
  return api<SecurityOpsSummary>("/admin/security/summary");
}

export function getBlockRules(): Promise<SecurityBlockRule[]> {
  return api<SecurityBlockRule[]>("/admin/security/block-rules?limit=500");
}

export function createBlockRule(payload: Partial<SecurityBlockRule>): Promise<SecurityBlockRule> {
  return api<SecurityBlockRule>("/admin/security/block-rules", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateBlockRule(id: number, payload: Partial<SecurityBlockRule>): Promise<SecurityBlockRule> {
  return api<SecurityBlockRule>(`/admin/security/block-rules/${id}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function queueWafSync(id: number, operationType = "UPSERT"): Promise<SecurityBlockRule> {
  return api<SecurityBlockRule>(`/admin/security/block-rules/${id}/waf-sync?operationType=${operationType}`, {
    method: "POST",
  });
}

export function getWafEvents(): Promise<WafSyncEvent[]> {
  return api<WafSyncEvent[]>("/admin/security/waf-events?limit=500");
}

export function getProviders(): Promise<SecurityProviderConfig[]> {
  return api<SecurityProviderConfig[]>("/admin/security/providers");
}

export function updateProvider(providerCode: string, payload: Partial<SecurityProviderConfig>): Promise<SecurityProviderConfig> {
  return api<SecurityProviderConfig>(`/admin/security/providers/${providerCode}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function runProviderHealthCheck(providerCode: string): Promise<SecurityProviderConfig> {
  return api<SecurityProviderConfig>(`/admin/security/providers/${providerCode}/health-check`, { method: "POST" });
}

export function getProviderHealthHistory(): Promise<SecurityProviderHealthHistory[]> {
  return api<SecurityProviderHealthHistory[]>("/admin/security/provider-health-history?limit=500");
}

export function getReviews(): Promise<SecurityReview[]> {
  return api<SecurityReview[]>("/admin/security/reviews?limit=500");
}

export function createReview(payload: Partial<SecurityReview>): Promise<SecurityReview> {
  return api<SecurityReview>("/admin/security/reviews", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateReview(id: number, payload: Partial<SecurityReview>): Promise<SecurityReview> {
  return api<SecurityReview>(`/admin/security/reviews/${id}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function getAppealPolicy(): Promise<SecurityAppealPolicy> {
  return api<SecurityAppealPolicy>("/admin/security/appeal-policy");
}

export function updateAppealPolicy(payload: Partial<SecurityAppealPolicy> & { reason?: string }): Promise<SecurityAppealPolicy> {
  return api<SecurityAppealPolicy>("/admin/security/appeal-policy", {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function getAppeals(): Promise<SecurityAppeal[]> {
  return api<SecurityAppeal[]>("/admin/security/appeals?limit=500");
}

export function decideAppeal(id: number, payload: { status: string; decisionReason?: string }): Promise<SecurityAppeal> {
  return api<SecurityAppeal>(`/admin/security/appeals/${id}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function getBlockCacheStatus(): Promise<BlockCacheStatus> {
  return api<BlockCacheStatus>("/admin/security/block-cache/status");
}

export function syncBlockCache(): Promise<BlockCacheStatus> {
  return api<BlockCacheStatus>("/admin/security/block-cache/sync", { method: "POST" });
}

export function getBlockBatches(): Promise<IpBlockBatch[]> {
  return api<IpBlockBatch[]>("/admin/security/block-batches?limit=200");
}

export function toggleBlockBatch(id: number, active: boolean, strategy = "BATCH_ONLY"): Promise<IpBlockBatch> {
  return api<IpBlockBatch>(`/admin/security/block-batches/${id}/toggle?active=${active}&strategy=${strategy}`, {
    method: "POST",
  });
}

export function uploadPolicyFeed(
  file: File,
  opts: { sourceName?: string; action?: string; category?: string } = {},
): Promise<PolicyFeedImportResult> {
  const form = new FormData();
  form.append("file", file);
  if (opts.sourceName) form.append("sourceName", opts.sourceName);
  if (opts.action) form.append("action", opts.action);
  if (opts.category) form.append("category", opts.category);
  return api<PolicyFeedImportResult>("/admin/security/policy-feed/upload", { method: "POST", body: form });
}

export function importPolicyFeedText(
  rawText: string,
  opts: { sourceName?: string; action?: string; category?: string } = {},
): Promise<PolicyFeedImportResult> {
  return api<PolicyFeedImportResult>("/admin/security/policy-feed/import", {
    method: "POST",
    body: JSON.stringify({ rawText, sourceName: opts.sourceName, action: opts.action ?? "BLOCK", category: opts.category ?? "SECURITY" }),
  });
}
