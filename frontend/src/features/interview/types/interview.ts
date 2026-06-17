// 면접 도메인 타입 + UI 상수.
// 백엔드 interview_session / interview_question / interview_answer 스키마와 1:1로 맞춘다.

export type InterviewMode =
  | "BASIC" // 기본 면접
  | "JOB" // 직무 면접
  | "PERSONALITY" // 인성 면접
  | "PRESSURE" // 압박 면접
  | "RESUME" // 자소서 기반
  | "COMPANY"; // 기업 맞춤

export type QuestionType = "EXPECTED" | "TECH" | "PERSONALITY" | "SITUATION" | "FOLLOW_UP";

export type InterviewDifficulty = "하" | "중" | "상";

/** interview_session */
export interface InterviewSession {
  id: number;
  applicationCaseId: number;
  mode: InterviewMode;
  startedAt: string | null;
  endedAt: string | null;
  totalScore: number | null;
  /** 답변 점수 평균(목록 조회 계산값). 리포트 미생성으로 totalScore 가 없을 때 카드 점수 폴백용. */
  avgScore: number | null;
  createdAt: string;
}

/** interview_question */
export interface InterviewQuestion {
  id: number;
  interviewSessionId: number;
  parentQuestionId: number | null;
  question: string;
  questionType: QuestionType | null;
  sortOrder: number;
}

/** interview_answer */
export interface InterviewAnswer {
  id: number;
  questionId: number;
  answerText: string | null;
  audioUrl: string | null;
  videoUrl: string | null;
  score: number | null;
  feedback: string | null;
  improvedAnswer: string | null;
  createdAt: string;
}

/** interview_session.report (JSON 컬럼) 구조 */
export interface InterviewReport {
  totalScore: number;
  previousScore: number | null;
  questionCount: number;
  durationLabel: string | null;
  categories: { label: string; score: number }[];
  summaryFeedback: string[];
}

// ───── 요청 DTO ─────

export interface CreateInterviewSessionRequest {
  applicationCaseId: number;
  mode: InterviewMode;
}

export interface GenerateQuestionsRequest {
  mode: InterviewMode;
  count?: number;
}

export interface GenerateFollowUpsRequest {
  count?: number;
}

export interface SubmitAnswerRequest {
  answerText: string;
  audioUrl?: string | null;
  videoUrl?: string | null;
  /** 사용자에게 보여준 모범답안(답안지). 있으면 채점의 만점 기준으로 함께 보낸다. */
  modelAnswer?: string | null;
}

/** 자율 에이전트 진행 단계 (AI 사고과정 트레이스) */
export interface InterviewAgentStep {
  id: number;
  questionId: number | null;
  stepNo: number;
  agent: string; // RETRIEVER / EVALUATOR / CRITIC / PROBER / PLANNER / ...
  action: string | null;
  status: string | null; // DONE / FAILED
  summary: string | null;
  detail: string | null; // JSON 문자열
  elapsedMs: number | null;
  createdAt: string;
}

/** 면접 진행 상태 (AI 면접관 대화 진행) */
export interface InterviewProgress {
  sessionId: number;
  totalQuestions: number;
  answeredQuestions: number;
  finished: boolean;
  currentQuestion: InterviewQuestion | null;
}

/** 실시간 음성 면접관(WebRTC) 세션 — 프런트가 OpenAI Realtime 에 직접 연결하는 단기 정보 */
export interface RealtimeSession {
  clientSecret: string;
  expiresAt: number | null;
  model: string;
  voice: string;
  realtimeUrl: string;
}

// ───── 음성/아바타 면접 분석 (interview_media_analysis) ─────

/** 대화 트랜스크립트 한 줄 */
export interface TranscriptLine {
  role: "ai" | "user";
  text: string;
}

/** Inworld voice profiling 라벨 (confidence 내림차순) */
export interface VoiceProfileLabel {
  label: string;
  confidence: number;
}

/** Inworld voice profiling 결과 — 신뢰도 낮으면 필드가 빠질 수 있음 */
export interface VoiceProfile {
  age?: VoiceProfileLabel[];
  emotion?: VoiceProfileLabel[];
  pitch?: VoiceProfileLabel[];
  vocalStyle?: VoiceProfileLabel[];
  accent?: VoiceProfileLabel[];
}

/** 서버 음성 감정 분석 응답 (POST /sessions/{id}/voice-analysis) */
export interface VoiceAnalysisResult {
  transcript: string;
  voiceProfile: VoiceProfile | null;
}

/** 브라우저 온디바이스 음성 지표 */
export interface VoiceMetrics {
  totalSec: number;
  speakingSec: number;
  speechRateSpm: number | null; // 분당 음절(글자) 수
  fillerCount: number;
  fillerPerMin: number | null;
  avgPitchHz: number | null;
  pitchStdevHz: number | null;
  avgResponseLatencySec: number | null; // 질문 후 첫 발화까지
  maxResponseLatencySec: number | null;
  avgVolume: number | null; // RMS 0~1
}

/** 항목별 음성 점수 (0~100) */
export interface VoiceScoreDetail {
  pace: number; // 말 속도
  fluency: number; // 필러 최소화
  stability: number; // 톤 안정감
  confidence: number; // 자신감(성량·감정)
  responsiveness: number; // 답변 반응 속도
  overall: number;
}

/** 외부 키 보유 여부 (GET /media/capabilities) */
export interface MediaCapabilities {
  voiceProfiling: boolean;
  avatar: boolean;
  avatarSandbox: boolean;
}

/** 저장된 분석 결과 (interview_media_analysis) */
export interface MediaAnalysis {
  id: number;
  interviewSessionId: number;
  kind: "VOICE" | "AVATAR";
  transcript: TranscriptLine[] | null;
  metrics: Record<string, unknown> | null;
  score: number;
  scoreDetail: Record<string, number> | null;
  createdAt: string;
}

/** 분석 결과 저장 요청 (POST /sessions/{id}/media-results) */
export interface SaveMediaAnalysisRequest {
  kind: "VOICE" | "AVATAR";
  transcript: TranscriptLine[] | null;
  metrics: Record<string, unknown> | null;
  score: number;
  scoreDetail: Record<string, number> | null;
}

/** 아바타 화상 면접 세션 (POST /sessions/{id}/avatar-token) */
export interface AvatarSession {
  sessionId: string | null;
  sessionToken: string;
  sandbox: boolean;
  language: string;
  questions: string[];
}

/** 파일 업로드 결과 (file_asset) */
export interface FileAsset {
  id: number;
  kind: "AUDIO" | "VIDEO" | "RESUME" | "PORTFOLIO" | "POSTING" | "ATTACHMENT";
  refType: string | null;
  refId: number | null;
  originalName: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  contentUrl: string;
  createdAt: string;
}

/** 지난 세션 복기(리뷰) 한 문항 — 질문 + 모범답안 + 내 최신 답변/점수. */
export interface SessionReviewItem {
  questionId: number;
  question: string;
  questionType: string;
  modelAnswer: string | null;
  answerText: string | null;
  score: number | null;
  feedback: string | null;
  improvedAnswer: string | null;
}

/** 최근 면접 기록에서 들어가 보는 세션 복기 응답. */
export interface SessionReview {
  sessionId: number;
  mode: string;
  items: SessionReviewItem[];
}

/** 최근 면접 기록 페이지 응답 (더보기 누적 페이징). 백엔드 SessionPageResponse 와 동형. */
export interface SessionPageResponse {
  sessions: InterviewSession[];
  total: number;
  page: number;
  size: number;
  hasNext: boolean;
}

// ───── UI 상수 (백엔드 데이터 아님, 화면 구성용) ─────

export interface InterviewModeOption {
  id: InterviewMode;
  icon: string;
  title: string;
  desc: string;
  difficulty: InterviewDifficulty;
  recommended: boolean;
}

export const INTERVIEW_MODES: InterviewModeOption[] = [
  { id: "BASIC", icon: "💬", title: "기본 면접", desc: "자기소개, 지원동기, 장단점", difficulty: "하", recommended: false },
  { id: "JOB", icon: "⚙️", title: "직무 면접", desc: "공고 기반 기술/직무 질문", difficulty: "상", recommended: true },
  { id: "PERSONALITY", icon: "🤝", title: "인성 면접", desc: "협업, 갈등, 책임감, 태도", difficulty: "중", recommended: false },
  { id: "PRESSURE", icon: "⚡", title: "압박 면접", desc: "꼬리 질문, 반박 질문", difficulty: "상", recommended: false },
  { id: "RESUME", icon: "📄", title: "자소서 기반", desc: "자기소개서 문장을 기반으로 질문", difficulty: "중", recommended: false },
  { id: "COMPANY", icon: "🏢", title: "기업 맞춤", desc: "기업 현황과 공고 기반 질문", difficulty: "상", recommended: false },
];

export function getInterviewModeLabel(mode: InterviewMode): string {
  return INTERVIEW_MODES.find((m) => m.id === mode)?.title ?? mode;
}

/** 문장 끝(.!?。) 뒤에 줄바꿈을 넣어 모범답안·개선답변을 문장 단위로 읽기 쉽게 만든다. (whitespace-pre-line 과 함께 사용) */
export function toSentenceLines(text: string): string {
  return text.replace(/([.!?。])\s+/g, "$1\n").trim();
}

/** 답변 평가 항목 (정적 안내) */
export const EVALUATION_CRITERIA: { label: string; desc: string }[] = [
  { label: "답변 내용", desc: "질문에 제대로 답했는가 · 핵심 포인트를 짚었는가" },
  { label: "직무 적합성", desc: "공고 요구 역량과 연결되는가 · 직무 관련 경험이 있는가" },
  { label: "구체성", desc: "경험, 수치, 사례가 있는가 · 추상적 말보다 구체적 사례 제시" },
  { label: "논리성", desc: "답변 구조가 자연스러운가 · STAR 기법 등 논리적 흐름" },
  { label: "표현력", desc: "말이 명확한가 · 전문 용어 적절 사용 · 필러 워드 최소화" },
  { label: "자신감", desc: "답변이 지나치게 위축되어 있지 않은가" },
  { label: "태도", desc: "면접 상황에 적절한 태도인가 · 경청, 예의" },
  { label: "시간 관리", desc: "답변 시간이 너무 짧거나 길지 않은가 (1-3분 권장)" },
];

/** 채점 구간 (정적 안내) */
export const SCORE_BANDS: { range: string; desc: string; color: string }[] = [
  { range: "90-100점", desc: "완성도 높은 답변 · 구체적 사례 + 수치 + 직무 연결 완벽", color: "bg-green-50 border-green-200 text-green-800" },
  { range: "75-89점", desc: "방향은 맞고 주요 포인트 포함 · 구체성 보완 여지 있음", color: "bg-blue-50 border-blue-200 text-blue-800" },
  { range: "60-74점", desc: "기본 방향은 맞으나 내용이 부족하거나 논리 연결 미흡", color: "bg-amber-50 border-amber-200 text-amber-800" },
  { range: "40-59점", desc: "부분적으로만 답변 · 직무 연결성 부족", color: "bg-orange-50 border-orange-200 text-orange-800" },
  { range: "0-39점", desc: "방향이 틀리거나 답변이 지나치게 짧고 구체성 전무", color: "bg-red-50 border-red-200 text-red-800" },
];

export function getScoreColor(score: number): string {
  if (score >= 75) return "text-green-600";
  if (score >= 60) return "text-amber-600";
  return "text-red-500";
}
