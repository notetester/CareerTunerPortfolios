import { api } from "@/app/lib/api";
import { getAccessToken } from "@/app/lib/tokenStore";
import { isDataMockActive } from "../tutorial/tutorialStore";
import {
  dummyAgentSteps,
  dummyAnswer,
  dummyCapabilities,
  dummyFollowUp,
  dummyMediaResults,
  dummyModelAnswer,
  dummyQuestions,
  dummyReport,
  dummySession,
} from "../tutorial/dummyData";
import type {
  AvatarSession,
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
  MediaAnalysis,
  MediaCapabilities,
  RealtimeSession,
  SaveMediaAnalysisRequest,
  SubmitAnswerRequest,
  VoiceAnalysisResult,
} from "../types/interview";

// 백엔드 계약: /api/interview/** , /api/file/**
// 컨트롤러는 ApiResponse<T> envelope 로 응답하고, api() 래퍼가 data 만 풀어서 돌려준다.
//
// 튜토리얼 모드(isDataMockActive)에서는 백엔드/AI 호출 없이 tutorial/dummyData 를 반환한다.
// 데이터성 탭(질문·복습·리포트)은 이 분기만으로 더미가 채워진다.
// 음성/아바타(realtime·avatar)는 외부 SDK 연결이라 여기서 막지 않고 탭에서 처리한다(단계 C).

/** 데모/튜토리얼에서 AI 호출처럼 보이도록 더미 응답을 잠깐 지연시킨다. */
const mockDelay = <T>(value: T, ms = 800): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(value), ms));

/** 내 면접 세션 목록 (최근 기록). */
export function listInterviewSessions(): Promise<InterviewSession[]> {
  if (isDataMockActive()) return Promise.resolve([dummySession]);
  return api<InterviewSession[]>("/interview/sessions", { method: "GET" });
}

/** 면접 세션 생성 (지원 건 + 모드 선택). */
export function createInterviewSession(
  request: CreateInterviewSessionRequest,
): Promise<InterviewSession> {
  if (isDataMockActive()) return Promise.resolve({ ...dummySession, mode: request.mode });
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
  if (isDataMockActive()) return mockDelay(dummyQuestions, 900);
  return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/generate-questions`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션의 질문 목록 조회. */
export function listSessionQuestions(sessionId: number): Promise<InterviewQuestion[]> {
  if (isDataMockActive()) return Promise.resolve(dummyQuestions);
  return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/questions`, { method: "GET" });
}

/** 질문에 답변 제출 → AI 평가(점수/피드백/개선답변) 반환. */
export function submitAnswer(
  questionId: number,
  request: SubmitAnswerRequest,
): Promise<InterviewAnswer> {
  if (isDataMockActive()) return mockDelay(dummyAnswer(questionId), 500);
  return api<InterviewAnswer>(`/interview/questions/${questionId}/answers`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 질문에 대한 모범답안 생성(학습용). 답변 제출 전에도 호출 가능. */
export function getModelAnswer(questionId: number): Promise<{ modelAnswer: string }> {
  if (isDataMockActive()) return mockDelay({ modelAnswer: dummyModelAnswer }, 700);
  return api<{ modelAnswer: string }>(`/interview/questions/${questionId}/model-answer`, {
    method: "POST",
  });
}

/** 질문 + 직전 답변 기반 꼬리 질문 생성 → 갱신된 질문 목록 반환. */
export function generateFollowUps(
  questionId: number,
  request: GenerateFollowUpsRequest = {},
): Promise<InterviewQuestion[]> {
  if (isDataMockActive()) return mockDelay([...dummyQuestions, dummyFollowUp], 800);
  return api<InterviewQuestion[]>(`/interview/questions/${questionId}/follow-ups`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션 진행 상태(다음 질문/종료 여부) 조회. */
export function getInterviewProgress(sessionId: number): Promise<InterviewProgress> {
  if (isDataMockActive()) {
    return Promise.resolve({
      sessionId,
      totalQuestions: dummyQuestions.length,
      answeredQuestions: dummyQuestions.length,
      finished: true,
      currentQuestion: null,
    });
  }
  return api<InterviewProgress>(`/interview/sessions/${sessionId}/progress`, { method: "GET" });
}

/** 멀티에이전트 진행 단계 트레이스 조회 (Evaluator/Critic 등). */
export function getAgentSteps(sessionId: number): Promise<InterviewAgentStep[]> {
  if (isDataMockActive()) return Promise.resolve(dummyQuestions.flatMap((q) => dummyAgentSteps(q.id)));
  return api<InterviewAgentStep[]>(`/interview/sessions/${sessionId}/agent-steps`, { method: "GET" });
}

/** 실시간 음성 면접관 세션 발급 (ephemeral key). 프런트는 이 키로 OpenAI Realtime 에 직접 WebRTC 연결. */
export function createRealtimeSession(sessionId: number): Promise<RealtimeSession> {
  // 튜토리얼: 실제 WebRTC 연결이라 여기서 막지 않고 탭에서 더미 흐름 처리(단계 C).
  return api<RealtimeSession>(`/interview/sessions/${sessionId}/realtime`, { method: "POST" });
}

/** 세션 종료 → AI 종합 리포트 생성/조회. */
export function getInterviewReport(sessionId: number): Promise<InterviewReport> {
  if (isDataMockActive()) return mockDelay(dummyReport, 700);
  return api<InterviewReport>(`/interview/sessions/${sessionId}/report`, { method: "GET" });
}

// ───── 음성/아바타 면접 분석 : /api/interview/media·sessions/** ─────

/** 외부 키(Inworld/HeyGen) 보유 여부 — 기능 활성/비활성 사전 판단용. */
export function getMediaCapabilities(): Promise<MediaCapabilities> {
  if (isDataMockActive()) return Promise.resolve(dummyCapabilities);
  return api<MediaCapabilities>("/interview/media/capabilities", { method: "GET" });
}

/**
 * 음성 감정 분석 (Inworld voice profiling, 키는 서버측).
 * audioBase64 는 16kHz mono PCM16(LINEAR16). 오디오는 분석 후 버려진다.
 */
export function analyzeVoice(
  sessionId: number,
  audioBase64: string,
  sampleRateHertz = 16000,
): Promise<VoiceAnalysisResult> {
  return api<VoiceAnalysisResult>(`/interview/sessions/${sessionId}/voice-analysis`, {
    method: "POST",
    body: JSON.stringify({ audioBase64, sampleRateHertz, language: "ko" }),
  });
}

/** 아바타 화상 면접 세션 토큰 발급 (LiveAvatar). 질문 미생성 시 400. */
export function createAvatarSession(sessionId: number): Promise<AvatarSession> {
  // 튜토리얼: 실제 LiveAvatar SDK 연결이라 여기서 막지 않고 탭에서 더미 흐름 처리(단계 C).
  return api<AvatarSession>(`/interview/sessions/${sessionId}/avatar-token`, { method: "POST" });
}

/** 온디바이스 분석 결과(트랜스크립트+지표+점수) 저장. 원본 미디어는 올리지 않는다. */
export function saveMediaResult(
  sessionId: number,
  request: SaveMediaAnalysisRequest,
): Promise<MediaAnalysis> {
  if (isDataMockActive()) {
    return Promise.resolve({
      id: -990000,
      interviewSessionId: sessionId,
      kind: request.kind,
      transcript: request.transcript,
      metrics: request.metrics,
      score: request.score,
      scoreDetail: request.scoreDetail,
      createdAt: "2026-06-14T10:10:00",
    });
  }
  return api<MediaAnalysis>(`/interview/sessions/${sessionId}/media-results`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

/** 세션의 저장된 음성/영상 분석 결과 목록 (최신순). */
export function listMediaResults(sessionId: number): Promise<MediaAnalysis[]> {
  if (isDataMockActive()) return Promise.resolve(dummyMediaResults);
  return api<MediaAnalysis[]>(`/interview/sessions/${sessionId}/media-results`, { method: "GET" });
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
