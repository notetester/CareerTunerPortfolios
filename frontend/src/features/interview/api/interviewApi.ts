import { api } from "@/app/lib/api";
import { getAccessToken } from "@/app/lib/tokenStore";
import type {
  CreateInterviewSessionRequest,
  FileAsset,
  GenerateFollowUpsRequest,
  GenerateQuestionsRequest,
  InterviewAgentStep,
  InterviewAnswer,
  InterviewProgress,
  InterviewQuestion,
  InterviewReport,
  InterviewSession,
  RealtimeSession,
  SubmitAnswerRequest,
} from "../types/interview";

// 백엔드 계약: /api/interview/** , /api/file/**
// 컨트롤러는 ApiResponse<T> envelope 로 응답하고, api() 래퍼가 data 만 풀어서 돌려준다.

/** 내 면접 세션 목록 (최근 기록). */
export function listInterviewSessions(): Promise<InterviewSession[]> {
  return api<InterviewSession[]>("/interview/sessions", { method: "GET" });
}

/** 면접 세션 생성 (지원 건 + 모드 선택). */
export function createInterviewSession(
  request: CreateInterviewSessionRequest,
): Promise<InterviewSession> {
  return api<InterviewSession>("/interview/sessions", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션에 대한 AI 예상 질문 생성. */
export function generateExpectedQuestions(
  sessionId: number,
  request: GenerateQuestionsRequest,
): Promise<InterviewQuestion[]> {
  return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/generate-questions`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션의 질문 목록 조회. */
export function listSessionQuestions(sessionId: number): Promise<InterviewQuestion[]> {
  return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/questions`, { method: "GET" });
}

/** 질문에 답변 제출 → AI 평가(점수/피드백/개선답변) 반환. */
export function submitAnswer(
  questionId: number,
  request: SubmitAnswerRequest,
): Promise<InterviewAnswer> {
  return api<InterviewAnswer>(`/interview/questions/${questionId}/answers`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 질문에 대한 모범답안 생성(학습용). 답변 제출 전에도 호출 가능. */
export function getModelAnswer(questionId: number): Promise<{ modelAnswer: string }> {
  return api<{ modelAnswer: string }>(`/interview/questions/${questionId}/model-answer`, {
    method: "POST",
  });
}

/** 질문 + 직전 답변 기반 꼬리 질문 생성 → 갱신된 질문 목록 반환. */
export function generateFollowUps(
  questionId: number,
  request: GenerateFollowUpsRequest = {},
): Promise<InterviewQuestion[]> {
  return api<InterviewQuestion[]>(`/interview/questions/${questionId}/follow-ups`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션 진행 상태(다음 질문/종료 여부) 조회. */
export function getInterviewProgress(sessionId: number): Promise<InterviewProgress> {
  return api<InterviewProgress>(`/interview/sessions/${sessionId}/progress`, { method: "GET" });
}

/** 멀티에이전트 진행 단계 트레이스 조회 (Evaluator/Critic 등). */
export function getAgentSteps(sessionId: number): Promise<InterviewAgentStep[]> {
  return api<InterviewAgentStep[]>(`/interview/sessions/${sessionId}/agent-steps`, { method: "GET" });
}

/** 실시간 음성 면접관 세션 발급 (ephemeral key). 프런트는 이 키로 OpenAI Realtime 에 직접 WebRTC 연결. */
export function createRealtimeSession(sessionId: number): Promise<RealtimeSession> {
  return api<RealtimeSession>(`/interview/sessions/${sessionId}/realtime`, { method: "POST" });
}

/** 세션 종료 → AI 종합 리포트 생성/조회. */
export function getInterviewReport(sessionId: number): Promise<InterviewReport> {
  return api<InterviewReport>(`/interview/sessions/${sessionId}/report`, { method: "GET" });
}

// ───── 파일(음성/영상) 업로드 · 다운로드 : /api/file/** ─────

/** 음성/영상 등 파일 업로드 → file_asset 메타 반환. */
export function uploadFile(
  file: Blob,
  kind: FileAsset["kind"],
  options: { fileName?: string; refType?: string; refId?: number } = {},
): Promise<FileAsset> {
  const form = new FormData();
  form.append("file", file, options.fileName ?? "upload");
  form.append("kind", kind);
  if (options.refType) form.append("refType", options.refType);
  if (options.refId != null) form.append("refId", String(options.refId));
  // api() 는 FormData 면 Content-Type 을 직접 지정하지 않고(boundary 자동), 인증 헤더만 붙인다.
  return api<FileAsset>("/file/upload", { method: "POST", body: form });
}

// api.ts 의 BASE 결정 규칙과 동일하게 맞춘다(공통 모듈을 수정하지 않기 위해 중복).
const FILE_BASE =
  (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/+$/, "") || "/api";

/**
 * 업로드 파일을 인증 헤더와 함께 받아 재생 가능한 object URL 로 변환한다.
 * (다운로드 엔드포인트는 바이너리라 ApiResponse envelope 가 아니므로 별도 fetch.)
 * 사용 후 URL.revokeObjectURL 로 해제할 것.
 */
export async function fetchFileObjectUrl(fileId: number): Promise<string> {
  const token = getAccessToken();
  const res = await fetch(`${FILE_BASE}/file/${fileId}/content`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    throw new Error(`파일을 불러오지 못했습니다 (${res.status})`);
  }
  return URL.createObjectURL(await res.blob());
}
