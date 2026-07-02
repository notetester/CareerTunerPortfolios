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
  /**
   * 백엔드가 응답 직전 aiInferences의 kind=UNKNOWN 마커를 펼쳐 내려주는 virtual 필드.
   * 프런트는 마커를 직접 파싱하지 않고 이 필드만 읽는다(읽기 전용, 검수 저장 대상 아님).
   */
  unknowns: string | null;
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
  evidence?: string | null;
  ambiguousConditions?: string | null;
  confirmed?: boolean;
}

export interface CompanyAnalysisReviewRequest {
  companySummary?: string | null;
  recentIssues?: string | null;
  industry?: string | null;
  competitors?: string | null;
  interviewPoints?: string | null;
  sources?: string | null;
  verifiedFacts?: string | null;
  aiInferences?: string | null;
  confirmed?: boolean;
}

export interface EvidenceRow extends Record<string, string> {
  field: string;
  quote: string;
}

export interface AmbiguousConditionRow extends Record<string, string> {
  condition: string;
  assumption: string;
}

export interface VerifiedFactRow extends Record<string, string> {
  fact: string;
  source: string;
}

export interface AiInferenceRow extends Record<string, string> {
  inference: string;
  basis: string;
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

function hasStructuredRowValue<T extends Record<string, string>>(
  row: T,
  keys: readonly (keyof T & string)[],
): boolean {
  return keys.some((key) => row[key].trim().length > 0);
}

/**
 * 구조화 편집 행에서 UI에 노출되지 않는 원본 키(factId, sourceKind, sourceRef, evidence,
 * inferenceId, basedOn, confidence 등)를 JSON 문자열로 보관하는 숨은 키.
 * StructuredRowsEditor가 행을 spread로 복사하므로 편집을 거쳐도 유지되며,
 * serialize 시 원본 키를 재합성해 additive key 유실을 막는다.
 */
const HIDDEN_EXTRA_KEY = "__extra";

function objectToStructuredRow<T extends Record<string, string>>(
  item: Record<string, unknown>,
  keys: readonly (keyof T & string)[],
): T {
  const row: Record<string, string> = {};
  keys.forEach((key) => {
    row[key] = formatObjectValue(item[key]);
  });
  const extras: Record<string, unknown> = {};
  Object.keys(item).forEach((key) => {
    if (!(keys as readonly string[]).includes(key)) {
      extras[key] = item[key];
    }
  });
  if (Object.keys(extras).length > 0) {
    row[HIDDEN_EXTRA_KEY] = JSON.stringify(extras);
  }
  return row as T;
}

function parseStructuredRows<T extends Record<string, string>>(
  value: string | null | undefined,
  keys: readonly (keyof T & string)[],
  mapLegacyText: (text: string) => T,
  skipObjectItem?: (item: Record<string, unknown>) => boolean,
): T[] {
  const trimmed = value?.trim();
  if (!trimmed) return [];

  const rowFromText = (text: string): T | null => {
    const normalized = text.trim();
    return normalized ? mapLegacyText(normalized) : null;
  };

  const rowFromObject = (item: Record<string, unknown>): T | null => {
    if (skipObjectItem?.(item)) return null;
    const row = objectToStructuredRow<T>(item, keys);
    if (hasStructuredRowValue(row, keys)) return row;

    const fallback = rowFromText(stringifyJsonObjectItem(item));
    return fallback && hasStructuredRowValue(fallback, keys) ? fallback : null;
  };

  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (Array.isArray(parsed)) {
      return parsed.flatMap((item): T[] => {
        const row = isPlainObject(item)
          ? rowFromObject(item)
          : rowFromText(stringifyJsonArrayItem(item));
        return row && hasStructuredRowValue(row, keys) ? [row] : [];
      });
    }

    if (isPlainObject(parsed)) {
      const row = rowFromObject(parsed);
      return row && hasStructuredRowValue(row, keys) ? [row] : [];
    }

    const row = rowFromText(stringifyJsonArrayItem(parsed));
    return row && hasStructuredRowValue(row, keys) ? [row] : [];
  } catch {
    const row = rowFromText(trimmed);
    return row && hasStructuredRowValue(row, keys) ? [row] : [];
  }
}

function serializeStructuredRows<T extends Record<string, string>>(
  rows: T[],
  keys: readonly (keyof T & string)[],
): string {
  const items = rows
    .filter((row) => hasStructuredRowValue(row, keys))
    .map((row) => {
      const item: Record<string, unknown> = {};
      const extraRaw = row[HIDDEN_EXTRA_KEY];
      if (extraRaw) {
        try {
          const extras = JSON.parse(extraRaw) as unknown;
          if (isPlainObject(extras)) {
            Object.assign(item, extras);
          }
        } catch {
          // 손상된 숨은 키는 재합성하지 않는다(표시 필드만 저장).
        }
      }
      keys.forEach((key) => {
        item[key] = row[key].trim();
      });
      return item;
    });

  return JSON.stringify(items);
}

const EVIDENCE_ROW_KEYS = ["field", "quote"] as const;
const AMBIGUOUS_CONDITION_ROW_KEYS = ["condition", "assumption"] as const;
const VERIFIED_FACT_ROW_KEYS = ["fact", "source"] as const;
const AI_INFERENCE_ROW_KEYS = ["inference", "basis"] as const;

export function parseEvidenceRows(value: string | null | undefined): EvidenceRow[] {
  return parseStructuredRows<EvidenceRow>(value, EVIDENCE_ROW_KEYS, (text) => ({
    field: "기타",
    quote: text,
  }));
}

export function parseAmbiguousConditionRows(value: string | null | undefined): AmbiguousConditionRow[] {
  return parseStructuredRows<AmbiguousConditionRow>(value, AMBIGUOUS_CONDITION_ROW_KEYS, (text) => ({
    condition: text,
    assumption: "",
  }));
}

export function parseVerifiedFactRows(value: string | null | undefined): VerifiedFactRow[] {
  return parseStructuredRows<VerifiedFactRow>(value, VERIFIED_FACT_ROW_KEYS, (text) => ({
    fact: text,
    source: "사용자 확인",
  }));
}

export function parseAiInferenceRows(value: string | null | undefined): AiInferenceRow[] {
  // kind=UNKNOWN 마커는 편집 가능한 일반 AI 추론으로 노출하지 않는다. 백엔드가 응답에서
  // 마커를 분리해 virtual unknowns로 내려주고 검수 저장 시 재부착하므로(마커 소유권은 서버),
  // 여기 필터는 구버전 응답·비정상 데이터에 대한 방어선이다.
  return parseStructuredRows<AiInferenceRow>(
    value,
    AI_INFERENCE_ROW_KEYS,
    (text) => ({ inference: text, basis: "" }),
    (item) => item.kind === "UNKNOWN",
  );
}

export interface UnknownItem {
  topic: string;
  reason: string;
  neededSource?: string;
}

/** 백엔드 virtual unknowns 필드([{topic,reason,neededSource}]) 표시용 파서(읽기 전용). */
export function parseUnknownItems(value: string | null | undefined): UnknownItem[] {
  const trimmed = value?.trim();
  if (!trimmed) return [];
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.flatMap((item): UnknownItem[] => {
      if (!isPlainObject(item)) return [];
      const topic = formatObjectValue(item.topic);
      if (!topic) return [];
      const reason = formatObjectValue(item.reason);
      const neededSource = formatObjectValue(item.neededSource);
      return [neededSource ? { topic, reason, neededSource } : { topic, reason }];
    });
  } catch {
    return [];
  }
}

export function serializeEvidenceRows(rows: EvidenceRow[]): string {
  return serializeStructuredRows(rows, EVIDENCE_ROW_KEYS);
}

export function serializeAmbiguousConditionRows(rows: AmbiguousConditionRow[]): string {
  return serializeStructuredRows(rows, AMBIGUOUS_CONDITION_ROW_KEYS);
}

export function serializeVerifiedFactRows(rows: VerifiedFactRow[]): string {
  return serializeStructuredRows(rows, VERIFIED_FACT_ROW_KEYS);
}

export function serializeAiInferenceRows(rows: AiInferenceRow[]): string {
  return serializeStructuredRows(rows, AI_INFERENCE_ROW_KEYS);
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
