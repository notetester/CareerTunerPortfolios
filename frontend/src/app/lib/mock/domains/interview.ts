// 데모/목: D 도메인(가상 면접). /interview/** 흐름을 백엔드 없이 채운다.
import type {
  InterviewSession, InterviewQuestion, InterviewAnswer, InterviewReport,
  InterviewProgress, InterviewAgentStep, RealtimeSession, FileAsset,
  CreateInterviewSessionRequest, SubmitAnswerRequest,
} from "@/features/interview/types/interview";

const now = Date.now();
const iso = (daysAgo: number) => new Date(now - daysAgo * 86_400_000).toISOString();

export const demoInterviewSessions: InterviewSession[] = [
  { id: 8002, applicationCaseId: 102, mode: "JOB", startedAt: iso(1), endedAt: iso(1), totalScore: 80, avgScore: 78, avgVoiceScore: 72, lastResumedAt: null, createdAt: iso(1) },
  { id: 8001, applicationCaseId: 101, mode: "BASIC", startedAt: iso(3), endedAt: iso(3), totalScore: 74, avgScore: 71, avgVoiceScore: null, lastResumedAt: iso(0), createdAt: iso(3) },
];

const sessionQuestions: Record<number, InterviewQuestion[]> = {
  8002: [
    { id: 90021, interviewSessionId: 8002, parentQuestionId: null, question: "React에서 상태 관리를 어떻게 설계하나요? Recoil과 Zustand를 비교해 설명해주세요.", questionType: "TECH", sortOrder: 1 },
    { id: 90022, interviewSessionId: 8002, parentQuestionId: null, question: "REST API 연동 중 발생한 문제와 해결 과정을 말해주세요.", questionType: "TECH", sortOrder: 2 },
    { id: 90023, interviewSessionId: 8002, parentQuestionId: null, question: "팀 프로젝트에서 협업 갈등을 어떻게 풀었나요?", questionType: "PERSONALITY", sortOrder: 3 },
  ],
  8001: [
    { id: 90011, interviewSessionId: 8001, parentQuestionId: null, question: "자기소개를 1분 내로 해주세요.", questionType: "EXPECTED", sortOrder: 1 },
    { id: 90012, interviewSessionId: 8001, parentQuestionId: null, question: "이 회사에 지원한 동기는 무엇인가요?", questionType: "EXPECTED", sortOrder: 2 },
  ],
};

export function findSessionQuestions(sessionId: number): InterviewQuestion[] {
  return sessionQuestions[sessionId] ?? [];
}

const reports: Record<number, InterviewReport> = {
  8002: {
    totalScore: 80, previousScore: 74, questionCount: 3, durationLabel: "18분",
    categories: [
      { label: "구체성", score: 72 }, { label: "직무 적합성", score: 85 },
      { label: "논리성", score: 80 }, { label: "표현력", score: 83 },
    ],
    summaryFeedback: [
      "직무 역량 관련 답변이 구체적이고 사례 중심이었습니다.",
      "정량 성과(로딩 35% 단축 등)를 더 활용하면 설득력이 높아집니다.",
      "협업 질문에서 본인의 역할을 더 명확히 드러내세요.",
    ],
  },
  8001: {
    totalScore: 74, previousScore: null, questionCount: 2, durationLabel: "9분",
    categories: [{ label: "구체성", score: 68 }, { label: "표현력", score: 80 }],
    summaryFeedback: ["기본 질문에 무난히 답변했습니다.", "지원동기에 회사 분석 내용을 연결하면 좋습니다."],
  },
};

export function findReport(sessionId: number): InterviewReport {
  return reports[sessionId] ?? reports[8002];
}

export function progress(sessionId: number): InterviewProgress {
  const questions = findSessionQuestions(sessionId);
  return {
    sessionId,
    totalQuestions: questions.length,
    answeredQuestions: questions.length,
    finished: true,
    currentQuestion: null,
  };
}

export function agentSteps(sessionId: number): InterviewAgentStep[] {
  return [
    { id: 1, questionId: 90021, stepNo: 1, agent: "EVALUATOR", action: "답변 평가", status: "DONE", summary: "구체성·직무적합성 채점", detail: "사용한 기술은 명확하나 정량 성과가 부족", elapsedMs: 1240, createdAt: iso(1) },
    { id: 2, questionId: 90021, stepNo: 2, agent: "CRITIC", action: "평가 검증", status: "DONE", summary: "점수 근거 일관성 확인", detail: "개선 답변 제안이 질문과 정합", elapsedMs: 980, createdAt: iso(1) },
  ];
}

let nextSessionId = 8100;
let nextAnswerId = 95000;
let nextQuestionId = 96000;

export function createSession(req: CreateInterviewSessionRequest): InterviewSession {
  const session: InterviewSession = {
    id: ++nextSessionId, applicationCaseId: req.applicationCaseId, mode: req.mode,
    startedAt: new Date().toISOString(), endedAt: null, totalScore: null, avgScore: null, avgVoiceScore: null, lastResumedAt: null, createdAt: new Date().toISOString(),
  };
  demoInterviewSessions.unshift(session);
  sessionQuestions[session.id] = [];
  return session;
}

export function generateQuestions(sessionId: number): InterviewQuestion[] {
  const generated: InterviewQuestion[] = [
    { id: ++nextQuestionId, interviewSessionId: sessionId, parentQuestionId: null, question: "지원 직무에서 가장 자신 있는 기술과 그 근거를 말해주세요.", questionType: "TECH", sortOrder: 1 },
    { id: ++nextQuestionId, interviewSessionId: sessionId, parentQuestionId: null, question: "최근 해결한 어려운 기술 문제를 설명해주세요.", questionType: "SITUATION", sortOrder: 2 },
    { id: ++nextQuestionId, interviewSessionId: sessionId, parentQuestionId: null, question: "5년 후 커리어 목표는 무엇인가요?", questionType: "PERSONALITY", sortOrder: 3 },
  ];
  sessionQuestions[sessionId] = generated;
  return generated;
}

export function submitAnswer(questionId: number, req: SubmitAnswerRequest): InterviewAnswer {
  return {
    id: ++nextAnswerId, questionId, answerText: req.answerText, audioUrl: req.audioUrl ?? null, videoUrl: req.videoUrl ?? null,
    score: 76,
    feedback: "핵심 기술은 잘 언급했으나 본인의 역할과 정량 성과를 더 구체적으로 설명하면 좋습니다.",
    improvedAnswer: "저는 React 게시판 프로젝트에서 인증 API와 CRUD를 연동하고, 불필요한 리렌더링을 줄여 목록 로딩을 35% 단축했습니다.",
    createdAt: new Date().toISOString(),
  };
}

export function followUps(questionId: number): InterviewQuestion[] {
  return [
    ...findSessionQuestions(8002),
    { id: ++nextQuestionId, interviewSessionId: 8002, parentQuestionId: questionId, question: "그 상태 관리 방식이 성능 문제를 만든 적은 없었나요?", questionType: "FOLLOW_UP", sortOrder: 99 },
  ];
}

export function realtimeSession(): RealtimeSession {
  // 데모: 실제 OpenAI Realtime 연결은 불가(키 없음). 형태만 반환해 화면이 진행 단계까지 도달하게 한다.
  return { clientSecret: "demo-realtime-secret", expiresAt: null, model: "mock-realtime", voice: "alloy", realtimeUrl: "" };
}

export function fileAsset(): FileAsset {
  return { id: 70001, kind: "AUDIO", refType: "INTERVIEW_ANSWER", refId: null, originalName: "demo-answer.webm", contentType: "audio/webm", sizeBytes: 12345, contentUrl: "", createdAt: new Date().toISOString() };
}
