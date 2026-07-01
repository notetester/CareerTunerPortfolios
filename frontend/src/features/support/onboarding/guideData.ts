// 튜너봇 온보딩 가이드 — 설정·목데이터·카피를 한 곳에.
// 실제 분석 배선(적합도/오케)은 이 파일 밖에서 하지 않는다(이번 스코프: 받는 데까지 + 목 결과).
// design_handoff_claudecode_build/OrchestratorOnboarding.dc.html 의 시퀀스·토큰을 위젯 폭(360)에 맞춰 옮김.

/* ════════════════ 스텝 ════════════════ */
export type GuideStep =
  | "role" // 1. 직군
  | "skills" // 2. 핵심 역량
  | "docs" // 3. 서류(자소서·이력서·포폴)
  | "jd" // 4. 공고문
  | "analyzing" // (목) 적합도 계산 중
  | "fit" // 5. 적합도 결과(목)
  | "interview"; // 6. 면접 권유

/** 진행 점 표시용 순서(analyzing 은 fit 에 흡수되어 점으로는 안 보임). */
export const STEP_DOTS: GuideStep[] = ["role", "skills", "docs", "jd", "fit", "interview"];

/* ════════════════ 직군 · 분야 ════════════════ */
// 자주 쓰는 분야를 앞에, 나머지는 "직접 입력"으로 커버(목업 STEP1).
export const ROLES: string[] = [
  "개발·IT",
  "마케팅·광고",
  "디자인",
  "영업·영업관리",
  "경영·기획·전략",
  "회계·재무",
  "인사·HR",
  "생산·품질·공정",
  "연구·R&D",
  "의료·보건·간호",
  "교육",
  "금융",
  "미디어·콘텐츠",
  "물류·유통",
  "공공·행정",
];

/** 링크 필드 종류 — MASTER_PLAN §6 "분석에 넣음 vs 저장만" 구분을 필드 메타로 인코딩. */
export type LinkKey = "github" | "blog" | "sns";

export interface LinkField {
  label: string;
  placeholder: string;
  /** analyze: 내용을 읽어 분석에 반영 · paste: 붙여넣기로 받아 분석 · store: URL 문자열만 보관(파싱 안 함) */
  mode: "analyze" | "paste" | "store";
  hint: string;
}

export const LINK_FIELDS: Record<LinkKey, LinkField> = {
  github: {
    label: "GitHub",
    placeholder: "github.com/username 또는 레포 주소",
    mode: "analyze",
    hint: "레포 README를 읽어 프로젝트 경험으로 반영해요.",
  },
  blog: {
    label: "블로그·노션",
    placeholder: "글 내용을 여기에 붙여넣어 주세요",
    mode: "paste",
    hint: "링크 크롤링 대신 붙여넣기로 받아 분석에 넣어요.",
  },
  sns: {
    label: "인스타·SNS·포트폴리오 URL",
    placeholder: "https://…",
    mode: "store",
    hint: "링크만 저장해 둬요(내용 분석은 하지 않아요).",
  },
};

export interface JobField {
  /** 핵심 역량 선택지(직군 적응형). */
  skills: string[];
  /** 역량 질문 앞머리(직군 언어). */
  skillLead: string;
  /** 이 직군에서 노출할 링크 필드. */
  links: LinkKey[];
}

const DEFAULT_FIELD: JobField = {
  skills: ["직무 전문성", "커뮤니케이션", "문제 해결", "프로젝트 관리", "데이터 분석", "리더십"],
  skillLead: "어떤 역량을 중심으로 준비할까요?",
  links: ["blog", "sns"],
};

// 직군별 언어 전환(목업 STEP2 info: 개발=기술스택 / 간호=진료과·근무형태 / 회계=회계기준·ERP / 디자인=툴).
const JOB_FIELDS: Record<string, JobField> = {
  "개발·IT": {
    skills: ["Java·Spring", "Python", "JavaScript·TS", "React", "Node.js", "DB·SQL", "AWS·클라우드", "DevOps"],
    skillLead: "어떤 기술스택 중심으로 준비할까요?",
    links: ["github", "blog", "sns"],
  },
  "마케팅·광고": {
    skills: ["퍼포먼스 마케팅", "GA4·데이터 분석", "콘텐츠 기획", "브랜딩", "그로스", "SNS·커뮤니티", "CRM"],
    skillLead: "어떤 역량 중심으로 준비할까요?",
    links: ["blog", "sns"],
  },
  디자인: {
    skills: ["UI·UX", "Figma", "그래픽·브랜딩", "프로토타이핑", "디자인 시스템", "모션"],
    skillLead: "어떤 툴·역량 중심으로 준비할까요?",
    links: ["sns", "blog"],
  },
  "회계·재무": {
    skills: ["재무회계", "관리회계", "세무", "ERP(SAP·더존)", "결산", "자금·예산"],
    skillLead: "어떤 회계 영역 중심으로 준비할까요?",
    links: ["blog", "sns"],
  },
  "의료·보건·간호": {
    skills: ["진료과 경험", "근무형태(3교대·상근)", "간호 자격·면허", "환자 응대", "EMR", "감염관리"],
    skillLead: "어떤 근무형태·전문 분야 중심으로 준비할까요?",
    links: ["sns"],
  },
};

export function getField(role: string | null): JobField {
  if (!role) return DEFAULT_FIELD;
  return JOB_FIELDS[role] ?? DEFAULT_FIELD;
}

/* ════════════════ 서류 슬롯 ════════════════ */
export interface DocSlot {
  key: "cover" | "resume" | "portfolio";
  label: string;
  desc: string;
  /** 업로드 시 FileAsset.kind (서류 전용). 자소서는 전용 kind 가 없어 ATTACHMENT. */
  kind: "ATTACHMENT" | "RESUME" | "PORTFOLIO";
  icon: "FileText" | "UserRound" | "Briefcase";
  optional?: boolean;
}

export const DOC_SLOTS: DocSlot[] = [
  { key: "cover", label: "자기소개서", desc: "PDF·DOCX·직접 붙여넣기", kind: "ATTACHMENT", icon: "FileText" },
  { key: "resume", label: "이력서", desc: "경력·학력·자격", kind: "RESUME", icon: "UserRound" },
  { key: "portfolio", label: "포트폴리오 · 선택", desc: "PDF·이미지", kind: "PORTFOLIO", icon: "Briefcase", optional: true },
];

/* ════════════════ 카피(A톤: 뜻+다음 행동, 존대체, 이모지 절제) ════════════════ */
// 톤은 여기서 잡고 나머지는 이 패턴으로 확장. "그래서 어쩌라고" 방지 — 각 줄에 의미 해석.
export const COPY = {
  role: "환영해요. 딱 맞게 도와드리려면, 먼저 **어떤 일을 준비하시는지** 알려주세요. 고르시면 이후 질문이 그 분야 언어로 바뀌어요.",
  roleHint: "자주 찾는 분야를 먼저 뒀어요. 없으면 직접 입력하셔도 돼요.",
  skills: (role: string) =>
    `**${role}** 준비시는군요. ${getField(role).skillLead} 고르신 역량이 **면접 질문과 자소서 방향**을 정해요. (여러 개 가능)`,
  docs: "가진 서류가 있으면 올려주세요. **한 번 올리면 분석·면접·첨삭에 그대로 재활용**돼요. 없으면 건너뛰고 나중에 올려도 괜찮아요.",
  jd: "지원하실 **공고**를 올려주세요. 링크만 붙여도 제가 읽어서 **자격요건을 뽑아** 드려요. 파일·붙여넣기도 돼요.",
  // 진행 중 — "지금 뭘 하는지" 사람 말로. 파트별 다른 문구(실제 SSE 연결 시 재사용).
  analyzing: [
    "지금 공고를 뜯어보는 중이에요, 30초만요.",
    "서류에서 강점이 될 만한 부분을 골라내고 있어요.",
    "공고 요건과 서류를 한 줄씩 대조하는 중이에요.",
  ],
  // 적합도: 점수+해석+보완만. 순위·상위% 표현 금지(데이터 없음).
  fitLead: "서류와 공고를 맞춰봤어요. **강점은 살리고, 보완점은 면접·첨삭에서 같이 채워**드릴게요.",
  // FIT 이 아직 안 나온 경우(케이스 방금 생성 → 공고 분석 비동기 대기). 점수 지어내지 않고 정직하게.
  fitPending: "**자소서 교정은 끝났어요.** 적합도 점수는 공고 분석이 마무리되면 **지원 건**에서 확인할 수 있어요.",
  fitEmpty: "아직 분석할 자료가 부족해요. **자소서나 공고**를 올리면 여기서 강점·보완을 정리해 드릴게요.",
  writeDone: "**자소서 교정 완료** — 면접·공고를 반영해 문장을 다듬었어요.",
  interview: "이 공고 기준으로 **모의 면접**을 해볼까요? 방금 나온 **보완점 위주로** 질문을 준비했어요. 면접은 전용 화면에서 편하게 진행돼요.",
  // 면접 권유 — C톤 허용(따뜻하게).
  interviewWarm: "긴장되시죠. 실전처럼 한 번 해보면 훨씬 편해져요. 준비되면 시작해요.",
} as const;
