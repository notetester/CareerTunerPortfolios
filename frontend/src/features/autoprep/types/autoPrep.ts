// AI 오케스트레이터(자동 준비) 타입. 백엔드 com.careertuner.ai.autoprep 와 1:1.

export interface PrepSlots {
  company: string | null;
  jobTitle: string | null;
  mode: string | null;
  applicationCaseId: number | null;
}

export interface PrepPlan {
  intent: string;
  slots: PrepSlots;
  steps: string[];
}

export type PrepStatus = "DONE" | "SKIPPED" | "FAILED";

export interface PrepStepResult {
  key: string;
  status: PrepStatus;
  summary: string;
  detail?: unknown;
  elapsedMs: number;
}

export interface AutoPrepRequest {
  query?: string;
  applicationCaseId?: number | null;
  mode?: string | null;
  coverLetterText?: string | null;
  /** 자소서 첨부 fileId — WRITE(자소서 교정)가 소비. */
  attachmentFileIds?: number[];
  /** 공고(텍스트/PDF-텍스트/docx) 첨부 fileId — 지원 건이 없으면 인테이크가 본문을 뽑아 지원 건을 자동 생성. */
  jobPostingFileIds?: number[];
  /** SSE 실행 한 건의 client-generated id. 서버 명시적 취소 레지스트리에서 사용자 범위로만 사용한다. */
  runId?: string;
}

/** 인테이크 응답의 지원 건 후보(백엔드 ApplicationCaseResponse 의 필요한 필드만). */
export interface PrepCaseCandidate {
  id: number;
  companyName: string;
  jobTitle: string;
  status?: string;
}

/** 면접 모드 선택지(백엔드 ModeOption). */
export interface PrepModeOption {
  code: string;
  label: string;
}

export interface AutoPrepIntakeResponse {
  plan: PrepPlan;
  ready: boolean;
  message: string;
  /** 다음 물을 슬롯: "CASE"(지원 건) | "MODE"(면접 모드) | "EXTRACTING"(공고 추출 중 — 폴링) | null(ready). */
  nextAsk: "CASE" | "MODE" | "EXTRACTING" | null;
  candidates: PrepCaseCandidate[];
  modes: PrepModeOption[];
  /** EXTRACTING 응답에서 갓 만든(또는 추출 진행 중인) 지원 건 id — 다음 턴에 재전송한다. */
  applicationCaseId?: number | null;
}

/** 공고 추출 상태 전용 폴링 응답. 인테이크/LLM을 다시 호출하지 않고 이 값만 확인한다. */
export interface AutoPrepExtractionStatus {
  applicationCaseId: number;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | string;
  errorMessage?: string | null;
  qualityStatus?: "PASS" | "REVIEW_REQUIRED" | "FAILED" | string | null;
}

/** 업로드된 첨부 파일(백엔드 FileAssetResponse). */
export interface PrepAttachedFile {
  id: number;
  originalName: string;
  contentType?: string;
  sizeBytes?: number;
}

/** SSE 진행 이벤트 — runStream 이 콜백으로 흘려보낸다. */
export type PrepEvent =
  | { type: "plan"; plan: PrepPlan }
  | { type: "part-start"; key: string }
  | { type: "substep"; key: string; name: string; desc: string }
  | { type: "part-done"; result: PrepStepResult }
  | { type: "done"; message: string }
  | { type: "error"; message: string };
