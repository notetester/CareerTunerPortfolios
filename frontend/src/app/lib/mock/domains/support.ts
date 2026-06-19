// 데모/목: SUPPORT 도메인 — FAQ·공지·문의 티켓·스레드.
// 백엔드 없이 고객센터(FAQ/공지/문의하기/내 문의 내역) 화면이 실데이터처럼 렌더되게 한다.
// 응답 shape 은 supportApi.ts 의 api<T>(...) 호출 T 와 정확히 일치시킨다(클라 transform 이전 백엔드 shape).
import type { MockRoute, MockContext } from "../registry";
import { ok, iso } from "../registry";
import type {
  Faq,
  Notice,
  SupportTicket,
  TicketThread,
  TicketStatus,
} from "@/features/support/types/support";

// getNotices() 는 목록 응답을 받아 content:"" 로 덮어쓰므로, 목록 핸들러는 content 가 없어도 되지만
// Notice 타입은 content: string 을 요구하므로 목록 항목에도 빈 content 를 채워 타입을 만족시킨다.
// getMyTickets() 는 Array<Omit<SupportTicket, "content">> 를 기대하므로 목록은 content 없는 shape 로 반환한다.
type TicketSummary = Omit<SupportTicket, "content">;

const krDate = (daysAgo: number): string =>
  iso(daysAgo).slice(0, 10).replace(/-/g, ".");

// ── FAQ ──────────────────────────────────────────────────────────────────────
// UI(FAQ_CATEGORIES) 가 보내는 카테고리 값과 정렬: general/account/payment/ai_feature/interview
const ALL_FAQS: Faq[] = [
  { id: 1, category: "general", question: "CareerTuner는 어떤 서비스인가요?", answer: "채용공고에 맞춰 이력서와 면접 답변을 조정해주는 AI 취업 전략 플랫폼입니다. 지원 건별로 스펙·답변을 정리해 합격까지의 시간을 줄여드려요." },
  { id: 2, category: "general", question: "PC와 모바일 모두 사용할 수 있나요?", answer: "네. 웹·모바일웹·앱을 모두 지원하며, 작성 중인 지원 건은 계정에 자동 동기화됩니다." },
  { id: 3, category: "account", question: "회원가입은 어떻게 하나요?", answer: "이메일 또는 소셜 계정으로 약 1분 만에 가입할 수 있습니다. 가입 시 희망 직무를 선택하면 맞춤 추천이 시작됩니다." },
  { id: 4, category: "account", question: "비밀번호를 잊어버렸어요.", answer: "로그인 화면의 '비밀번호 찾기'에서 가입 이메일로 재설정 링크를 받을 수 있습니다. 소셜 계정으로 가입한 경우 해당 소셜 서비스에서 관리됩니다." },
  { id: 5, category: "payment", question: "무료로 어디까지 쓸 수 있나요?", answer: "공고 분석과 적합도 분석을 매월 일정 횟수까지 무료로 제공합니다. 프로 플랜에서는 분석·모의면접을 무제한으로 이용할 수 있어요." },
  { id: 6, category: "payment", question: "결제 후 환불이 가능한가요?", answer: "결제일로부터 7일 이내, 유료 기능 미사용 시 전액 환불됩니다. 부분 사용 시 잔여 크레딧을 기준으로 환불 금액이 계산됩니다." },
  { id: 7, category: "ai_feature", question: "공고 분석 리포트에는 무엇이 담기나요?", answer: "공고에서 추출한 필수·우대 역량과 직무 적합도 점수, 보완이 필요한 항목별 개선 제안이 포함됩니다." },
  { id: 8, category: "ai_feature", question: "AI가 작성한 답변을 그대로 제출해도 되나요?", answer: "초안 용도로 활용하시길 권장합니다. 본인 경험에 맞게 다듬으면 적합도와 진정성이 함께 올라갑니다." },
  { id: 9, category: "interview", question: "모의면접은 어떻게 진행되나요?", answer: "지원 건의 공고·기업 분석을 기반으로 예상 질문을 생성하고, 텍스트 또는 음성으로 답하면 구조·표현을 분석해 리포트로 제공합니다." },
  { id: 10, category: "interview", question: "면접 후기는 익명으로 올라가나요?", answer: "커뮤니티 면접 후기는 기본 익명으로 게시되며 직무 라벨만 함께 표시됩니다. 개인을 식별할 수 있는 정보는 노출되지 않습니다." },
];

function faqHandler(ctx: MockContext): Faq[] {
  const category = ctx.query.get("category");
  if (!category || category === "all") return ALL_FAQS;
  return ALL_FAQS.filter((f) => f.category === category);
}

// ── 공지 ─────────────────────────────────────────────────────────────────────
const NOTICES: Notice[] = [
  {
    id: 14, isPinned: true, tag: "점검", viewCount: 4821, createdAt: krDate(13),
    title: "[점검] 6월 16일(월) 02:00~04:00 서비스 정기 점검 안내",
    content: "안녕하세요, CareerTuner입니다.\n더 안정적인 서비스 제공을 위해 아래와 같이 정기 점검을 진행합니다.\n\n## 점검 일정\n\n- **일시**: 6월 16일(월) 02:00 ~ 04:00 (약 2시간)\n- **대상**: 웹/앱 전체 서비스\n\n## 점검 중 영향\n\n- 로그인, 공고 분석, 모의면접 등 전체 기능 일시 중단\n- 작성 중이던 내용은 임시저장되어 점검 후 그대로 복구됩니다\n\n점검 시간은 상황에 따라 단축되거나 연장될 수 있습니다.",
  },
  {
    id: 13, isPinned: true, tag: "정책", viewCount: 3120, createdAt: krDate(16),
    title: "[안내] 개인정보처리방침 개정 안내 (시행 6월 20일)",
    content: "개인정보처리방침이 아래와 같이 개정되어 안내드립니다. 변경된 내용은 **6월 20일**부터 적용됩니다.\n\n## 주요 변경 사항\n\n1. 모의면접 음성 데이터의 보관 기간 및 파기 절차 명시\n2. 제3자 제공 항목에 결제 대행사 추가\n3. 개인정보 보호책임자 연락처 변경\n\n자세한 내용은 이용약관 > 개인정보처리방침 전문에서 확인하실 수 있습니다.",
  },
  {
    id: 12, isPinned: false, tag: "업데이트", viewCount: 8265, createdAt: krDate(19),
    title: "모의면접에 '음성 답변 분석' 기능이 추가되었습니다",
    content: "이제 모의면접에서 **음성으로 답변**하고, 말하기 습관까지 분석받을 수 있습니다.\n\n## 무엇이 달라지나요?\n\n- 답변을 녹음하면 말 속도·군더더기 표현·답변 구조(두괄식 여부)를 분석\n- 텍스트 답변만으로도 기존처럼 이용 가능\n\n지원 건 상세 > 모의면접에서 바로 사용해보세요.",
  },
  {
    id: 11, isPinned: false, tag: "이벤트", viewCount: 6540, createdAt: krDate(22),
    title: "프로 플랜 첫 결제 30% 할인 (6월 한정)",
    content: "6월 한 달 동안 프로 플랜을 처음 결제하시는 분께 **30% 할인**을 드립니다.\n\n- **대상**: 프로 플랜 첫 결제 회원\n- **기간**: 이번 달 한정\n- **혜택**: 첫 결제 금액 30% 할인 (자동 적용)\n\n이 기회에 공고 분석과 모의면접을 무제한으로 이용해보세요.",
  },
  {
    id: 10, isPinned: false, tag: "업데이트", viewCount: 4210, createdAt: krDate(26),
    title: "공고 분석 리포트 UI 개선 및 직무 적합도 점수 추가",
    content: "공고 분석 리포트가 더 보기 쉽게 개선되었고, **직무 적합도 점수**가 새로 추가되었습니다.\n\n## 개선 내용\n\n- 필수/우대 역량을 카드 형태로 정리\n- 지원 직무 기준 적합도 점수(0~100) 표시\n- 항목별 개선 제안 하이라이트",
  },
];

function noticeDetailHandler(ctx: MockContext): Notice {
  const id = Number(ctx.params[0]);
  const found = NOTICES.find((n) => n.id === id);
  if (found) return { ...found, viewCount: found.viewCount + 1 };
  // 미존재 id 도 데모에서는 빈 화면 대신 그럴듯한 공지를 보여준다.
  return {
    id,
    isPinned: false,
    tag: "안내",
    viewCount: 128,
    createdAt: krDate(30),
    title: "CareerTuner 공지사항",
    content: "요청하신 공지 내용을 불러왔습니다. 자세한 사항은 고객센터로 문의해주세요.",
  };
}

// ── 문의 티켓 ────────────────────────────────────────────────────────────────
const MY_TICKETS: TicketSummary[] = [
  {
    id: 5001,
    subject: "카카오 지원 건 공고 분석이 멈춰 있어요",
    status: "ANSWERED",
    reply: "확인 결과 일시적인 분석 큐 지연이었으며 현재 정상 처리되었습니다. 다시 시도해주세요.",
    repliedAt: iso(1),
    createdAt: iso(2),
  },
  {
    id: 5002,
    subject: "프로 플랜 결제 영수증 발급 요청",
    status: "IN_PROGRESS",
    createdAt: iso(1),
  },
];

function myTicketsHandler(): TicketSummary[] {
  return MY_TICKETS;
}

function myTicketDetailHandler(ctx: MockContext): TicketSummary {
  const id = Number(ctx.params[0]);
  const found = MY_TICKETS.find((t) => t.id === id);
  if (found) return found;
  return {
    id,
    subject: "문의 내역",
    status: "RECEIVED",
    createdAt: iso(0),
  };
}

// 티켓 생성: 백엔드 SupportTicket(접수 직후, content 없음) shape 을 echo. id 는 새로 부여.
function createTicketHandler(ctx: MockContext): SupportTicket {
  const body = (ctx.body ?? {}) as { category?: string; subject?: string; content?: string };
  return {
    id: 5100 + Math.floor(Math.random() * 900),
    subject: body.subject ?? "새 문의",
    content: body.content ?? "",
    status: "RECEIVED",
    createdAt: iso(0),
  };
}

// ── 티켓 스레드(전체 대화) ───────────────────────────────────────────────────
const THREADS: Record<number, TicketThread> = {
  5001: {
    id: 5001,
    subject: "카카오 지원 건 공고 분석이 멈춰 있어요",
    category: "기술문제",
    status: "ANSWERED",
    createdAt: iso(2),
    messages: [
      { id: 1, senderType: "USER", content: "카카오 프론트엔드 지원 건에서 공고 분석을 눌렀는데 30분째 진행 중 상태에서 멈춰 있습니다. 어떻게 해야 하나요?", createdAt: iso(2) },
      { id: 2, senderType: "ADMIN", content: "안녕하세요, CareerTuner 고객센터입니다. 해당 시간대에 분석 큐가 일시적으로 지연되어 발생한 현상으로 확인됩니다. 현재는 정상화되었으니 해당 지원 건에서 분석을 다시 실행해주세요.", createdAt: iso(1) },
      { id: 3, senderType: "USER", content: "다시 실행하니 정상적으로 완료됐어요. 감사합니다!", createdAt: iso(1) },
    ],
  },
  5002: {
    id: 5002,
    subject: "프로 플랜 결제 영수증 발급 요청",
    category: "결제",
    status: "IN_PROGRESS",
    createdAt: iso(1),
    messages: [
      { id: 1, senderType: "USER", content: "이번 달 프로 플랜 결제 건의 현금영수증 발급을 요청드립니다. 사업자 지출 증빙용으로 부탁드려요.", createdAt: iso(1) },
      { id: 2, senderType: "ADMIN", content: "요청 확인했습니다. 영수증 발급을 위해 사업자등록번호를 회신해주시면 1영업일 이내에 처리해드리겠습니다.", createdAt: iso(0) },
    ],
  },
};

function ticketThreadHandler(ctx: MockContext): TicketThread {
  const id = Number(ctx.params[0]);
  const found = THREADS[id];
  if (found) return found;
  return {
    id,
    subject: "문의 내역",
    category: "기타",
    status: "RECEIVED",
    createdAt: iso(0),
    messages: [
      { id: 1, senderType: "USER", content: "문의 내용을 접수했습니다.", createdAt: iso(0) },
    ],
  };
}

// 추가 메시지: 사용자 메시지를 스레드 끝에 붙여 echo (가벼운 인메모리 변형).
function addTicketMessageHandler(ctx: MockContext): TicketThread {
  const id = Number(ctx.params[0]);
  const body = (ctx.body ?? {}) as { content?: string };
  const base: TicketThread = THREADS[id] ?? {
    id,
    subject: "문의 내역",
    category: "기타",
    status: "RECEIVED" as TicketStatus,
    createdAt: iso(0),
    messages: [],
  };
  const nextId = (base.messages.at(-1)?.id ?? 0) + 1;
  const updated: TicketThread = {
    ...base,
    status: "IN_PROGRESS",
    messages: [
      ...base.messages,
      { id: nextId, senderType: "USER", content: body.content ?? "추가 문의입니다.", createdAt: iso(0) },
    ],
  };
  THREADS[id] = updated;
  return updated;
}

export const supportRoutes: MockRoute[] = [
  // FAQ 목록 (공개) — category 쿼리로 필터
  { method: "GET", pattern: /^\/support\/faq$/, handler: faqHandler },
  // 공지 목록 (공개)
  { method: "GET", pattern: /^\/support\/notices$/, handler: ok<Notice[]>(NOTICES) },
  // 공지 상세 (공개)
  { method: "GET", pattern: /^\/support\/notices\/(\d+)$/, handler: noticeDetailHandler },
  // 내 문의 목록 / 문의 접수
  { method: "GET", pattern: /^\/support\/tickets$/, handler: myTicketsHandler },
  { method: "POST", pattern: /^\/support\/tickets$/, handler: createTicketHandler },
  // 내 문의 단건
  { method: "GET", pattern: /^\/support\/tickets\/(\d+)$/, handler: myTicketDetailHandler },
  // 티켓 스레드(전체 대화) 조회 / 추가 메시지
  { method: "GET", pattern: /^\/support\/tickets\/(\d+)\/messages$/, handler: ticketThreadHandler },
  { method: "POST", pattern: /^\/support\/tickets\/(\d+)\/messages$/, handler: addTicketMessageHandler },
];
