import { api, ApiError } from "@/app/lib/api";
import type { AiModelChoice } from "@/app/components/ai/ModelPicker";
import { runWithAiCharge } from "@/features/billing/api/aiChargePreviewApi";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken, subscribeTokenStore } from "@/app/lib/tokenStore";
import { isDataMockActive } from "../tutorial/tutorialStore";
import { createAnswerSubmissionId } from "../lib/answerSubmissionId";
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
  AvatarScoreServerResult,
  InterviewAnswer,
  InterviewProgress,
  InterviewQuestion,
  InterviewReport,
  InterviewSession,
  MediaAnalysis,
  MediaCapabilities,
  RealtimeSession,
  SaveMediaAnalysisRequest,
  SessionPageResponse,
  SessionReview,
  SubmitAnswerRequest,
  TranscriptLine,
  TranscribeResult,
  VoiceScoreServerResult,
} from "../types/interview";

const pendingAnswerSubmissionIds = new Map<number, { fingerprint: string; id: string }>();
interface PendingQuestionOperation {
  key: string;
  baseline: string | null;
}
const pendingQuestionOperations = new Map<number, PendingQuestionOperation>();
const pendingFollowUpOperations = new Map<number, PendingQuestionOperation>();

subscribeTokenStore((event) => {
  if (event === "refreshed") return;
  pendingAnswerSubmissionIds.clear();
  pendingQuestionOperations.clear();
  pendingFollowUpOperations.clear();
});

function questionSignature(questions: InterviewQuestion[]): string {
  return questions.map((question) => question.id).sort((a, b) => a - b).join(",");
}

function followUpSignature(questions: InterviewQuestion[], parentQuestionId: number): string {
  return questions
    .filter((question) => question.parentQuestionId === parentQuestionId)
    .map((question) => question.id)
    .sort((a, b) => a - b)
    .join(",");
}

function isMutationOutcomeUncertain(error: unknown): boolean {
  if (error instanceof ApiError) {
    return error.code === "OUTAGE_MUTATION_UNCERTAIN"
      || error.status === 408
      || error.status === 425
      || error.status === 429
      || error.status >= 500;
  }
  return true;
}

async function listQuestionsSafely(sessionId: number): Promise<InterviewQuestion[] | null> {
  try {
    return await listSessionQuestions(sessionId);
  } catch {
    return null;
  }
}

async function reconcileQuestionOperation(
  sessionId: number,
  baseline: string | null,
  signature: (questions: InterviewQuestion[]) => string,
): Promise<InterviewQuestion[] | null> {
  if (baseline == null) return null;
  for (const delay of [250, 750, 1_500]) {
    await new Promise((resolve) => window.setTimeout(resolve, delay));
    const current = await listQuestionsSafely(sessionId);
    if (current && signature(current) !== baseline) return current;
  }
  return null;
}

function answerSubmissionFingerprint(request: SubmitAnswerRequest): string {
  return JSON.stringify([
    request.answerText,
    request.audioFileId ?? null,
    request.videoFileId ?? null,
    request.audioUrl ?? null,
    request.videoUrl ?? null,
    request.modelAnswer ?? null,
    request.voiceScore ?? null,
    request.visualScore ?? null,
  ]);
}

// 백엔드 계약: /api/interview/** , /api/file/**
// 컨트롤러는 ApiResponse<T> envelope 로 응답하고, api() 래퍼가 data 만 풀어서 돌려준다.
//
// 튜토리얼 모드(isDataMockActive)에서는 백엔드/AI 호출 없이 tutorial/dummyData 를 반환한다.
// 데이터성 탭(질문·복습·리포트)은 이 분기만으로 더미가 채워진다.
// 음성/아바타(realtime·avatar)는 외부 SDK 연결이라 여기서 막지 않고 탭에서 처리한다(단계 C).

/** 데모/튜토리얼에서 AI 호출처럼 보이도록 더미 응답을 잠깐 지연시킨다. */
const mockDelay = <T>(value: T, ms = 800): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(value), ms));
let nextTutorialFileAssetId = 8_000_000;

/** 내 면접 세션 목록 (최근 기록). 더보기 누적용 페이지 응답. */
export function listInterviewSessions(page = 0, size = 10): Promise<SessionPageResponse> {
  if (isDataMockActive())
    return Promise.resolve({ sessions: [dummySession], total: 1, page: 0, size, hasNext: false });
  return api<SessionPageResponse>(`/interview/sessions?page=${page}&size=${size}`, { method: "GET" });
}

/** 면접 기록 삭제 (soft delete). */
export function deleteInterviewSession(sessionId: number): Promise<void> {
  if (isDataMockActive()) return Promise.resolve();
  return api<void>(`/interview/sessions/${sessionId}`, { method: "DELETE" });
}

/** 모바일에서 이 세션을 데스크톱으로 보내기 — 데스크톱 폴러가 알림과 딥링크를 받는다. */
export function dispatchSessionToDesktop(sessionId: number): Promise<void> {
  return api<void>(`/interview/sessions/${sessionId}/dispatch`, {
    method: "POST",
    body: JSON.stringify({ target: "DESKTOP" }),
  });
}

/** 세션 복원(=복습) 시각 기록. */
export function markSessionResumed(sessionId: number): Promise<void> {
  if (isDataMockActive()) return Promise.resolve();
  return api<void>(`/interview/sessions/${sessionId}/resume`, { method: "POST" });
}

/**
 * 음성 모의면접 트랜스크립트 → 질문별 내용 채점(interview_answer 저장). 채점한 문항 수 반환.
 * questionLimit(1~6)을 주면 앞에서 그 수만큼만 채점 대상 — 체험판(1문제)은 1을 넘겨
 * 미진행 질문에 억지 매칭·저장되는 것을 막는다.
 */
export function scoreVoiceTranscript(
  sessionId: number,
  transcript: TranscriptLine[],
  questionLimit?: number,
): Promise<number> {
  if (isDataMockActive()) return Promise.resolve(transcript.some((l) => l.role === "user") ? 3 : 0);
  return runWithAiCharge("INTERVIEW_VOICE_SCORING", (headers) =>
    api<number>(`/interview/sessions/${sessionId}/score-voice`, {
      method: "POST",
      headers,
      body: JSON.stringify({ transcript, questionLimit }),
    }));
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

/** 세션에 대한 AI 예상 질문 생성. model 로 생성 모델 명시 선택(AUTO=현행 폴백, 채점 경로엔 무관). */
export async function generateExpectedQuestions(
  sessionId: number,
  request: GenerateQuestionsRequest,
  model: AiModelChoice = "AUTO",
): Promise<InterviewQuestion[]> {
  if (isDataMockActive()) return mockDelay(dummyQuestions, 900);
  let pending = pendingQuestionOperations.get(sessionId);
  if (!pending) {
    const current = await listQuestionsSafely(sessionId);
    pending = {
      key: `AI_USAGE:${createAnswerSubmissionId()}`,
      baseline: current ? questionSignature(current) : null,
    };
    pendingQuestionOperations.set(sessionId, pending);
  } else {
    const current = await listQuestionsSafely(sessionId);
    if (current && pending.baseline != null && questionSignature(current) !== pending.baseline) {
      pendingQuestionOperations.delete(sessionId);
      return current;
    }
  }

  let mutationStarted = false;
  try {
    const result = await runWithAiCharge("INTERVIEW_QUESTION_GEN", (headers) => {
      mutationStarted = true;
      const modelQuery = model && model !== "AUTO" ? `?model=${model}` : "";
      return api<InterviewQuestion[]>(`/interview/sessions/${sessionId}/generate-questions${modelQuery}`, {
        method: "POST",
        headers,
        body: JSON.stringify(request),
      });
    }, pending.key);
    if (pendingQuestionOperations.get(sessionId)?.key === pending.key) {
      pendingQuestionOperations.delete(sessionId);
    }
    return result;
  } catch (error) {
    if (!mutationStarted || !isMutationOutcomeUncertain(error)) {
      if (pendingQuestionOperations.get(sessionId)?.key === pending.key) {
        pendingQuestionOperations.delete(sessionId);
      }
      throw error;
    }
    const reconciled = await reconcileQuestionOperation(sessionId, pending.baseline, questionSignature);
    if (reconciled) {
      if (pendingQuestionOperations.get(sessionId)?.key === pending.key) {
        pendingQuestionOperations.delete(sessionId);
      }
      return reconciled;
    }
    throw error;
  }
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
  const fingerprint = answerSubmissionFingerprint(request);
  const pending = pendingAnswerSubmissionIds.get(questionId);
  const clientSubmissionId = request.clientSubmissionId
    ?? (pending?.fingerprint === fingerprint ? pending.id : createAnswerSubmissionId());
  if (!request.clientSubmissionId) {
    pendingAnswerSubmissionIds.set(questionId, { fingerprint, id: clientSubmissionId });
  }
  const requestWithId = { ...request, clientSubmissionId };
  if (isDataMockActive()) {
    pendingAnswerSubmissionIds.delete(questionId);
    return mockDelay({
      ...dummyAnswer(questionId),
      audioUrl: request.audioUrl ?? null,
      videoUrl: request.videoUrl ?? null,
      clientSubmissionId,
    }, 500);
  }
  return runWithAiCharge("INTERVIEW_ANSWER_EVAL", (headers) =>
    api<InterviewAnswer>(`/interview/questions/${questionId}/answers`, {
      method: "POST",
      headers,
      body: JSON.stringify(requestWithId),
    })).then((answer) => {
      const active = pendingAnswerSubmissionIds.get(questionId);
      if (active?.id === clientSubmissionId) pendingAnswerSubmissionIds.delete(questionId);
      return answer;
    });
}

/** 답변 내용·채점은 유지하고 선택한 음성/영상 원본만 물리 삭제한다. */
export function deleteAnswerMedia(answerId: number, kind: "AUDIO" | "VIDEO"): Promise<void> {
  if (isDataMockActive()) return Promise.resolve();
  return api<void>(`/interview/answers/${answerId}/media/${kind}`, { method: "DELETE" });
}

/** 질문에 대한 모범답안 생성(학습용). 답변 제출 전에도 호출 가능. */
export function getModelAnswer(questionId: number): Promise<{ modelAnswer: string }> {
  if (isDataMockActive()) return mockDelay({ modelAnswer: dummyModelAnswer }, 700);
  return runWithAiCharge("INTERVIEW_MODEL_ANSWER", (headers) =>
    api<{ modelAnswer: string }>(`/interview/questions/${questionId}/model-answer`, {
      method: "POST",
      headers,
    }));
}

/** 질문 + 직전 답변 기반 꼬리 질문 생성 → 갱신된 질문 목록 반환. */
export async function generateFollowUps(
  questionId: number,
  request: GenerateFollowUpsRequest = {},
  sessionId?: number,
): Promise<InterviewQuestion[]> {
  if (isDataMockActive()) return mockDelay([...dummyQuestions, dummyFollowUp], 800);
  let pending = pendingFollowUpOperations.get(questionId);
  if (!pending) {
    const current = sessionId == null ? null : await listQuestionsSafely(sessionId);
    pending = {
      key: `AI_USAGE:${createAnswerSubmissionId()}`,
      baseline: current ? followUpSignature(current, questionId) : null,
    };
    pendingFollowUpOperations.set(questionId, pending);
  } else if (sessionId != null) {
    const current = await listQuestionsSafely(sessionId);
    if (current && pending.baseline != null
        && followUpSignature(current, questionId) !== pending.baseline) {
      pendingFollowUpOperations.delete(questionId);
      return current;
    }
  }

  let mutationStarted = false;
  try {
    const result = await runWithAiCharge("INTERVIEW_FOLLOWUP_GEN", (headers) => {
      mutationStarted = true;
      return api<InterviewQuestion[]>(`/interview/questions/${questionId}/follow-ups`, {
        method: "POST",
        headers,
        body: JSON.stringify(request),
      });
    }, pending.key);
    if (pendingFollowUpOperations.get(questionId)?.key === pending.key) {
      pendingFollowUpOperations.delete(questionId);
    }
    return result;
  } catch (error) {
    if (!mutationStarted || !isMutationOutcomeUncertain(error)) {
      if (pendingFollowUpOperations.get(questionId)?.key === pending.key) {
        pendingFollowUpOperations.delete(questionId);
      }
      throw error;
    }
    const reconciled = sessionId == null ? null : await reconcileQuestionOperation(
      sessionId,
      pending.baseline,
      (questions) => followUpSignature(questions, questionId),
    );
    if (reconciled) {
      if (pendingFollowUpOperations.get(questionId)?.key === pending.key) {
        pendingFollowUpOperations.delete(questionId);
      }
      return reconciled;
    }
    throw error;
  }
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

/**
 * 실시간 음성 면접관 세션 발급 (ephemeral key). 프런트는 이 키로 OpenAI Realtime 에 직접 WebRTC 연결.
 * questionLimit(1~6)을 주면 그 수만큼만 질문한다 — 체험판(시연)은 1.
 */
export function createRealtimeSession(sessionId: number, questionLimit?: number): Promise<RealtimeSession> {
  // 튜토리얼: 실제 WebRTC 연결이라 여기서 막지 않고 탭에서 더미 흐름 처리(단계 C).
  const query = questionLimit ? `?questionLimit=${questionLimit}` : "";
  return runWithAiCharge("INTERVIEW_VOICE_SESSION", (headers) =>
    api<RealtimeSession>(`/interview/sessions/${sessionId}/realtime${query}`, {
      method: "POST",
      headers,
    }));
}

/** 세션 종료 → AI 종합 리포트 생성/조회. */
export function getInterviewReport(sessionId: number): Promise<InterviewReport> {
  if (isDataMockActive()) return mockDelay(dummyReport, 700);
  return runWithAiCharge("INTERVIEW_REPORT", (headers) =>
    api<InterviewReport>(`/interview/sessions/${sessionId}/report`, {
      method: "GET",
      headers,
    }));
}

/** 지난 세션 복기: 질문 + 모범답안 + 내 최신 답변/점수 (최근 면접 기록에서 들어가 보기). */
export function getSessionReview(sessionId: number): Promise<SessionReview> {
  if (isDataMockActive()) {
    return Promise.resolve({
      sessionId,
      mode: dummySession.mode,
      items: dummyQuestions.map((q) => ({
        questionId: q.id,
        question: q.question,
        questionType: q.questionType ?? "EXPECTED",
        modelAnswer: dummyModelAnswer,
        answerId: null,
        answerText: null,
        audioUrl: null,
        videoUrl: null,
        score: null,
        feedback: null,
        improvedAnswer: null,
        voiceScore: null,
        visualScore: null,
      })),
    });
  }
  return api<SessionReview>(`/interview/sessions/${sessionId}/review`, { method: "GET" });
}

// ───── 음성/아바타 면접 분석 : /api/interview/media·sessions/** ─────

/** 외부 키(HeyGen) 보유 여부 — 기능 활성/비활성 사전 판단용. */
export function getMediaCapabilities(): Promise<MediaCapabilities> {
  if (isDataMockActive()) return Promise.resolve(dummyCapabilities);
  return api<MediaCapabilities>("/interview/media/capabilities", { method: "GET" });
}

/**
 * 음성 답변 → 자체 추론 서버 점수 (ADR-006).
 * audioBase64 는 녹음 원본(webm 등). 글자수·군말수·응답지연은 프런트가 계산해 함께 보낸다.
 * 이 분석 요청의 base64 사본은 점수 산출 후 보관하지 않는다. 제출 원본은 file_asset에 별도 저장한다.
 */
export function scoreVoiceServer(
  sessionId: number,
  payload: {
    audioBase64: string;
    audioFormat?: string;
    transcriptChars?: number;
    fillerCount?: number;
    latencySec?: number;
  },
  signal?: AbortSignal,
): Promise<VoiceScoreServerResult> {
  return runWithAiCharge("INTERVIEW_VOICE_SCORING", (headers) =>
    api<VoiceScoreServerResult>(`/interview/sessions/${sessionId}/voice-score`, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
      signal,
    }));
}

/**
 * 아바타 화상면접 → 자체 추론 서버 음성+영상 점수 (late fusion, ADR-006/007).
 * videoBase64 는 녹화 원본(webm 등). serve 가 webm 1개에서 음성·영상 피처를 함께 뽑아 결합한다.
 * 이 분석 요청의 base64 사본은 점수 산출 후 보관하지 않는다. 제출 원본은 file_asset에 별도 저장한다.
 */
export function scoreAvatarServer(
  sessionId: number,
  payload: {
    videoBase64: string;
    videoFormat?: string;
    transcriptChars?: number;
    fillerCount?: number;
    latencySec?: number;
  },
  signal?: AbortSignal,
): Promise<AvatarScoreServerResult> {
  return runWithAiCharge("INTERVIEW_VIDEO_ANALYSIS", (headers) =>
    api<AvatarScoreServerResult>(`/interview/sessions/${sessionId}/avatar-score`, {
      method: "POST",
      headers,
      body: JSON.stringify(payload),
      signal,
    }));
}

/**
 * 음성 답변 → 자체 STT 전사 (B 베이직, faster-whisper, API 0).
 * audioBase64 는 녹음 원본(webm 등). 전사 요청 사본은 보관하지 않고 제출 원본은 별도 저장한다.
 */
export function transcribeVoice(
  sessionId: number,
  audioBase64: string,
  audioFormat = "webm",
  language = "ko",
  signal?: AbortSignal,
): Promise<TranscribeResult> {
  return api<TranscribeResult>(`/interview/sessions/${sessionId}/voice-transcribe`, {
    method: "POST",
    body: JSON.stringify({ audioBase64, audioFormat, language }),
    signal,
  });
}

/** 아바타 화상 면접 세션 토큰 발급 (LiveAvatar). 질문 미생성 시 400. */
export function createAvatarSession(sessionId: number): Promise<AvatarSession> {
  // 튜토리얼: 실제 LiveAvatar SDK 연결이라 여기서 막지 않고 탭에서 더미 흐름 처리(단계 C).
  return runWithAiCharge("INTERVIEW_AVATAR_SESSION", (headers) =>
    api<AvatarSession>(`/interview/sessions/${sessionId}/avatar-token`, {
      method: "POST",
      headers,
    }));
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
      questionId: request.questionId ?? null,
      answerId: request.answerId ?? null,
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
  options: { fileName?: string; refType?: string; refId?: number; signal?: AbortSignal } = {},
): Promise<FileAsset> {
  if (isDataMockActive()) {
    const id = ++nextTutorialFileAssetId;
    return Promise.resolve({
      id,
      kind,
      refType: options.refType ?? null,
      refId: options.refId ?? null,
      originalName: options.fileName ?? "upload",
      contentType: file.type || null,
      sizeBytes: file.size,
      contentUrl: `/api/file/${id}/content`,
      createdAt: new Date().toISOString(),
    });
  }
  const form = new FormData();
  form.append("file", file, options.fileName ?? "upload");
  form.append("kind", kind);
  if (options.refType) form.append("refType", options.refType);
  if (options.refId != null) form.append("refId", String(options.refId));
  // api() 는 FormData 면 Content-Type 을 직접 지정하지 않고(boundary 자동), 인증 헤더만 붙인다.
  return api<FileAsset>("/file/upload", { method: "POST", body: form, signal: options.signal });
}

/**
 * 업로드 파일을 인증 헤더와 함께 받아 재생 가능한 object URL 로 변환한다.
 * (다운로드 엔드포인트는 바이너리라 ApiResponse envelope 가 아니므로 별도 fetch.)
 * 베이스 URL 은 apiBase() 단일 소스를 사용한다(런타임 오버라이드 반영).
 * 사용 후 URL.revokeObjectURL 로 해제할 것.
 */
export async function fetchFileObjectUrl(fileId: number): Promise<string> {
  const token = getAccessToken();
  const res = await fetch(`${apiBase()}/file/${fileId}/content`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    throw new Error(`파일을 불러오지 못했습니다 (${res.status})`);
  }
  return URL.createObjectURL(await res.blob());
}
