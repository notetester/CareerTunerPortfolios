// 통합 챗봇(①FAQ + ③오케스트레이터 인테이크) mock — useChatbot 의 ChatbotApiResponse 계약을 따른다.
// 세션(conversationId) 단위로 메시지를 in-memory 보관해 목록/복원까지 데모가 돌게 한다.
// 인테이크는 결정적 상태머신: 행위 의도 감지 → CASE 칩 → MODE 칩 → ready(autoPrepRequest).
// (실 SSE 실행 /auto-prep/run/stream 은 raw fetch 라 mock 인터셉트 밖 — mock 데모에선 실행 에러 배너가 정상.)
import { demoApplicationCases } from "../data";
import type { MockRoute } from "../registry";

interface StoredMsg {
  role: "user" | "bot";
  text: string;
}

interface Conversation {
  id: number;
  title: string | null;
  mode: string | null;
  updatedAt: number;
  messages: StoredMsg[];
  // 인테이크 슬롯(mock): 지원 건 → 모드 순으로 채워진다.
  caseId: number | null;
  inOrchestration: boolean;
}

const conversations = new Map<number, Conversation>();
let nextConversationId = 9100;

const MODE_OPTIONS = [
  { code: "BASIC", label: "기본 면접" },
  { code: "JOB", label: "직무 면접" },
  { code: "PERSONALITY", label: "인성 면접" },
  { code: "PRESSURE", label: "압박 면접" },
  { code: "RESUME", label: "자소서 기반" },
  { code: "PORTFOLIO", label: "포트폴리오 기반" },
  { code: "REAL", label: "실전 종합" },
  { code: "COMPANY", label: "기업 맞춤" },
];

const CASE_CANDIDATES = demoApplicationCases
  .slice(0, 4)
  .map((c) => ({ id: c.id, companyName: c.companyName, jobTitle: c.jobTitle, status: c.status }));

/** 행위 의도(오케스트레이터 진입) 감지 — 백엔드 UnifiedChatRouter 의 intake 시드를 키워드로 근사. */
function isIntakeIntent(q: string): boolean {
  return /(준비|면접|자소서|이력서|첨삭|예상\s*질문)/.test(q) && /(해줘|해 줘|하고 싶|도와|시작|봐줘|봐 줘|위주로|까지)/.test(q)
    || /(준비해줘|면접 준비|모의면접 연습)/.test(q);
}

/** FAQ 데모 즉답(구 profile.ts answerChatbot 이관 — 현 ChatbotApiResponse 형태로 갱신). */
function faqAnswer(q: string): { message: string; links: { url: string; label: string }[] } | null {
  const has = (...keys: string[]) => keys.some((k) => q.includes(k));
  if (has("환불", "결제 취소", "돈 돌려")) {
    return {
      message:
        "결제 후 7일 이내, 크레딧을 사용하지 않은 경우 전액 환불이 가능합니다. " +
        "마이페이지 > 결제/구독에서 환불 신청을 하시거나 고객센터로 문의해 주세요.",
      links: [
        { url: "/billing", label: "결제/구독 관리" },
        { url: "/support/faq", label: "환불 정책 FAQ" },
      ],
    };
  }
  if (has("탈퇴", "계정 삭제")) {
    return {
      message:
        "회원 탈퇴는 설정 > 계정 설정에서 진행할 수 있습니다. 탈퇴 시 프로필·분석 기록이 모두 삭제되며 복구되지 않습니다.",
      links: [{ url: "/settings?tab=account", label: "계정 설정" }],
    };
  }
  if (has("무료", "공짜", "어디까지")) {
    return {
      message:
        "무료 플랜에서도 지원 건 등록, 기본 공고 분석, 일부 적합도 분석을 체험할 수 있습니다. " +
        "심화 분석과 모의면접 횟수는 PRO 플랜에서 크레딧으로 이용할 수 있습니다.",
      links: [{ url: "/pricing", label: "요금제 보기" }],
    };
  }
  return null;
}

function touch(c: Conversation): void {
  c.updatedAt = Date.now();
}

function getOrCreate(conversationId: unknown): Conversation {
  const id = Number(conversationId);
  const found = Number.isFinite(id) ? conversations.get(id) : undefined;
  if (found) return found;
  const fresh: Conversation = {
    id: nextConversationId++,
    title: null,
    mode: null,
    updatedAt: Date.now(),
    messages: [],
    caseId: null,
    inOrchestration: false,
  };
  conversations.set(fresh.id, fresh);
  return fresh;
}

interface AskBody {
  question?: string;
  conversationId?: number | null;
  selectedCaseId?: number;
  selectedModeCode?: string;
}

function ask(body: unknown) {
  const req = (body ?? {}) as AskBody;
  const question = String(req.question ?? "").trim();
  const c = getOrCreate(req.conversationId);
  if (c.title == null && question) c.title = question.slice(0, 40);
  c.messages.push({ role: "user", text: question });
  touch(c);

  const reply = (payload: {
    message: string;
    links?: { url: string; label: string }[];
    quickReplies?: string[];
    intake?: unknown;
    inOrchestration?: boolean;
  }) => {
    c.messages.push({ role: "bot", text: payload.message });
    touch(c);
    return {
      conversationId: c.id,
      message: payload.message,
      links: payload.links ?? [],
      quickReplies: payload.quickReplies ?? [],
      intake: payload.intake ?? null,
      inOrchestration: payload.inOrchestration ?? false,
      summaryChip: null,
    };
  };

  // ── ③ 칩 선택(결정적 바인딩) ──
  if (req.selectedCaseId != null) {
    c.caseId = req.selectedCaseId;
    c.inOrchestration = true;
    return reply({
      message: "좋아요, 그 지원 건으로 진행할게요. 어떤 면접 모드로 준비할까요?",
      intake: { ready: false, nextAsk: "MODE", autoPrepRequest: null, candidates: [], modes: MODE_OPTIONS },
      inOrchestration: true,
    });
  }
  if (req.selectedModeCode) {
    c.mode = req.selectedModeCode;
    c.inOrchestration = true;
    const label = MODE_OPTIONS.find((m) => m.code === req.selectedModeCode)?.label ?? req.selectedModeCode;
    return reply({
      message: `${label}로 준비를 시작할게요. 공고 분석부터 예상 질문까지 순서대로 진행합니다.`,
      intake: {
        ready: true,
        nextAsk: null,
        autoPrepRequest: { applicationCaseId: c.caseId, mode: req.selectedModeCode },
        candidates: [],
        modes: [],
      },
      inOrchestration: true,
    });
  }

  // ── 오케스트레이터 진행 중(모드 유지) — 자유 텍스트로 케이스/모드 되묻기 반복 ──
  if (c.inOrchestration && c.caseId == null) {
    return reply({
      message: "어느 지원 건으로 준비할까요? 아래에서 골라주세요.",
      intake: { ready: false, nextAsk: "CASE", autoPrepRequest: null, candidates: CASE_CANDIDATES, modes: [] },
      inOrchestration: true,
    });
  }
  if (c.inOrchestration && c.mode == null) {
    return reply({
      message: "면접 모드를 골라주시면 바로 시작할게요.",
      intake: { ready: false, nextAsk: "MODE", autoPrepRequest: null, candidates: [], modes: MODE_OPTIONS },
      inOrchestration: true,
    });
  }

  // ── ③ 행위 의도 첫 진입 ──
  if (isIntakeIntent(question)) {
    c.inOrchestration = true;
    return reply({
      message: "네, 준비를 도와드릴게요. 어느 지원 건으로 진행할까요?",
      intake: { ready: false, nextAsk: "CASE", autoPrepRequest: null, candidates: CASE_CANDIDATES, modes: [] },
      inOrchestration: true,
    });
  }

  // ── ① FAQ 데모 즉답 ──
  const faq = faqAnswer(question);
  if (faq) return reply({ message: faq.message, links: faq.links, quickReplies: ["모의면접은 어떻게 진행되나요?"] });

  // ── catch-all(커뮤니티 에이전트 근사) ──
  return reply({
    message:
      "재밌는 질문이네요! 저는 취업 준비를 돕는 튜너봇이에요. " +
      "\"카카오 프론트엔드 면접 준비해줘\"처럼 말씀해 주시면 공고 분석부터 모의면접까지 한 번에 준비해 드려요.",
    quickReplies: ["카카오 프론트엔드 면접 준비해줘", "무료로 어디까지 쓸 수 있나요?"],
  });
}

function summaries() {
  return [...conversations.values()]
    .filter((c) => c.messages.length > 0)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, 5)
    .map((c) => ({ conversationId: c.id, title: c.title, mode: c.mode, updatedAt: c.updatedAt }));
}

function history(c: Conversation) {
  return { conversationId: c.id, messages: c.messages.map((m) => ({ role: m.role, text: m.text })), resume: null };
}

export const chatbotRoutes: MockRoute[] = [
  { method: "POST", pattern: /^\/chatbot\/ask$/, handler: ({ body }) => ask(body) },
  {
    method: "GET",
    pattern: /^\/chatbot\/conversations$/,
    handler: () => summaries(),
  },
  {
    method: "GET",
    pattern: /^\/chatbot\/conversations\/recent$/,
    handler: () => {
      const latest = summaries()[0];
      return latest ? history(conversations.get(latest.conversationId)!) : null;
    },
  },
  {
    method: "GET",
    pattern: /^\/chatbot\/conversations\/(\d+)\/messages$/,
    handler: ({ params }) => {
      const c = conversations.get(Number(params[0]));
      return c ? history(c) : { conversationId: Number(params[0]), messages: [], resume: null };
    },
  },
];
