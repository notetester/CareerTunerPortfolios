export interface JobAnalysis {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  employmentType: string | null;
  experienceLevel: string | null;
  requiredSkills: string | null;
  preferredSkills: string | null;
  duties: string | null;
  qualifications: string | null;
  difficulty: string | null;
  summary: string | null;
  evidence: string | null;
  ambiguousConditions: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export interface CompanyAnalysis {
  id: number;
  applicationCaseId: number;
  jobPostingId: number | null;
  jobPostingRevision: number | null;
  companySummary: string | null;
  recentIssues: string | null;
  industry: string | null;
  competitors: string | null;
  interviewPoints: string | null;
  sources: string | null;
  verifiedFacts: string | null;
  aiInferences: string | null;
  sourceType: string | null;
  checkedAt: string | null;
  refreshRecommendedAt: string | null;
  confirmedAt: string | null;
  adminMemo: string | null;
  createdAt: string;
}

export interface BAnalysisFailureLog {
  featureType: string;
  errorMessage: string | null;
  createdAt: string;
}

export type JsonArrayOrText =
  | { kind: "empty" }
  | { kind: "list"; items: string[] }
  | { kind: "text"; text: string };

export interface JobAnalysisReviewRequest {
  employmentType?: string | null;
  experienceLevel?: string | null;
  requiredSkills?: string | null;
  preferredSkills?: string | null;
  duties?: string | null;
  qualifications?: string | null;
  difficulty?: string | null;
  summary?: string | null;
  confirmed?: boolean;
}

export interface CompanyAnalysisReviewRequest {
  companySummary?: string | null;
  recentIssues?: string | null;
  industry?: string | null;
  competitors?: string | null;
  interviewPoints?: string | null;
  sources?: string | null;
  confirmed?: boolean;
}

const OBJECT_KEY_LABELS: Record<string, string> = {
  field: "항목",
  quote: "근거",
  condition: "조건",
  assumption: "해석",
  fact: "사실",
  source: "출처",
  inference: "추론",
  basis: "근거",
};

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function formatObjectValue(value: unknown): string {
  if (typeof value === "string") return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (Array.isArray(value)) {
    return value.map(formatObjectValue).filter(Boolean).join(", ");
  }
  if (isPlainObject(value)) {
    return Object.entries(value)
      .map(([key, nestedValue]) => `${OBJECT_KEY_LABELS[key] ?? key}: ${formatObjectValue(nestedValue)}`)
      .filter(Boolean)
      .join(" / ");
  }
  return "";
}

function stringifyJsonObjectItem(item: Record<string, unknown>): string {
  const field = formatObjectValue(item.field);
  const quote = formatObjectValue(item.quote);
  if (field && quote) return `${field}: ${quote}`;

  const condition = formatObjectValue(item.condition);
  const assumption = formatObjectValue(item.assumption);
  if (condition && assumption) return `${condition} - ${assumption}`;

  const fact = formatObjectValue(item.fact);
  const source = formatObjectValue(item.source);
  if (fact && source) return `${fact} (출처: ${source})`;

  const inference = formatObjectValue(item.inference);
  const basis = formatObjectValue(item.basis);
  if (inference && basis) return `${inference} (근거: ${basis})`;

  return Object.entries(item)
    .map(([key, value]) => `${OBJECT_KEY_LABELS[key] ?? key}: ${formatObjectValue(value)}`)
    .filter(Boolean)
    .join(" / ");
}

function stringifyJsonArrayItem(item: unknown): string {
  if (typeof item === "string") return item.trim();
  if (typeof item === "number" || typeof item === "boolean") return String(item);
  if (item == null) return "";
  if (isPlainObject(item)) return stringifyJsonObjectItem(item);
  if (Array.isArray(item)) return item.map(stringifyJsonArrayItem).filter(Boolean).join(", ");
  return String(item);
}

export function parseJsonArrayOrText(value: string | null | undefined): JsonArrayOrText {
  const trimmed = value?.trim();
  if (!trimmed) return { kind: "empty" };

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (Array.isArray(parsed)) {
      const items = parsed.map(stringifyJsonArrayItem).filter(Boolean);
      return items.length > 0 ? { kind: "list", items } : { kind: "empty" };
    }
  } catch {
    return { kind: "text", text: trimmed };
  }

  return { kind: "text", text: trimmed };
}

export function parseJsonStringArray(value: string | null | undefined): string[] {
  const parsed = parseJsonArrayOrText(value);
  if (parsed.kind === "list") return parsed.items;
  if (parsed.kind === "text") return [parsed.text];
  return [];
}

export function formatJsonArrayForTextarea(value: string | null | undefined): string {
  const parsed = parseJsonArrayOrText(value);
  if (parsed.kind === "list") return parsed.items.join("\n");
  if (parsed.kind === "text") return parsed.text;
  return "";
}

export function serializeTextareaList(value: string | null | undefined): string {
  const trimmed = value?.trim();
  if (!trimmed) return "";

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (Array.isArray(parsed)) {
      const items = parsed.map(stringifyJsonArrayItem).filter(Boolean);
      return JSON.stringify(items);
    }
  } catch {
    // Treat normal edited text as one item per line.
  }

  const items = trimmed
    .split(/\r?\n/)
    .map((item) => item.replace(/^[-*]\s+/, "").trim())
    .filter(Boolean);
  return JSON.stringify(items);
}

export function getDifficultyLabel(value: string | null | undefined): string {
  switch (value) {
    case "EASY":
      return "쉬움";
    case "NORMAL":
      return "보통";
    case "HARD":
      return "높음";
    default:
      return value ?? "미정";
  }
}
