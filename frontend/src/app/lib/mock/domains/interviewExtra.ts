// 데모/목: D 도메인(가상 면접)에서 core registry·interview.ts 가 아직 다루지 않는 나머지 엔드포인트.
// 복습(review)·모범답안·음성/아바타 분석·미디어 결과·세션 삭제/복원/음성채점을 백엔드 없이 채운다.
// 식별자·페르소나는 interview.ts / data.ts 와 동일하게 맞춘다: 세션 8002(네이버, JOB), 8001(카카오, BASIC).
import type { MockRoute } from "../registry";
import { ok, iso } from "../registry";
import type {
  SessionReview,
  MediaCapabilities,
  VoiceScoreServerResult,
  TranscribeResult,
  AvatarSession,
  MediaAnalysis,
  SaveMediaAnalysisRequest,
} from "@/features/interview/types/interview";

// ───── 모범답안 (POST /interview/questions/:id/model-answer) → api<{ modelAnswer: string }> ─────

const MODEL_ANSWERS: Record<number, string> = {
  // 세션 8002(네이버) 질문들
  90021:
    "상태의 성격에 따라 도구를 나눕니다. 서버 상태는 React Query로, 전역 UI 상태는 Zustand로 관리합니다. " +
    "Recoil은 atom 단위 구독으로 세밀한 리렌더링 제어가 강점이지만 보일러플레이트가 많고, Zustand는 스토어 하나로 단순하게 시작해 셀렉터로 구독을 최적화할 수 있어 중소 규모에 적합합니다. " +
    "실제로는 '서버 상태/전역 상태/지역 상태'를 먼저 분리하고, 전역 상태에만 외부 라이브러리를 쓰는 원칙을 설명하면 설득력이 높습니다.",
  90022:
    "STAR 구조로 답합니다. 상황(S) 목록 API 응답이 200ms 이상 지연되고 중복 호출이 발생했습니다. 과제(T) 응답 속도와 네트워크 비용을 줄여야 했습니다. " +
    "행동(A) React Query로 캐싱·중복 제거를 적용하고 페이지네이션 키를 정규화했습니다. 결과(R) 중복 호출을 70% 줄이고 체감 로딩을 35% 단축했습니다. 결과는 반드시 수치로 제시하세요.",
  90023:
    "갈등 상황을 비난 없이 사실 중심으로 설명하고, 본인이 한 구체적 행동과 합의 과정을 드러냅니다. " +
    "예) 컴포넌트 분리 기준을 두고 이견이 있었을 때, 양쪽 안의 장단점을 표로 정리해 공유하고 작은 PR로 먼저 검증한 뒤 팀 합의를 이끌었습니다. 결과적으로 리뷰 시간이 줄었다는 식의 정량 효과를 덧붙이면 좋습니다.",
};

const DEFAULT_MODEL_ANSWER =
  "STAR 구조(상황·과제·행동·결과)로 답하고, 특히 결과는 수치로 제시하세요. " +
  "예) '빌드 지연으로 배포가 늦어지던 상황에서(S) 빌드 최적화를 맡아(T) 캐시 전략을 도입해(A) 빌드 시간을 40% 단축했습니다(R).'";

function modelAnswer(questionId: number): { modelAnswer: string } {
  return { modelAnswer: MODEL_ANSWERS[questionId] ?? DEFAULT_MODEL_ANSWER };
}

// ───── 세션 복기 (GET /interview/sessions/:id/review) → api<SessionReview> ─────

const reviews: Record<number, SessionReview> = {
  8002: {
    sessionId: 8002,
    mode: "JOB",
    items: [
      {
        questionId: 90021,
        question: "React에서 상태 관리를 어떻게 설계하나요? Recoil과 Zustand를 비교해 설명해주세요.",
        questionType: "TECH",
        modelAnswer: MODEL_ANSWERS[90021],
        answerText:
          "서버 상태는 React Query, 전역 UI 상태는 Zustand로 나눠 관리했습니다. Recoil은 atom 단위 구독이 강점이지만 보일러플레이트가 많아 규모가 작은 프로젝트에서는 Zustand가 더 단순했습니다.",
        score: 84,
        feedback: "도구 선택 기준이 명확하고 비교가 구체적입니다. 실제 적용 사례의 정량 효과를 덧붙이면 더 좋습니다.",
        improvedAnswer:
          "서버/전역/지역 상태를 먼저 분리하고, 전역 상태에만 Zustand를 적용해 리렌더링을 셀렉터로 최적화했습니다. 그 결과 목록 화면 리렌더 횟수를 40% 줄였습니다.",
      },
      {
        questionId: 90022,
        question: "REST API 연동 중 발생한 문제와 해결 과정을 말해주세요.",
        questionType: "TECH",
        modelAnswer: MODEL_ANSWERS[90022],
        answerText:
          "목록 API가 중복 호출되어 응답이 느려지는 문제가 있었습니다. React Query 캐싱과 중복 제거를 적용해 해결했습니다.",
        score: 78,
        feedback: "문제-해결 흐름은 좋으나 결과를 수치로 제시하면 설득력이 올라갑니다.",
        improvedAnswer:
          "React Query로 캐싱·중복 제거를 적용하고 페이지네이션 키를 정규화해 중복 호출을 70% 줄이고 체감 로딩을 35% 단축했습니다.",
      },
      {
        questionId: 90023,
        question: "팀 프로젝트에서 협업 갈등을 어떻게 풀었나요?",
        questionType: "PERSONALITY",
        modelAnswer: MODEL_ANSWERS[90023],
        answerText: null,
        score: null,
        feedback: null,
        improvedAnswer: null,
      },
    ],
  },
  8001: {
    sessionId: 8001,
    mode: "BASIC",
    items: [
      {
        questionId: 90011,
        question: "자기소개를 1분 내로 해주세요.",
        questionType: "EXPECTED",
        modelAnswer: DEFAULT_MODEL_ANSWER,
        answerText:
          "React 기반 웹 프론트엔드를 개발해 온 신입 지원자입니다. 게시판·대시보드 프로젝트에서 API 연동과 상태 관리를 담당했습니다.",
        score: 74,
        feedback: "강점은 잘 드러났습니다. 지원 직무와 연결되는 한 문장을 마지막에 덧붙이면 좋습니다.",
        improvedAnswer:
          "React로 사용자 화면을 만들어 온 지원자입니다. 특히 목록 로딩을 35% 단축한 경험이 있어, 대규모 트래픽 서비스의 성능 개선에 기여하고 싶습니다.",
      },
      {
        questionId: 90012,
        question: "이 회사에 지원한 동기는 무엇인가요?",
        questionType: "EXPECTED",
        modelAnswer: DEFAULT_MODEL_ANSWER,
        answerText: null,
        score: null,
        feedback: null,
        improvedAnswer: null,
      },
    ],
  },
};

function sessionReview(sessionId: number): SessionReview {
  return reviews[sessionId] ?? { sessionId, mode: "JOB", items: [] };
}

// ───── 외부 키 보유 여부 (GET /interview/media/capabilities) → api<MediaCapabilities> ─────

const demoCapabilities: MediaCapabilities = {
  nonverbal: true,
  avatar: true,
  avatarSandbox: true,
};

// ───── 자체 추론 서버 음성 점수 (POST /interview/sessions/:id/voice-score) → api<VoiceScoreServerResult> ─────

function voiceScore(): VoiceScoreServerResult {
  return {
    score: 78,
    detail: {
      pace: 76,
      fluency: 71,
      stability: 82,
      confidence: 80,
      responsiveness: 74,
      overall: 78,
    },
    metrics: {
      totalSec: 62.4,
      speakingSec: 48.1,
      speechRateSpm: 312,
      fillerCount: 4,
      fillerPerMin: 3.9,
      avgResponseLatencySec: 1.8,
    },
    source: "rule",
  };
}

// ───── 자체 STT 전사 (POST /interview/sessions/:id/voice-transcribe) → api<TranscribeResult> ─────

function transcribe(): TranscribeResult {
  return {
    text:
      "네, 저는 목록 API가 중복 호출되어 응답이 느려지던 문제를 React Query 캐싱으로 해결했고 체감 로딩을 35% 단축했습니다.",
    language: "ko",
    duration: 9.6,
  };
}

// ───── 아바타 화상 면접 토큰 (POST /interview/sessions/:id/avatar-token) → api<AvatarSession> ─────

function avatarSession(sessionId: number): AvatarSession {
  return {
    sessionId: `demo-avatar-${sessionId}`,
    sessionToken: "demo-avatar-token",
    sandbox: true,
    language: "ko",
    questions: [
      "React에서 상태 관리를 어떻게 설계하나요? Recoil과 Zustand를 비교해 설명해주세요.",
      "REST API 연동 중 발생한 문제와 해결 과정을 말해주세요.",
      "팀 프로젝트에서 협업 갈등을 어떻게 풀었나요?",
    ],
  };
}

// ───── 저장된 음성/영상 분석 결과 (GET·POST /interview/sessions/:id/media-results) → api<MediaAnalysis[]> / api<MediaAnalysis> ─────

const mediaResults: Record<number, MediaAnalysis[]> = {
  8002: [
    {
      id: 71001,
      interviewSessionId: 8002,
      kind: "VOICE",
      transcript: [
        { role: "ai", text: "React에서 상태 관리를 어떻게 설계하나요?" },
        { role: "user", text: "서버 상태는 React Query, 전역 상태는 Zustand로 나눠 관리했습니다." },
        { role: "ai", text: "그 방식이 성능 문제를 만든 적은 없었나요?" },
        { role: "user", text: "셀렉터로 구독을 좁혀서 불필요한 리렌더링을 줄였습니다." },
      ],
      metrics: {
        totalSec: 62.4,
        speakingSec: 48.1,
        speechRateSpm: 312,
        fillerCount: 4,
        fillerPerMin: 3.9,
        avgResponseLatencySec: 1.8,
      },
      score: 78,
      scoreDetail: { pace: 76, fluency: 71, stability: 82, confidence: 80, responsiveness: 74, overall: 78 },
      createdAt: iso(1),
    },
    {
      id: 71002,
      interviewSessionId: 8002,
      kind: "AVATAR",
      transcript: [
        { role: "ai", text: "REST API 연동 중 발생한 문제와 해결 과정을 말해주세요." },
        { role: "user", text: "중복 호출 문제를 캐싱으로 해결해 로딩을 35% 단축했습니다." },
      ],
      metrics: { totalSec: 41.2, speakingSec: 30.5, eyeContactRatio: 0.72, smileRatio: 0.34 },
      score: 81,
      scoreDetail: { posture: 80, eyeContact: 79, expression: 76, confidence: 84, overall: 81 },
      createdAt: iso(1),
    },
  ],
};

function listMedia(sessionId: number): MediaAnalysis[] {
  return mediaResults[sessionId] ?? [];
}

let nextMediaId = 72000;

function saveMedia(sessionId: number, req: SaveMediaAnalysisRequest): MediaAnalysis {
  const saved: MediaAnalysis = {
    id: ++nextMediaId,
    interviewSessionId: sessionId,
    kind: req.kind,
    transcript: req.transcript,
    metrics: req.metrics,
    score: req.score,
    scoreDetail: req.scoreDetail,
    createdAt: new Date().toISOString(),
  };
  const list = mediaResults[sessionId] ?? (mediaResults[sessionId] = []);
  list.unshift(saved);
  return saved;
}

// ───── 음성 트랜스크립트 채점 (POST /interview/sessions/:id/score-voice) → api<number> (채점한 문항 수) ─────

function scoreVoiceTranscript(body: unknown): number {
  const transcript = (body as { transcript?: { role?: string }[] } | null)?.transcript ?? [];
  return transcript.filter((line) => line.role === "user").length;
}

export const interviewExtraRoutes: MockRoute[] = [
  // 세션 삭제(soft delete) · 복원(복습 시각 기록) — api<void>
  { method: "DELETE", pattern: /^\/interview\/sessions\/(\d+)$/, handler: ok(null) },
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/resume$/, handler: ok(null) },

  // 음성 모의면접 트랜스크립트 채점 — api<number>
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/score-voice$/, handler: ({ body }) => scoreVoiceTranscript(body) },

  // 지난 세션 복기 — api<SessionReview>
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/review$/, handler: ({ params }) => sessionReview(Number(params[0])) },

  // 모범답안 생성 — api<{ modelAnswer: string }>
  { method: "POST", pattern: /^\/interview\/questions\/(\d+)\/model-answer$/, handler: ({ params }) => modelAnswer(Number(params[0])) },

  // 외부 키 보유 여부 — api<MediaCapabilities>
  { method: "GET", pattern: /^\/interview\/media\/capabilities$/, handler: ok(demoCapabilities) },

  // 자체 추론 서버 음성 점수 — api<VoiceScoreServerResult>
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/voice-score$/, handler: () => voiceScore() },

  // 자체 STT 전사 — api<TranscribeResult>
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/voice-transcribe$/, handler: () => transcribe() },

  // 아바타 화상 면접 토큰 — api<AvatarSession>
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/avatar-token$/, handler: ({ params }) => avatarSession(Number(params[0])) },

  // 음성/영상 분석 결과 목록·저장 — api<MediaAnalysis[]> / api<MediaAnalysis>
  { method: "GET", pattern: /^\/interview\/sessions\/(\d+)\/media-results$/, handler: ({ params }) => listMedia(Number(params[0])) },
  { method: "POST", pattern: /^\/interview\/sessions\/(\d+)\/media-results$/, handler: ({ params, body }) => saveMedia(Number(params[0]), body as SaveMediaAnalysisRequest) },
];
