export interface PublicArticle {
  slug: string;
  category: string;
  title: string;
  summary: string;
  publishedAt: string;
  readMinutes: number;
  body: string[];
}

export interface PublicRole {
  slug: string;
  title: string;
  team: string;
  location: string;
  employmentType: string;
  summary: string;
  responsibilities: string[];
  qualifications: string[];
}

export interface SocialChannel {
  id: "youtube" | "instagram" | "twitter" | "kakao";
  label: string;
  handle: string;
  description: string;
  cadence: string;
  posts: Array<{ title: string; body: string; publishedAt: string }>;
}

export const blogPosts: PublicArticle[] = [
  {
    slug: "application-case-workspace",
    category: "제품 이야기",
    title: "공고가 아니라 지원 건을 중심에 둔 이유",
    summary: "기업·직무·공고 조합별로 분석, 면접, 첨삭 기록을 한 문맥에 보존하는 CareerTuner의 설계 원칙을 소개합니다.",
    publishedAt: "2026-07-08",
    readMinutes: 6,
    body: [
      "같은 공고를 보더라도 지원자의 경력과 목표에 따라 준비 전략은 달라집니다. CareerTuner는 공고 파일 자체가 아니라 기업·직무·공고 조합인 지원 건을 제품의 중심 단위로 삼았습니다.",
      "지원 건 안에는 공고 revision, 요구 역량, 프로필 비교, 학습 과제, 예상 질문, 면접 답변과 첨삭 이력이 함께 쌓입니다. 사용자는 화면을 옮겨 다녀도 같은 지원 맥락을 잃지 않습니다.",
      "이 구조는 분석 결과의 재현성도 높입니다. 어떤 프로필 버전과 공고 revision을 기준으로 결과가 만들어졌는지 추적할 수 있어, 단순한 생성형 AI 화면보다 검토 가능한 취업 준비 기록에 가깝습니다.",
    ],
  },
  {
    slug: "ai-evidence-and-consent",
    category: "신뢰와 안전",
    title: "AI 결과에 근거와 동의 상태를 함께 남기는 방법",
    summary: "AI 기능 실행 전 동의를 확인하고 결과·근거·실패 이력을 분리해 관리하는 제품 원칙을 정리했습니다.",
    publishedAt: "2026-07-04",
    readMinutes: 5,
    body: [
      "취업 정보에는 이력서, 자기소개서, 면접 답변처럼 민감한 개인 맥락이 포함됩니다. CareerTuner는 서비스 가입 동의와 AI 처리 동의, 이력서 분석 동의를 구분해 사용자가 처리 범위를 직접 선택하도록 설계합니다.",
      "AI 동의를 철회하면 관련 기능은 즉시 중단되며, 설정에서 다시 동의하기 전까지 실행할 수 없습니다. 동의와 철회는 현재값을 덮어쓰는 대신 시간 순서의 이력으로 남깁니다.",
      "분석 결과는 점수만 보여주지 않습니다. 사용한 입력 버전, 근거, 신뢰도, provider와 실패·fallback 상태를 함께 기록해 사용자가 결과를 검토하고 수정할 수 있게 합니다.",
    ],
  },
  {
    slug: "multi-channel-release",
    category: "엔지니어링",
    title: "하나의 제품을 웹·모바일·데스크톱으로 전달하기",
    summary: "React 기반 사용자 경험과 Spring API를 Pages, PWA, Android, iOS, 데스크톱 산출물로 검증하는 릴리즈 흐름입니다.",
    publishedAt: "2026-06-28",
    readMinutes: 7,
    body: [
      "CareerTuner의 사용자 화면은 React와 Vite를 중심으로 구성하고, Spring Boot API와 명시적인 mock registry를 같은 계약으로 사용합니다. 덕분에 전체 서버 환경과 정적 포트폴리오 데모를 분리해 검증할 수 있습니다.",
      "모바일은 PWA와 Capacitor 패키지를, 데스크톱은 압축본·설치본·포터블 실행 형태를 함께 다룹니다. 산출물마다 단순히 빌드 성공만 보는 것이 아니라 로그인, 지원 건, 분석, 면접과 설정 흐름을 smoke test 대상으로 둡니다.",
      "공개 데모에는 민감정보를 넣지 않습니다. 대신 실제 코드 경로, 아키텍처와 지식 그래프를 공개해 구현 깊이를 검토할 수 있도록 구성했습니다.",
    ],
  },
];

export const pressReleases: PublicArticle[] = [
  {
    slug: "careertuner-public-beta",
    category: "출범 소식",
    title: "CareerTuner, 지원 건 중심 AI 취업 전략 플랫폼 공개 베타 출범",
    summary: "공고 분석부터 면접·첨삭까지 한 작업공간에서 이어지는 CareerTuner 공개 베타를 선보였습니다.",
    publishedAt: "2026-07-10",
    readMinutes: 3,
    body: [
      "CareerTuner 프로젝트 팀은 2026년 7월 10일, 채용공고와 지원자 프로필을 연결해 준비 과정을 관리하는 AI 취업 전략 플랫폼의 공개 베타를 발표했습니다.",
      "공개 베타는 지원 건을 중심으로 공고 구조화, 스펙 비교, 지원 전략, 학습 과제, 예상 질문, 모의면접과 답변 첨삭을 연결합니다. 사용자와 관리자 화면, AI 실행 이력과 멀티채널 릴리즈 흐름도 함께 구현했습니다.",
      "이번 공개는 포트폴리오·심사용 데모 단계입니다. 실제 채용 결과를 보장하는 서비스가 아니며, AI 결과는 사용자가 검토해 활용하는 참고 정보로 제공됩니다.",
    ],
  },
  {
    slug: "second-brain-open",
    category: "기술 공개",
    title: "CareerTuner, 프로젝트 코드와 문서를 연결한 공개 Second Brain 선보여",
    summary: "Obsidian, LLM Wiki와 Graphify를 연결한 110개 노드의 프로젝트 근거 지도를 공개했습니다.",
    publishedAt: "2026-07-09",
    readMinutes: 4,
    body: [
      "CareerTuner는 제품 기능, Spring/MyBatis 백엔드, React 프런트엔드, AI/ML 실험과 릴리즈 산출물의 관계를 탐색할 수 있는 공개 Second Brain을 선보였습니다.",
      "공개 지식맵은 110개 코드·문서 노드와 Wiki 근거 페이지를 연결합니다. 리뷰어는 기능 설명에서 실제 저장소 상대 경로까지 내려가 구현 근거를 확인할 수 있습니다.",
      "API 키, 계정값, 비공개 raw output은 공개 대상에서 제외했습니다. 원본 지식은 비공개 Obsidian 저장소에 유지하고 검토된 공개본만 데모에 반영합니다.",
    ],
  },
  {
    slug: "release-matrix-ready",
    category: "제품 업데이트",
    title: "웹·Android·iOS·데스크톱을 아우르는 릴리즈 매트릭스 점검 완료",
    summary: "정적 데모부터 설치형·포터블 데스크톱 산출물까지 전체 배포 경로를 한 번에 점검했습니다.",
    publishedAt: "2026-07-03",
    readMinutes: 3,
    body: [
      "CareerTuner 프로젝트 팀은 웹 데모, Android APK, iOS simulator build와 데스크톱 zip·installer·portable 산출물의 릴리즈 경로를 점검했습니다.",
      "각 채널은 동일한 제품 정보 구조를 공유하되 화면 크기, 저장 위치와 배포 환경에 맞는 별도 검증 기준을 적용합니다.",
      "앞으로도 typecheck, backend test, 민감정보 검사와 실제 배포 URL smoke test를 릴리즈 완료 조건으로 유지할 계획입니다.",
    ],
  },
];

export const openRoles: PublicRole[] = [
  {
    slug: "product-engineer",
    title: "Product Engineer",
    team: "제품 엔지니어링",
    location: "서울·하이브리드",
    employmentType: "프로젝트 협업",
    summary: "사용자 지원 흐름을 React와 Spring API의 수직 기능으로 완성합니다.",
    responsibilities: ["지원 건 중심 사용자 기능 구현", "관리자 운영 화면과 API 동시 완성", "테스트·릴리즈 기준 유지"],
    qualifications: ["React 또는 Spring 기반 제품 개발 경험", "도메인 요구를 API·DB·UI로 연결하는 능력", "코드 리뷰와 문서화에 익숙한 분"],
  },
  {
    slug: "ai-ml-engineer",
    title: "AI/ML Engineer",
    team: "AI 플랫폼",
    location: "원격 협업 가능",
    employmentType: "프로젝트 협업",
    summary: "취업 전략·면접·첨삭 모델과 evidence gate를 재현 가능한 파이프라인으로 만듭니다.",
    responsibilities: ["데이터셋·평가 지표 설계", "provider fallback과 schema validation 개선", "실험 보고서와 artifact 경계 관리"],
    qualifications: ["LLM 평가 또는 fine-tuning 경험", "Python 기반 실험 자동화 경험", "개인정보·근거 품질을 함께 고려하는 분"],
  },
  {
    slug: "product-designer",
    title: "Product Designer",
    team: "제품 경험",
    location: "서울·하이브리드",
    employmentType: "프로젝트 협업",
    summary: "복잡한 분석 결과와 동의·설정 흐름을 사용자가 이해하기 쉬운 작업공간으로 설계합니다.",
    responsibilities: ["웹·모바일 정보 구조 설계", "분석·면접·설정 UX 개선", "접근성과 디자인 시스템 점검"],
    qualifications: ["운영형 SaaS 또는 복합 폼 UX 경험", "반응형 화면 설계 능력", "정책 문구와 제품 행동을 일치시키는 분"],
  },
];

export const socialChannels: SocialChannel[] = [
  {
    id: "youtube",
    label: "YouTube",
    handle: "@CareerTuner",
    description: "제품 데모, 면접 준비 흐름과 엔지니어링 기록을 영상으로 소개합니다.",
    cadence: "격주 목요일",
    posts: [
      { title: "5분 만에 보는 지원 건 분석", body: "공고 등록부터 부족 역량과 학습 과제 확인까지의 실제 데모입니다.", publishedAt: "2026-07-09" },
      { title: "Second Brain 그래프 투어", body: "코드와 문서를 110개 근거 노드로 탐색하는 방법을 소개합니다.", publishedAt: "2026-07-06" },
    ],
  },
  {
    id: "instagram",
    label: "Instagram",
    handle: "@careertuner.today",
    description: "취업 준비 체크리스트, 제품 업데이트와 팀의 제작 과정을 짧게 전합니다.",
    cadence: "주 2회",
    posts: [
      { title: "이번 주 제품 노트", body: "동의 철회와 재동의 흐름, 푸터 콘텐츠를 실제 화면으로 연결했습니다.", publishedAt: "2026-07-10" },
      { title: "면접 답변 점검 카드", body: "근거, 구조, 전달력 세 축으로 답변을 점검해 보세요.", publishedAt: "2026-07-07" },
    ],
  },
  {
    id: "twitter",
    label: "X / Twitter",
    handle: "@CareerTunerDev",
    description: "릴리즈 상태, 기술 결정과 짧은 제품 소식을 가장 빠르게 공유합니다.",
    cadence: "업데이트 수시",
    posts: [
      { title: "Public beta is live", body: "지원 건 중심 CareerTuner 공개 베타와 프로젝트 Wiki를 열었습니다.", publishedAt: "2026-07-10" },
      { title: "Release matrix", body: "Pages, mobile, desktop 산출물의 전체 smoke test를 완료했습니다.", publishedAt: "2026-07-03" },
    ],
  },
  {
    id: "kakao",
    label: "KakaoTalk",
    handle: "CareerTuner 소식 채널",
    description: "중요 공지, 가이드 업데이트와 공개 행사 알림을 모아 전달합니다.",
    cadence: "주요 공지 시",
    posts: [
      { title: "공개 베타 안내", body: "포트폴리오 데모에서 공고 분석, 면접, 첨삭과 지식맵을 확인할 수 있습니다.", publishedAt: "2026-07-10" },
      { title: "개인정보 설정 가이드", body: "설정에서 동의를 철회하거나 다시 동의하는 방법을 안내합니다.", publishedAt: "2026-07-08" },
    ],
  },
];
