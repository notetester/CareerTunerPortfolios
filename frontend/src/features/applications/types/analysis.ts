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
  // 모델 선택·실행 provenance(응답이 항상 내려주는 nullable 필드). 자동 초기 실행·strict 재분석만 값이 있고
  // 레거시 행은 NULL(표시 안 함). 계약 강화를 위해 optional 이 아니라 required-nullable 로 둔다(필드 누락을 tsc 가 잡음).
  requestedProvider: string | null;
  actualProvider: string | null;
  actualModel: string | null;
  fallbackUsed: boolean | null;
  attemptPath: string | null;
  runMode: string | null;
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
  // 모델 선택·실행 provenance(응답이 항상 내려주는 nullable 필드). 자동 초기 실행·strict 재분석만 값이 있고
  // 레거시 행은 NULL(표시 안 함). 계약 강화를 위해 optional 이 아니라 required-nullable 로 둔다(필드 누락을 tsc 가 잡음).
  requestedProvider: string | null;
  actualProvider: string | null;
  actualModel: string | null;
  fallbackUsed: boolean | null;
  attemptPath: string | null;
  runMode: string | null;
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

// 근거(evidence)의 field 값은 문장 분류기 라벨(RESPONSIBILITY 등)이나 self-rules 키(requiredSkills)라
// 원문 그대로면 영어로 노출된다. 공통 분석 텍스트 렌더 경로(stringifyJsonObjectItem)에서 한글 라벨로
// 치환한다(표시 전용 — 저장값·생성 로직은 건드리지 않는다). 조회 key 는 대문자 정규화 기준이라
// self-rules 의 requiredSkills 는 REQUIREDSKILLS 로 매핑한다.
// 회귀 방지 책임 분리: as const satisfies 는 키 계약(누락/오타)과 라벨 리터럴 타입 보존을 담당하고,
// 주요 라벨 '값' 회귀는 analysis.contract.test.ts 의 리터럴 배정문이 잡는다(둘 다 tsc --noEmit).
// 실제 변환 동작(trim().toUpperCase()·미매핑 폴백) 검증은 런타임 테스트 러너 도입 시 보강한다.
export type EvidenceFieldLabelKey =
  | "RESPONSIBILITY"
  | "REQUIRED"
  | "PREFERRED"
  | "QUALIFICATION"
  | "TECH_STACK"
  | "EMPLOYMENT_CONDITION"
  | "BENEFIT"
  | "APPLICATION_INFO"
  | "COMPANY_INFO"
  | "SECTION_HEADER"
  | "OTHER"
  | "REQUIREDSKILLS"
  | "PREFERREDSKILLS";

export const EVIDENCE_FIELD_LABELS = {
  RESPONSIBILITY: "주요 업무",
  REQUIRED: "필수 요건",
  PREFERRED: "우대 사항",
  QUALIFICATION: "자격 요건",
  TECH_STACK: "기술 스택",
  EMPLOYMENT_CONDITION: "근무 조건",
  BENEFIT: "복리후생",
  APPLICATION_INFO: "지원 안내",
  COMPANY_INFO: "회사 정보",
  SECTION_HEADER: "구분",
  OTHER: "기타",
  REQUIREDSKILLS: "필수 역량",
  PREFERREDSKILLS: "우대 역량",
} as const satisfies Record<EvidenceFieldLabelKey, string>;

function translateEvidenceField(field: string): string {
  if (!field) return field;
  const key = field.trim().toUpperCase();
  return (EVIDENCE_FIELD_LABELS as Record<string, string>)[key] ?? field;
}

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
  const field = translateEvidenceField(formatObjectValue(item.field));
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

/**
 * verifiedFacts 표시 전용 뷰(읽기 전용 · D-4d). {@link parseVerifiedFactRows} 와 동일한 행 집합을 쓰되,
 * UI에 노출되지 않아 {@link HIDDEN_EXTRA_KEY} 에 보관되던 sourceKind/sourceRef 를 타입 있는 형태로 노출한다.
 * 검수 편집(StructuredRowsEditor)·serialize 경로와 무관하다(기존 파서 시그니처·동작 불변, additive).
 */
export interface VerifiedFactView {
  fact: string;
  source: string;
  sourceKind: string | null;
  sourceRef: string | null;
}

function readHiddenExtra(row: Record<string, string>): Record<string, unknown> {
  const raw = row[HIDDEN_EXTRA_KEY];
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    return isPlainObject(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function readExtraString(extra: Record<string, unknown>, key: string): string | null {
  const value = extra[key];
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function parseVerifiedFactViews(value: string | null | undefined): VerifiedFactView[] {
  return parseVerifiedFactRows(value).map((row) => {
    const extra = readHiddenExtra(row);
    return {
      fact: row.fact,
      source: row.source,
      sourceKind: readExtraString(extra, "sourceKind"),
      sourceRef: readExtraString(extra, "sourceRef"),
    };
  });
}

/** WEB 근거 fact 여부(sourceKind === "WEB"). */
export function isWebFact(view: VerifiedFactView): boolean {
  return view.sourceKind === "WEB";
}

/** sourceRef 가 http/https URL 이면 그 URL, 아니면 null(링크 대신 텍스트로 표시하는 안전 가드). */
export function webFactLinkUrl(view: VerifiedFactView): string | null {
  const ref = view.sourceRef;
  if (!ref) return null;
  return /^https?:\/\//i.test(ref) ? ref : null;
}

/**
 * 기업분석 재조회 권장 시점(refreshRecommendedAt) 경과 여부(사용자 신선도 표시 · D-5).
 * null/공백/파싱 불가면 false(표시 안 함). 관리자 isRefreshNeeded 와 동일한 날짜 비교(<= now).
 */
export function isCompanyAnalysisRefreshDue(refreshRecommendedAt: string | null | undefined): boolean {
  if (!refreshRecommendedAt) return false;
  const time = new Date(refreshRecommendedAt).getTime();
  return !Number.isNaN(time) && time <= Date.now();
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

// ── 표준 코드 → 한글 표시 라벨 매퍼 (E2) ──
// employmentType·experienceLevel 은 저장 타입이 String 이지만 생성 파이프라인이 표준 코드로 정규화한다
// (backend BAnalysisGenerationService.normalizeEmploymentType/normalizeExperienceLevel, 미분류는 MID 수렴).
// 따라서 알려진 코드는 결정적으로 한글 라벨로 표시하고, 레거시·사용자 편집 등 미지 값은 원문 그대로 통과한다.
// 표시 전용 — 저장/API 값은 코드를 유지한다(편집 폼에서 라벨을 저장하지 말 것).
const EMPLOYMENT_TYPE_LABELS: Record<string, string> = {
  FULL_TIME: "정규직",
  CONTRACT: "계약직",
  INTERN: "인턴",
  PART_TIME: "시간제",
};

const EXPERIENCE_LEVEL_LABELS: Record<string, string> = {
  JUNIOR: "주니어",
  MID: "중급",
  SENIOR: "시니어",
};

const COMPANY_SOURCE_TYPE_LABELS: Record<string, string> = {
  WEB: "웹 조사",
  JOB_POSTING: "공고 기반",
  MANUAL: "수동 입력",
  API: "외부 API",
};

/** 고용형태 표준 코드를 한글 라벨로. 미지·레거시 값은 원문 그대로, 빈 값은 "미정". */
export function getEmploymentTypeLabel(value: string | null | undefined): string {
  if (!value) return "미정";
  return EMPLOYMENT_TYPE_LABELS[value] ?? value;
}

/** 경력수준 표준 코드를 한글 라벨로. 미지·레거시 값은 원문 그대로, 빈 값은 "미정". */
export function getExperienceLevelLabel(value: string | null | undefined): string {
  if (!value) return "미정";
  return EXPERIENCE_LEVEL_LABELS[value] ?? value;
}

/** 기업분석 출처 코드를 한글 라벨로. 미지 값은 원문 그대로, 빈 값은 null(호출부에서 "-"/"미정" 선택). */
export function getCompanySourceTypeLabel(value: string | null | undefined): string | null {
  if (!value) return null;
  return COMPANY_SOURCE_TYPE_LABELS[value] ?? value;
}

// ── 분석 provenance(생성 모델·실행 이력) 표시 헬퍼 (지원건별 모델 선택·재실행) ──

/**
 * provenance 미기록(레거시·mock·자동 무선택) 분석 행용 기본값 — 6필드 전부 null.
 * required-nullable 타입을 만족시키면서 "기록 없음"을 표현하는 fixture·과거 데이터 구성에 쓴다.
 */
export const NULL_ANALYSIS_PROVENANCE = {
  requestedProvider: null,
  actualProvider: null,
  actualModel: null,
  fallbackUsed: null,
  attemptPath: null,
  runMode: null,
} as const;

const ANALYSIS_PROVIDER_LABELS: Record<string, string> = {
  LOCAL: "자체 모델",
  CLAUDE: "Claude",
  OPENAI: "OpenAI",
  SELF_RULES: "규칙 기반",
};

const ANALYSIS_RUN_MODE_LABELS: Record<string, string> = {
  INITIAL: "초기 분석",
  MANUAL: "수동 재분석",
};

/** provider 식별자(LOCAL/CLAUDE/OPENAI/SELF_RULES)를 사람이 읽는 라벨로. 미지의 값은 원본을 그대로 쓴다. */
export function getAnalysisProviderLabel(provider: string | null | undefined): string | null {
  if (!provider) return null;
  return ANALYSIS_PROVIDER_LABELS[provider] ?? provider;
}

/** run_mode(INITIAL/MANUAL)를 라벨로. */
export function getAnalysisRunModeLabel(runMode: string | null | undefined): string | null {
  if (!runMode) return null;
  return ANALYSIS_RUN_MODE_LABELS[runMode] ?? runMode;
}

export interface AnalysisProvenanceSource {
  requestedProvider?: string | null;
  actualProvider?: string | null;
  actualModel?: string | null;
  fallbackUsed?: boolean | null;
  attemptPath?: string | null;
  runMode?: string | null;
}

export interface AnalysisProvenanceView {
  /** 실제 생성 provider 라벨(예: "Claude"). */
  actualProviderLabel: string;
  /** 실제 모델명(예: "claude-haiku-4-5"). 없으면 null. */
  actualModel: string | null;
  /** 사용자가 등록/재분석 시 고른 provider 라벨. 없으면 null(자동 체인). */
  requestedProviderLabel: string | null;
  /** 고른 provider 로 실패해 다른 모델로 폴백했는지. */
  fallbackUsed: boolean;
  /**
   * 폴백 표시 라벨. 명시 선택 폴백이면 "폴백(요청 X)", AUTO(요청 provider 없음) 폴백이면 "자동 폴백".
   * 폴백이 없었으면 null — AUTO 는 requested 가 NULL 이라 requestedProviderLabel 만으로는 폴백이 숨는다.
   */
  fallbackLabel: string | null;
  /** 실제 시도 순서 라벨(예: ["Local LLM","Claude"]). attempt_path 미기록/파싱 불가면 null. */
  attemptPathLabels: string[] | null;
  /** 실행 모드 라벨("초기 분석"/"수동 재분석"). 없으면 null. */
  runModeLabel: string | null;
}

/** attempt_path JSON(["LOCAL","CLAUDE",...])을 라벨 배열로. 비정상 값은 조용히 null(표시 생략). */
function parseAttemptPathLabels(attemptPath: string | null | undefined): string[] | null {
  if (!attemptPath) return null;
  try {
    const parsed: unknown = JSON.parse(attemptPath);
    if (!Array.isArray(parsed) || parsed.length === 0) return null;
    const labels = parsed
      .filter((token): token is string => typeof token === "string")
      .map((token) => getAnalysisProviderLabel(token))
      .filter((label): label is string => label !== null);
    return labels.length > 0 ? labels : null;
  } catch {
    return null;
  }
}

/**
 * 분석 행의 provenance 표시 뷰. <b>실제 생성 provider(actualProvider)가 기록된 행만</b> 표시 대상이다
 * (초기 등록 preferred·strict 재분석). provider 미기록(레거시·자동 무선택·mock)이면 null → 뱃지 미표시.
 */
export function parseAnalysisProvenance(
  source: AnalysisProvenanceSource | null | undefined,
): AnalysisProvenanceView | null {
  const actualLabel = getAnalysisProviderLabel(source?.actualProvider);
  if (!source || !actualLabel) {
    return null;
  }
  const requestedLabel = getAnalysisProviderLabel(source.requestedProvider);
  const fallbackUsed = source.fallbackUsed === true;
  const shownRequestedLabel = fallbackUsed ? requestedLabel : null;
  return {
    actualProviderLabel: actualLabel,
    actualModel: source.actualModel ?? null,
    // 요청=실제면 중복이라 숨긴다. 폴백이 일어난 경우에만 "요청: X" 를 따로 보여준다.
    requestedProviderLabel: shownRequestedLabel,
    fallbackUsed,
    // AUTO(요청 없음)의 폴백도 표시되도록 requested 유무로 라벨을 분기한다.
    fallbackLabel: fallbackUsed
      ? (shownRequestedLabel ? `폴백(요청 ${shownRequestedLabel})` : "자동 폴백")
      : null,
    attemptPathLabels: parseAttemptPathLabels(source.attemptPath),
    runModeLabel: getAnalysisRunModeLabel(source.runMode),
  };
}

/**
 * provenance 를 한 줄 요약 문자열로. 기록이 없으면(레거시·자동 무선택) {@code "미기록"}.
 * 관리자 상세의 단문 표시(MetaBlock)처럼 컴포넌트 대신 문자열이 필요한 곳에서 쓴다.
 */
export function formatAnalysisProvenanceSummary(source: AnalysisProvenanceSource | null | undefined): string {
  const prov = parseAnalysisProvenance(source);
  if (!prov) {
    return "미기록";
  }
  const parts = [prov.actualProviderLabel];
  if (prov.actualModel) parts.push(prov.actualModel);
  if (prov.fallbackLabel) parts.push(prov.fallbackLabel); // AUTO 폴백("자동 폴백")도 포함
  if (prov.runModeLabel) parts.push(prov.runModeLabel);
  return parts.join(" · ");
}
