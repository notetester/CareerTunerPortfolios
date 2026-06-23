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
  attachmentFileIds?: number[];
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
  /** 다음 물을 슬롯: "CASE"(지원 건) | "MODE"(면접 모드) | null(ready). */
  nextAsk: "CASE" | "MODE" | null;
  candidates: PrepCaseCandidate[];
  modes: PrepModeOption[];
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

/** 6파트 표시 메타(키 → 한글 라벨/아이콘/담당/예상 소요 ms). estMs 는 에너지바 점근 속도용. */
export const PREP_PARTS: Record<string, { label: string; icon: string; part: string; estMs: number }> = {
  PROFILE: { label: "프로필·역량 정리", icon: "👤", part: "A", estMs: 9000 },
  JOB: { label: "공고 분석", icon: "📄", part: "B", estMs: 15000 },
  FIT: { label: "적합도 분석", icon: "🎯", part: "C", estMs: 15000 },
  WRITE: { label: "자소서 교정", icon: "✍️", part: "E", estMs: 12000 },
  INTERVIEW: { label: "예상 면접 질문", icon: "🎙️", part: "D", estMs: 18000 },
  COMMUNITY: { label: "커뮤니티 큐레이션", icon: "💬", part: "F", estMs: 4000 },
};
