const areas = {
  hub: { label: "Hub", color: "#4fd1c5", cx: 640, cy: 410 },
  product: { label: "Product", color: "#f2c14e", cx: 260, cy: 170 },
  workflow: { label: "Workflow", color: "#f59e0b", cx: 250, cy: 445 },
  frontend: { label: "Frontend", color: "#79a7ff", cx: 390, cy: 665 },
  backend: { label: "Backend", color: "#5eead4", cx: 885, cy: 175 },
  ai: { label: "AI/ML", color: "#b794f4", cx: 1025, cy: 395 },
  data: { label: "Data", color: "#8bd17c", cx: 805, cy: 660 },
  release: { label: "Release", color: "#fb7185", cx: 1110, cy: 650 },
  governance: { label: "Governance", color: "#c084fc", cx: 640, cy: 105 },
  docs: { label: "Docs", color: "#a3e635", cx: 595, cy: 720 },
};

const nodes = [
  node("career-tuner", "CareerTuner", "hub", "core", 32, "채용공고, 사용자 스펙, 학습 과제, 면접 준비를 하나의 지원 건 안에서 연결하는 AI 취업 전략 플랫폼입니다.", [
    "공고 하나가 아니라 지원 건을 중심으로 제품 흐름을 구성합니다.",
    "사용자 화면, 관리자 화면, AI/ML, 릴리즈 산출물이 같은 지식맵 안에서 연결됩니다.",
    "이 공개본은 내부 secret과 raw output을 제외하고 공개 가능한 구조 키워드를 촘촘히 보여줍니다.",
  ], ["application-case", "product-structure", "spring-api", "react-spa", "ai-orchestrator", "release-matrix", "ownership"]),

  node("public-knowledge-map", "공개 지식맵", "hub", "map", 24, "비공개 Obsidian vault의 판단 맥락을 공개 가능한 키워드 그래프로 재구성한 심사용 뷰입니다.", [
    "문서 원문 복사가 아니라 프로젝트 핵심 개념과 연결 관계를 공개용으로 정리합니다.",
    "검색과 영역 필터로 특정 기능, 기술, 릴리즈 축을 빠르게 좁힐 수 있습니다.",
    "선택한 노드의 주변 키워드를 따라가며 구조를 탐색할 수 있습니다.",
  ], ["obsidian-overlay", "private-vault", "demo-pages", "decision-log", "architecture-doc"]),
  node("private-vault", "내부 Vault", "hub", "boundary", 22, "상세 판단, 내부 노트, 원자료, 공개 제외 자료는 비공개 Obsidian vault에 남기는 원본 저장소입니다.", [
    "공개본은 원본 vault를 대체하지 않고 공개 가능한 요약과 연결만 제공합니다.",
    "API 키, 계정, 내부 endpoint, raw output은 공개 대상에서 제외합니다.",
    "공개 범위가 넓어지면 이 Pages 하위 경로에 안전하게 확장합니다.",
  ], ["public-knowledge-map", "obsidian-overlay", "ai-artifacts", "ai-reports", "submodule-pointer"]),

  node("application-case", "지원 건", "product", "concept", 25, "CareerTuner의 핵심 단위입니다. 한 기업·직무·공고 조합에 대한 분석과 준비 기록이 모입니다.", [
    "공고 분석, 기업 분석, 스펙 비교, 전략, 면접, 첨삭 기록이 이 단위에 묶입니다.",
    "보관과 삭제는 상태값이 아니라 별도 시각 컬럼으로 관리합니다.",
    "사용자는 지원 건별로 준비 현황과 다음 행동을 추적합니다.",
  ], ["job-posting", "company-analysis", "fit-analysis", "interview-prep", "correction", "application-case-table", "soft-delete"]),
  node("job-posting", "공고문", "product", "input", 18, "텍스트, PDF, 이미지, URL, 수동 입력 등으로 들어오는 채용공고 원문입니다.", [
    "같은 지원 건 안에서 공고 revision을 관리합니다.",
    "공고 분석과 필수/우대조건 추출의 입력입니다.",
    "파일과 텍스트 입력은 공개 데모에서는 mock 데이터로 대체됩니다.",
  ], ["job-upload", "pdf-image-upload", "job-posting-worker", "job-posting-revision"]),
  node("company-analysis", "기업 분석", "product", "analysis", 18, "공고와 외부 근거를 바탕으로 기업 현황과 출처 메타데이터를 분리해 다루는 분석 축입니다.", [
    "확인된 사실과 AI 추론을 구분하는 방향으로 설계했습니다.",
    "출처 URL, 확인 시점, 재조회 권장 시점을 저장할 수 있어야 합니다.",
    "관리자 화면에서 출처 메타데이터를 보정할 수 있습니다.",
  ], ["evidence-source", "admin-ui", "spring-api", "ai-orchestrator"]),
  node("profile", "사용자 프로필", "product", "input", 18, "이력서, 경력, 기술스택, 자격증, 포트폴리오 등 공고와 비교할 사용자 기준 정보입니다.", [
    "공고 분석과 적합도 분석의 비교 기준입니다.",
    "분석 재현성을 위해 프로필 버전과 분석 실행 단위를 분리합니다.",
    "민감한 개인 데이터는 공개 데모에는 실데이터로 포함하지 않습니다.",
  ], ["user-profile-version", "spec-compare", "fit-analysis"]),
  node("spec-compare", "스펙 비교", "product", "feature", 18, "공고 요구조건과 사용자 프로필의 강점·부족 역량을 비교하는 사용자 기능입니다.", [
    "지원 전략과 학습 과제 추천의 입력입니다.",
    "대시보드의 준비도 요약에도 연결됩니다.",
    "C 영역 적합도 분석과 가까운 사용자 경험입니다.",
  ], ["profile", "fit-analysis", "learning-tasks", "dashboard"]),
  node("fit-analysis", "적합도 분석", "product", "feature", 20, "지원 건별 최신 적합도, 비교 매트릭스, 신뢰도, 액션 보드를 제공하는 핵심 분석입니다.", [
    "강점, 보완점, 지원 판단, 톤별 전략을 구조화합니다.",
    "사용자 화면과 관리자 분석 관리 화면이 함께 필요합니다.",
    "결과는 analysis_run, profile version, job posting revision과 연결됩니다.",
  ], ["spec-compare", "strategy", "action-board", "fit-analysis-table", "admin-fit-analysis", "career-strategy-llm"]),
  node("strategy", "지원 전략", "product", "output", 18, "공고와 사용자 역량 차이를 바탕으로 지원 방향, 어필 포인트, 보완 우선순위를 제안합니다.", [
    "Career Strategy LLM의 핵심 산출물입니다.",
    "학습 과제, 자격증 추천, 면접 예상 질문과 연결됩니다.",
    "심사 관점에서는 AI가 다음 행동을 만들어 낸다는 점을 보여줍니다.",
  ], ["career-strategy-llm", "learning-tasks", "interview-prep", "action-board"]),
  node("learning-tasks", "학습 과제", "product", "output", 17, "부족 역량을 보완하기 위한 학습, 자격증, 프로젝트 보강 과제입니다.", [
    "대시보드와 액션 보드에서 완료 상태를 추적합니다.",
    "지원 건의 다음 행동을 구체화하는 장치입니다.",
    "공개 데모에서는 완료 토글을 체험할 수 있습니다.",
  ], ["dashboard", "action-board", "strategy", "mock-registry"]),
  node("interview-prep", "면접 대비", "product", "feature", 18, "지원 건의 공고·스펙·전략을 바탕으로 예상 질문과 모의면접 흐름을 제공합니다.", [
    "텍스트 면접, 답변 평가, 기본 첨삭과 연결됩니다.",
    "향후 음성/영상 및 비언어 분석으로 확장됩니다.",
    "합격/불합격 판정이 아니라 준비 품질 개선을 목표로 합니다.",
  ], ["interview-llm", "nonverbal-analysis", "interview-session", "correction"]),
  node("correction", "AI 첨삭", "product", "feature", 18, "자기소개서와 답변 문장을 공고 요구사항과 사용자 맥락에 맞게 개선하는 기능입니다.", [
    "지원 건 상세와 독립 첨삭 화면 양쪽에서 접근할 수 있습니다.",
    "성공 이력과 실패 로그는 관리자 화면에서 분리해 볼 수 있습니다.",
    "E Correction LLM과 직접 연결되는 제품 기능입니다.",
  ], ["correction-llm", "correction-request", "admin-correction", "ai-usage-log"]),
  node("dashboard", "대시보드", "product", "screen", 18, "지원 현황, 최근 AI 분석, 부족 역량, 오늘의 할 일, 크레딧을 모아 보여주는 요약 화면입니다.", [
    "독립 데이터 소유자가 아니라 여러 도메인의 요약입니다.",
    "사용자 dashboard와 관리자 dashboard가 운영 관점에서 짝을 이룹니다.",
    "공개 데모의 주요 첫 체험 화면입니다.",
  ], ["application-case", "learning-tasks", "mock-demo-build", "admin-dashboard"]),
  node("community", "커뮤니티", "product", "domain", 16, "후기, 질문 공유, 신고/정책과 연결되는 사용자 도메인입니다.", [
    "F 영역 커뮤니티 검열과 연결됩니다.",
    "운영 정책과 관리자 화면이 필요한 기능입니다.",
    "MVP 이후 확장성이 큰 영역입니다.",
  ], ["ollama", "provider-dispatcher", "admin-ui"]),
  node("credit-payment", "크레딧/결제", "product", "domain", 16, "요금제, 크레딧 충전, 사용량, 결제 내역을 다루는 수익화와 운영 도메인입니다.", [
    "AI 기능 사용량과 연결됩니다.",
    "관리자 크레딧 조정과 감사 로그가 필요합니다.",
    "공개 데모에서는 실제 결제가 아니라 화면 구조만 보여줍니다.",
  ], ["ai-usage-log", "admin-ui", "dashboard"]),

  node("job-upload", "공고 업로드", "workflow", "step", 17, "지원 건 생성 후 공고 텍스트, 파일, URL, 수동 입력을 받아 분석 흐름으로 넘깁니다.", [
    "공고 원문의 revision 관리와 연결됩니다.",
    "텍스트 입력은 MVP 1차, PDF/이미지는 후속 범위입니다.",
    "입력 방식은 분석 신뢰도와 증거 추적에 영향을 줍니다.",
  ], ["job-posting", "pdf-image-upload", "analysis-run"]),
  node("pdf-image-upload", "PDF/이미지", "workflow", "input", 16, "텍스트가 없는 PDF와 이미지는 문서 추출 워커나 허용된 OCR fallback으로 처리하는 확장 입력입니다.", [
    "MVP 이후 기업/공고 원문 입력 범위를 넓힙니다.",
    "원본 파일과 추가 분석 가능 여부를 정책적으로 구분합니다.",
    "공개 데모에는 실파일이 포함되지 않습니다.",
  ], ["job-posting-worker", "fallback", "file-domain"]),
  node("analysis-run", "분석 실행", "workflow", "step", 18, "분석 재현성을 위해 어떤 프로필 버전과 공고 revision으로 분석했는지 묶는 실행 단위입니다.", [
    "fit_analysis, job_analysis, company_analysis 결과의 공통 맥락입니다.",
    "사용자 확정 여부와 수정 결과를 함께 기록할 수 있습니다.",
    "AI 사용량과 실패 로그 추적에도 연결됩니다.",
  ], ["user-profile-version", "job-posting-revision", "fit-analysis-table", "ai-usage-log"]),
  node("evidence-source", "근거/출처", "workflow", "quality", 16, "공고 원문, 웹 스니펫, 수동 출처를 구분해 분석 근거를 관리하는 품질 축입니다.", [
    "확인된 사실과 추론을 구분하는 설계와 연결됩니다.",
    "기업 분석에서 특히 중요합니다.",
    "공개 뷰어에는 구체 출처 값 대신 개념만 노출합니다.",
  ], ["company-analysis", "json-schema", "admin-ui"]),
  node("confidence", "신뢰도", "workflow", "quality", 16, "적합도와 분석 결과의 확실성, 근거 수준, 재분석 필요성을 드러내는 판단 정보입니다.", [
    "지원 판단과 액션 보드의 우선순위를 보조합니다.",
    "관리자 검토와 사용자 확정 플로우에 연결됩니다.",
    "AI 결과를 무조건 사실로 보이지 않게 하는 장치입니다.",
  ], ["fit-analysis", "evidence-source", "admin-fit-analysis"]),
  node("action-board", "액션 보드", "workflow", "output", 17, "분석 결과를 사용자의 다음 행동으로 바꾸는 체크리스트형 작업 보드입니다.", [
    "학습 과제, 지원 전략, 면접 대비를 묶습니다.",
    "공개 데모에서 토글 가능한 사용자 체험 포인트입니다.",
    "지원 건 상세의 반복 방문성을 높입니다.",
  ], ["fit-analysis", "learning-tasks", "strategy", "dashboard"]),
  node("report", "지원 건 리포트", "workflow", "output", 16, "지원 건 단위의 분석, 전략, 면접, 첨삭 결과를 심사·회고 가능한 형태로 묶는 산출물입니다.", [
    "취업 준비 과정의 누적 증거가 됩니다.",
    "장기 취업 경향 분석과 연결될 수 있습니다.",
    "공개 데모에서는 요약 카드로만 표현합니다.",
  ], ["application-case", "dashboard", "release-matrix"]),

  node("react-spa", "React SPA", "frontend", "stack", 20, "사용자/관리자 화면을 하나의 Vite React 앱에서 관리하는 반응형 SPA입니다.", [
    "관리자 화면은 별도 앱이 아니라 frontend/src/admin 아래에 둡니다.",
    "PWA와 Capacitor 패키징이 같은 dist를 공유합니다.",
    "공개 데모도 같은 프런트 산출물을 mock 모드로 빌드합니다.",
  ], ["vite", "typescript", "tailwind", "admin-ui", "pwa", "mock-demo-build"]),
  node("vite", "Vite", "frontend", "stack", 16, "개발 서버와 정적 빌드를 담당하며 GitHub Pages base path와 데모 모드를 처리합니다.", [
    "로컬 개발에서는 /api 프록시로 백엔드와 연결됩니다.",
    "Pages 배포는 /CareerTunerPortfolios/ base path를 사용합니다.",
    "mock 빌드와 모바일 sync의 출발점입니다.",
  ], ["demo-pages", "mock-demo-build", "capacitor"]),
  node("typescript", "TypeScript", "frontend", "stack", 16, "프런트 타입 안정성과 CI typecheck의 기준입니다.", [
    "릴리즈 전 타입체크 실패는 담당 파일 기준으로 바로 추적합니다.",
    "mock registry와 API contract 유지에 도움이 됩니다.",
    "공개 데모 배포 워크플로는 typecheck 결과를 warning으로 보고할 수 있습니다.",
  ], ["frontend-ci", "mock-registry", "react-spa"]),
  node("tailwind", "Tailwind v4", "frontend", "stack", 15, "프런트 UI 스타일 시스템의 기반입니다.", [
    "반응형 웹과 모바일 화면 대응에 사용됩니다.",
    "디자인 시스템과 shadcn/ui 사용을 함께 고려합니다.",
    "공개 지식맵은 별도 정적 CSS로 동작합니다.",
  ], ["responsive-layout", "shadcn", "react-spa"]),
  node("shadcn", "shadcn/ui", "frontend", "ui", 14, "프런트 UI 컴포넌트 기반으로 쓰이는 디자인 시스템 축입니다.", [
    "사용자 화면과 관리자 화면의 일관성을 유지합니다.",
    "상세 구현은 기능별 components 폴더로 나뉩니다.",
    "운영 도구 화면에서도 같은 앱 구조를 공유합니다.",
  ], ["tailwind", "admin-ui", "feature-modules"]),
  node("react-router", "React Router", "frontend", "routing", 15, "사용자 라우트와 관리자 라우트를 같은 SPA 안에서 관리합니다.", [
    "라우팅 변경은 공통 영역 영향이 커서 주의가 필요합니다.",
    "데모 Pages에서는 SPA fallback이 필요합니다.",
    "관리자 /admin/** 라우트와 사용자 기능 라우트가 함께 존재합니다.",
  ], ["admin-ui", "common-areas", "demo-pages"]),
  node("feature-modules", "기능 모듈", "frontend", "structure", 17, "frontend/src/features/<feature>와 admin/features/<feature> 표준 구조입니다.", [
    "기능별 api, hooks, types, components, pages를 분리합니다.",
    "사용자 기능과 관리자 기능을 같은 릴리스 기준으로 봅니다.",
    "타인 담당 폴더 수정 시 충돌 위험을 먼저 봅니다.",
  ], ["admin-ui", "ownership", "common-areas"]),
  node("admin-ui", "관리자 화면", "frontend", "admin", 18, "29개 세부 권한에 따라 route·navigation·action을 제어하는 사용자 기능의 운영 표면입니다.", [
    "읽기 권한이 없으면 domain tab과 사용자 상세의 관리자 option을 노출하지 않습니다.",
    "권한 확인 전 route module을 import하지 않고 조회 실패도 fail-closed합니다.",
    "PR #408은 AI 상담 공백 사유를 실제 점수·임계값 관계에 맞게 분리했습니다.",
  ], ["admin-dashboard", "admin-fit-analysis", "admin-correction", "spring-api", "feature-modules", "admin-permission-boundary"]),
  node("mock-registry", "Mock Registry", "frontend", "demo", 18, "정적 체험과 장애 시 read-only fallback을 위한 API registry입니다.", [
    "정적 mock build와 AWS-first outage mode는 서로 다른 계약입니다.",
    "일반 Sites build는 실제 API를 먼저 사용합니다.",
    "미등록 endpoint와 권한 없는 mutation은 명시적으로 실패합니다.",
  ], ["mock-demo-build", "demo-pages", "android-apk", "admin-ui", "outage-demo-fallback"]),
  node("pwa", "PWA", "frontend", "mobile", 17, "manifest, service worker, 오프라인 셸을 통해 웹을 앱처럼 설치하는 모바일/데스크톱 경험입니다.", [
    "캐시는 정적 리소스와 공개 정보 중심으로 제한합니다.",
    "개인·민감 데이터는 장기 캐시하지 않는 원칙입니다.",
    "Android/iOS 패키징 이전에도 바로 설치형 경험을 제공합니다.",
  ], ["service-worker", "capacitor", "mobile-policy"]),
  node("service-worker", "Service Worker", "frontend", "mobile", 14, "PWA 캐시와 오프라인 셸을 담당하는 웹 플랫폼 기능입니다.", [
    "민감 데이터 장기 캐시는 피합니다.",
    "정적 리소스 중심 precache를 원칙으로 합니다.",
    "로그아웃 시 사용자 관련 캐시 삭제가 필요합니다.",
  ], ["pwa", "mobile-policy", "vite"]),
  node("capacitor", "Capacitor", "frontend", "mobile", 16, "같은 React dist를 Android/iOS 앱으로 패키징하는 모바일 확장 경로입니다.", [
    "PWA에서 Android APK, iOS 프로젝트로 확장합니다.",
    "mock 데모 APK와 실제 앱 빌드 흐름을 나눠 생각합니다.",
    "모바일 플랫폼별 권한과 서명 정책을 따로 관리합니다.",
  ], ["android-apk", "ios-build", "pwa"]),
  node("responsive-layout", "반응형 레이아웃", "frontend", "ux", 16, "3열 작업공간을 모바일에서 1열, Drawer, 접이식 카드로 접는 UX 원칙입니다.", [
    "지원 건 목록, 상세, 준비도 패널을 화면 폭에 맞춰 재배치합니다.",
    "반복 사용 도구는 정보 밀도와 스캔성을 우선합니다.",
    "모바일 앱 확장 전부터 웹에서 검증해야 합니다.",
  ], ["application-case", "tailwind", "mobile-policy"]),

  node("spring-api", "Spring API", "backend", "stack", 21, "Spring Boot 4.1, Java 21, MyBatis, MySQL 기반 REST API 서버입니다.", [
    "모든 컨트롤러는 /api/** 하위에 둡니다.",
    "응답은 ApiResponse envelope로 감쌉니다.",
    "도메인별 controller, service, mapper, domain 계층을 유지합니다.",
  ], ["spring-boot", "java21", "mybatis", "mysql", "api-response", "security"]),
  node("spring-boot", "Spring Boot 4.1", "backend", "stack", 16, "백엔드 런타임과 테스트, 설정의 기반입니다.", [
    "Spring Boot 4/Jackson 3 환경의 ObjectMapper 사용 규칙을 따릅니다.",
    "보안, CORS, OpenAPI 설정을 공통 config로 관리합니다.",
    "공통 영역 변경은 영향 범위를 먼저 확인합니다.",
  ], ["spring-api", "security", "openapi"]),
  node("java21", "Java 21", "backend", "stack", 15, "백엔드 애플리케이션의 Java 런타임 기준입니다.", [
    "Gradle과 테스트 환경도 Java 21 기준입니다.",
    "도메인 서비스와 mapper 인터페이스가 이 런타임 위에서 동작합니다.",
    "릴리즈 전 백엔드 테스트와 함께 검증합니다.",
  ], ["spring-api", "backend-ci"]),
  node("mybatis", "MyBatis", "backend", "persistence", 18, "CareerTuner 백엔드의 유일한 영속성 접근 방식입니다. JPA는 사용하지 않습니다.", [
    "@Mapper 인터페이스와 XML mapper를 사용합니다.",
    "SQL 흐름이 명시적으로 보이는 장점이 있습니다.",
    "MyBatis XML 경로는 도메인별 mapper 폴더를 따릅니다.",
  ], ["mapper-xml", "mysql", "application-case-table"]),
  node("mysql", "MySQL 8", "backend", "persistence", 16, "서비스 데이터, 분석 이력, JSON 컬럼, 사용량 로그를 저장하는 관계형 DB입니다.", [
    "배열/구조 데이터는 MySQL JSON 컬럼으로 저장할 수 있습니다.",
    "분석 재현성을 위해 revision과 version을 별도 엔터티로 둡니다.",
    "스키마 변경은 공통 영역 영향이 큽니다.",
  ], ["json-columns", "ai-usage-log", "common-areas"]),
  node("api-response", "ApiResponse<T>", "backend", "contract", 16, "모든 API 응답을 success, code, message, data envelope로 통일하는 계약입니다.", [
    "프런트 API 처리와 에러 처리 일관성을 높입니다.",
    "공통 web 패키지에 속합니다.",
    "공통 API 계약 변경은 팀 합의 대상입니다.",
  ], ["spring-api", "common-areas", "frontend-ci"]),
  node("security", "인증/권한", "backend", "security", 17, "JWT/stateless 보안, 29-code 관리자 권한, Firebase phone과 native OAuth trust boundary를 관리합니다.", [
    "익명·일반 회원과 권한 조회 실패를 frontend/backend 양쪽에서 fail-closed합니다.",
    "Firebase 공개 web config와 server service-account secret을 구분합니다.",
    "Native OAuth는 PKCE와 exact verified link, one-time handoff를 사용합니다.",
  ], ["jwt", "admin-ui", "common-areas", "admin-permission-boundary", "firebase-phone-trust", "native-auth-handoff"]),
  node("jwt", "JWT", "backend", "security", 14, "이메일 로그인과 API 인증에 쓰이는 stateless 토큰 기반 인증 방식입니다.", [
    "Bearer 인증으로 사용자와 관리자 API를 보호합니다.",
    "토큰 재발급과 로그아웃 흐름이 인증 영역에 포함됩니다.",
    "공개 데모에서는 실제 인증 서버 없이 mock으로 대체합니다.",
  ], ["security", "mock-registry", "admin-ui"]),
  node("mapper-xml", "Mapper XML", "backend", "persistence", 15, "MyBatis SQL을 도메인별 resources/mapper 경로에 두는 파일 구조입니다.", [
    "도메인별 mapper 인터페이스와 짝을 이룹니다.",
    "SQL 변경은 API와 데이터 모델에 직접 영향을 줍니다.",
    "대형 refactor보다 기능별 수직 변경을 우선합니다.",
  ], ["mybatis", "feature-modules", "ownership"]),
  node("domain-layers", "4계층 구조", "backend", "structure", 16, "controller, service, mapper, domain 계층을 기준으로 백엔드 기능을 구성합니다.", [
    "API 요청/응답은 필요 시 dto로 분리합니다.",
    "도메인 경계를 지키면 담당자 간 충돌이 줄어듭니다.",
    "공통 예외와 응답 규약은 common에 둡니다.",
  ], ["spring-api", "feature-modules", "api-response"]),
  node("openapi", "OpenAPI", "backend", "docs", 14, "springdoc-openapi 기반 API 확인과 문서화 축입니다.", [
    "개발 중 API 목록과 계약을 빠르게 확인합니다.",
    "관리자/사용자 API 변경의 외부 표면을 검토합니다.",
    "공개 데모에는 백엔드 API 원본을 포함하지 않습니다.",
  ], ["spring-boot", "api-response", "spring-api"]),

  node("ai-orchestrator", "AI 오케스트레이터", "ai", "system", 22, "사용자 요청을 받아 필요한 도메인 작업을 계획하고 의존 그래프대로 실행하는 자동 준비 파이프라인입니다.", [
    "인테이크 챗봇, Planner, 의존 그래프, 단계별 실행 상태가 연결됩니다.",
    "일부 도메인이 미완이어도 mock/skip/fallback으로 완주하는 방향입니다.",
    "진행 상황은 SSE 스트리밍 UX와 맞물립니다.",
  ], ["planner", "dependency-graph", "fallback", "sse", "provider-dispatcher"]),
  node("planner", "Planner", "ai", "system", 17, "사용자의 한 줄 요청과 부족 정보를 바탕으로 필요한 작업 파트를 선택하는 두뇌 역할입니다.", [
    "지원 건과 준비 모드를 정리합니다.",
    "전체 도메인을 무조건 돌리지 않고 필요한 단계만 계획합니다.",
    "오케스트레이터의 실행 그래프를 만듭니다.",
  ], ["ai-orchestrator", "dependency-graph", "intake-chat"]),
  node("intake-chat", "인테이크 챗봇", "ai", "ux", 16, "부족한 정보를 멀티턴 대화로 수집해 자동 준비 파이프라인에 넘기는 진입점입니다.", [
    "지원 건, 목표, 모드, 입력 부족분을 대화로 보완합니다.",
    "사용자 경험에서는 AI 기능의 첫 인상입니다.",
    "프런트 features/autoprep와 연결됩니다.",
  ], ["planner", "ai-orchestrator", "react-spa"]),
  node("dependency-graph", "의존 그래프", "ai", "system", 17, "프로필, 공고, 적합도, 자소서, 면접, 커뮤니티 등 도메인 실행 순서를 표현합니다.", [
    "병렬 실행 가능한 작업과 선행 작업을 분리합니다.",
    "도메인별 실패가 전체 흐름을 막지 않도록 fallback과 연결됩니다.",
    "이번 공개 지식맵도 같은 개념을 시각적으로 차용합니다.",
  ], ["ai-orchestrator", "fallback", "json-schema"]),
  node("provider-dispatcher", "Provider Dispatcher", "ai", "runtime", 18, "자체모델, Claude/OpenAI, 규칙/Mock 등 provider 우선순위를 한 곳에서 결정하는 구조입니다.", [
    "전략 인터페이스에는 @Primary 구현체가 여러 개 생기지 않게 합니다.",
    "provider 선택 지점은 한 곳이어야 합니다.",
    "비용 절감, 시연 안정성, 포트폴리오 증거 확보와 연결됩니다.",
  ], ["fallback", "openai-provider", "ollama", "mock-provider", "common-areas"]),
  node("fallback", "Fallback", "ai", "runtime", 18, "도메인이 명시적으로 허용한 provider retry·degrade만 수행하고 미허용 실패는 그대로 드러내는 실행 정책입니다.", [
    "정적 demo mock과 AI semantic fallback을 같은 성공으로 보지 않습니다.",
    "JSON schema parse나 낮은 loss만으로 model 품질을 판정하지 않습니다.",
    "E 최신 비교 실패처럼 완주하지 못한 run은 미검증 상태로 기록합니다.",
  ], ["provider-dispatcher", "mock-provider", "json-schema", "ai-usage-log", "model-evidence"]),
  node("openai-provider", "OpenAI Provider", "ai", "runtime", 16, "구조화 분석과 fallback 경로에서 사용할 수 있는 외부 AI provider입니다.", [
    "API 키는 환경변수로만 주입하고 공개하지 않습니다.",
    "공개 지식맵에는 provider 개념만 노출합니다.",
    "Responses API와 JSON schema 검증 흐름에 연결됩니다.",
  ], ["provider-dispatcher", "json-schema", "secret-scan"]),
  node("ollama", "Ollama", "ai", "runtime", 16, "로컬 LLM과 커뮤니티 검열 등 일부 자체 모델 실험의 실행 기반입니다.", [
    "4090 운영 문서와 실험 산출물은 artifact 경계에 따라 분리합니다.",
    "외부 API 비용 절감과 포트폴리오 증거 확보 목적이 있습니다.",
    "실운영 대체 엔진이 아니라 보조 provider로 봅니다.",
  ], ["provider-dispatcher", "ai-artifacts", "community"]),
  node("lora-qlora", "LoRA/QLoRA", "ai", "training", 16, "담당별 자체 LLM 파인튜닝 실험의 핵심 학습 방법입니다.", [
    "프로필, 커리어 전략, 첨삭 등 도메인별 실험과 연결됩니다.",
    "대형 산출물은 docs/ai-artifacts로 분리합니다.",
    "보고서는 docs/ai-reports에 남기는 경계가 있습니다.",
  ], ["career-strategy-llm", "correction-llm", "ai-reports", "ai-artifacts"]),
  node("career-strategy-llm", "Career Strategy LLM", "ai", "model", 18, "C 영역 자체 LLM 축으로 지원 전략, 부족 역량, 학습/자격증 추천과 연결됩니다.", [
    "사용자 스펙과 목표 공고를 기반으로 준비 전략을 생성합니다.",
    "validator, runner, helper는 본체에 남기고 큰 결과물은 artifact repo로 분리합니다.",
    "심사에서는 제품 가치와 자체 모델 운영 역량을 동시에 보여줍니다.",
  ], ["strategy", "fit-analysis", "lora-qlora", "ai-reports"]),
  node("correction-llm", "Correction LLM", "ai", "model", 17, "E 영역 자기소개서·답변 첨삭 모델 축입니다.", [
    "사용자 문맥과 채용공고 요구사항을 함께 반영하는 방향입니다.",
    "model card와 README로 범위와 현재 상태를 추적합니다.",
    "성공 이력과 실패 로그 관리가 운영 화면과 연결됩니다.",
  ], ["correction", "lora-qlora", "admin-correction"]),
  node("interview-llm", "Interview LLM", "ai", "model", 16, "예상 질문, 답변 평가, 면접 리포트와 연결되는 면접 준비 AI 축입니다.", [
    "텍스트 모의면접에서 시작해 음성/영상으로 확장됩니다.",
    "지원 건의 공고·전략 맥락을 활용합니다.",
    "비언어 분석은 참고자료로만 사용합니다.",
  ], ["interview-prep", "interview-session", "nonverbal-analysis"]),
  node("job-posting-worker", "공고 추출 Worker", "ai", "worker", 16, "공고문 텍스트와 파일에서 조건을 추출하는 B 영역 AI/문서 처리 축입니다.", [
    "필수/우대조건 추출과 공고 분석에 연결됩니다.",
    "실제 validation 데이터와 워커 산출물은 공개 범위를 분리해 봅니다.",
    "지원 건 분석 흐름의 초기 품질을 좌우합니다.",
  ], ["job-posting", "pdf-image-upload", "analysis-run"]),
  node("nonverbal-analysis", "비언어 분석", "ai", "model", 15, "표정, 시선, 자세 등 면접 태도 개선 참고자료를 다루는 확장 AI 기능입니다.", [
    "합격/불합격 판단 근거로 쓰지 않습니다.",
    "면접 리포트의 보조 피드백으로 연결됩니다.",
    "개인·민감 데이터 처리 정책이 중요합니다.",
  ], ["interview-prep", "interview-llm", "mobile-policy"]),
  node("json-schema", "JSON Schema 검증", "ai", "quality", 16, "AI 응답을 구조화하고 실패 시 fallback 판단에 쓰는 품질 관리 장치입니다.", [
    "도메인별 prompt builder와 validator가 함께 필요합니다.",
    "잘못된 AI 응답이 제품 데이터로 들어오는 것을 줄입니다.",
    "공통 프롬프트 구조 변경은 합의 대상입니다.",
  ], ["fallback", "provider-dispatcher", "analysis-run", "common-areas"]),
  node("sse", "SSE 진행 표시", "ai", "ux", 14, "자동 준비 파이프라인의 단계별 진행 상태를 스트리밍하는 사용자 경험입니다.", [
    "긴 AI 작업의 불확실성을 줄입니다.",
    "SecurityConfig의 async/error dispatcher 허용 같은 공통 설정과 연결됩니다.",
    "작업 화면 시안과 실제 AutoPrepWorkView가 맞물립니다.",
  ], ["ai-orchestrator", "react-spa", "security"]),
  node("mock-provider", "Rule/Mock Provider", "ai", "runtime", 15, "실제 키나 모델 없이도 데모와 CI에서 결정적 결과를 제공하는 보조 provider입니다.", [
    "공개 데모의 안정성을 높입니다.",
    "키가 없을 때도 구조화 분석 체험을 제공합니다.",
    "실운영 provider와 선택 지점을 분리해야 합니다.",
  ], ["fallback", "mock-registry", "mock-demo-build"]),

  node("users-table", "users", "data", "table", 15, "회원 계정과 인증 흐름의 중심 테이블입니다.", [
    "프로필, 지원 건, 결제, 커뮤니티, 알림과 연결됩니다.",
    "공개 데모에는 실제 사용자 데이터가 들어가지 않습니다.",
    "JWT 인증과 관리자 권한 처리의 기반입니다.",
  ], ["jwt", "user-profile-version", "application-case-table"]),
  node("user-profile-version", "profile version", "data", "table", 16, "분석 재현성을 위해 사용자 프로필의 버전을 관리하는 데이터 축입니다.", [
    "분석 당시의 사용자 정보를 고정합니다.",
    "적합도 분석과 분석 실행 단위에 연결됩니다.",
    "사용자 수정과 AI 결과의 근거 추적에 필요합니다.",
  ], ["profile", "analysis-run", "fit-analysis-table"]),
  node("application-case-table", "application_case", "data", "table", 17, "지원 건의 기업, 직무, 상태, 보관/삭제 시각을 저장하는 핵심 테이블입니다.", [
    "DRAFT, ANALYZING, READY, APPLIED, CLOSED 진행 상태를 표현합니다.",
    "보관과 삭제는 archived_at, deleted_at으로 분리합니다.",
    "여러 분석 결과의 부모 역할을 합니다.",
  ], ["application-case", "soft-delete", "archive", "job-posting-revision"]),
  node("job-posting-revision", "job_posting revision", "data", "table", 15, "같은 공고의 수정 이력을 지원 건 안에서 관리하는 데이터 구조입니다.", [
    "서로 다른 공고 여러 개를 한 지원 건에 묶는 의미가 아닙니다.",
    "분석 재현성을 위해 revision을 분석 결과와 연결합니다.",
    "공고문 저장 API의 의미를 명확히 합니다.",
  ], ["job-posting", "analysis-run", "application-case-table"]),
  node("fit-analysis-table", "fit_analysis", "data", "table", 16, "적합도 분석 결과와 비교 매트릭스, 판단 JSON을 저장하는 데이터 축입니다.", [
    "최신 결과와 이력 조회 모두 고려합니다.",
    "관리자 상세에서 스냅샷과 판단 JSON을 확인할 수 있습니다.",
    "C 영역 AI 분석의 주요 저장 위치입니다.",
  ], ["fit-analysis", "json-columns", "admin-fit-analysis"]),
  node("interview-session", "interview_session", "data", "table", 15, "면접 세션, 질문, 답변, 리포트 흐름을 저장하는 데이터 축입니다.", [
    "지원 건과 연결된 면접 기록을 구성합니다.",
    "면접 질문과 답변은 리포트 생성의 근거가 됩니다.",
    "음성/영상 확장 시 원본 보관 정책이 필요합니다.",
  ], ["interview-prep", "interview-llm", "report"]),
  node("correction-request", "correction_request", "data", "table", 15, "첨삭 요청과 결과, 성공/실패 이력을 추적하는 데이터 축입니다.", [
    "지원 건 기반 첨삭 기록과 독립 첨삭 화면 모두 연결됩니다.",
    "실패 로그는 ai_usage_log와 별도 관점에서 확인합니다.",
    "관리자 첨삭 화면의 조회 대상입니다.",
  ], ["correction", "correction-llm", "admin-correction"]),
  node("ai-usage-log", "ai_usage_log", "data", "table", 16, "AI 사용량, 실패, fallback, 비용 추적의 운영 로그입니다.", [
    "크레딧/결제와 운영 대시보드에 연결됩니다.",
    "도메인별 AI 실패 로그를 관리자 화면에서 확인할 수 있습니다.",
    "모델 provider와 fallback 결과 추적에 필요합니다.",
  ], ["fallback", "credit-payment", "admin-dashboard", "analysis-run"]),
  node("soft-delete", "소프트 삭제", "data", "policy", 14, "지원 건 삭제를 실제 row 삭제가 아니라 deleted_at 기록으로 처리하는 정책입니다.", [
    "일반 목록과 보관함에서는 삭제된 지원 건을 표시하지 않습니다.",
    "복원 API와 운영 추적을 가능하게 합니다.",
    "데이터 보존 정책과 사용자 기대를 맞춰야 합니다.",
  ], ["application-case-table", "archive", "spring-api"]),
  node("archive", "보관함", "data", "policy", 14, "지원 건을 완료·보류 상태로 숨기되 삭제하지 않는 archived_at 기반 정책입니다.", [
    "진행 상태와 보관 여부를 분리합니다.",
    "ACTIVE, ARCHIVED, DELETED view 조회와 연결됩니다.",
    "사용자가 지원 건을 장기적으로 관리할 수 있게 합니다.",
  ], ["application-case-table", "soft-delete", "dashboard"]),
  node("json-columns", "JSON 컬럼", "data", "storage", 14, "스킬, 자격증, 추천, 판단 세부 결과 같은 구조 데이터를 MySQL JSON으로 저장하는 방식입니다.", [
    "MVP에서 TypeHandler 없이 빠르게 구조화 결과를 붙이는 결정입니다.",
    "프런트/백엔드의 구조화 조작이 많아지면 별도 테이블 전환을 검토할 수 있습니다.",
    "AI 결과 schema와 맞물립니다.",
  ], ["mysql", "fit-analysis-table", "json-schema"]),

  node("release-matrix", "릴리즈 매트릭스", "release", "overview", 20, "웹 데모, Android, iOS와 Qt desktop 산출물의 source·실행·live gate를 함께 관리하는 릴리즈 축입니다.", [
    "발표/심사 전 산출물 종류와 실행 경로를 먼저 확인합니다.",
    "태그 기반 모바일/데스크톱 release workflow와 연결됩니다.",
    "공개 데모는 Pages에, 앱 산출물은 GitHub Release에 올리는 구조입니다.",
  ], ["demo-pages", "android-apk", "ios-build", "desktop-zip", "installer", "portable-exe", "cross-platform-integration"]),
  node("demo-pages", "GitHub Pages Demo", "release", "web", 18, "CareerTunerPortfolios 공개 repo에서 정적 mock 또는 AWS-first 장애 demo를 체험하는 웹 채널입니다.", [
    "정상 상태에서는 AWS API가 먼저이며 실제 readiness 장애에서만 read-only mock으로 전환합니다.",
    "이 Obsidian 공개 지식맵도 /Obsidian/ 하위에 보존됩니다.",
    "SPA fallback과 base path 설정이 필요합니다.",
  ], ["mock-demo-build", "secret-scan", "vite", "public-knowledge-map", "outage-demo-fallback"]),
  node("mock-demo-build", "Mock 데모 빌드", "release", "demo", 18, "VITE_USE_MOCK=true 기반으로 서버 없이 웹 데모와 APK를 자체완결로 실행하는 빌드입니다.", [
    "로그인에 아무 값이나 입력하면 데모 계정으로 진입합니다.",
    "등록되지 않은 endpoint는 데모 미제공 안내로 처리합니다.",
    "공개 산출물에 실제 백엔드 secret이 들어가지 않게 합니다.",
  ], ["mock-registry", "demo-pages", "android-apk", "secret-scan"]),
  node("secret-scan", "민감정보 스캔", "release", "security", 16, "공개 dist에 API 키, DB 비밀번호, 내부 주소가 들어가는지 배포 전에 검사하는 안전장치입니다.", [
    "정규식 기반 obvious secret 패턴을 확인합니다.",
    "키 값은 환경변수로만 주입하고 공개 repo에 남기지 않습니다.",
    "공개 지식맵도 동일한 관점으로 검토합니다.",
  ], ["demo-pages", "openai-provider", "public-knowledge-map"]),
  node("android-apk", "Android APK", "release", "mobile", 16, "mock demo build를 Capacitor로 패키징한 Android 설치 산출물입니다.", [
    "태그 release workflow로 APK를 만들 수 있습니다.",
    "BlueStacks 등에서 설치 테스트가 가능합니다.",
    "웹 데모와 같은 mock 데이터 흐름을 공유합니다.",
  ], ["capacitor", "mock-demo-build", "mobile-build-doc"]),
  node("ios-build", "iOS Build", "release", "mobile", 15, "Mac/Xcode 환경에서 Capacitor iOS 프로젝트를 동기화하고 배포하는 확장 경로입니다.", [
    "동일한 React dist를 기반으로 합니다.",
    "서명과 App Store 정책은 별도 고려가 필요합니다.",
    "PWA와 Android APK 이후의 플랫폼 확장입니다.",
  ], ["capacitor", "mobile-build-doc", "release-matrix"]),
  node("desktop-zip", "Desktop Zip", "release", "desktop", 15, "현재 가장 단순한 데스크톱 배포 형태인 압축파일 산출물입니다.", [
    "NSIS가 없어도 생성 가능한 기본 산출물입니다.",
    "설치 없이 압축 해제 후 실행하는 사용자를 대상으로 합니다.",
    "설치본/포터블 exe와 릴리즈 노트에서 차이를 명확히 적습니다.",
  ], ["desktop-readme", "installer", "portable-exe"]),
  node("installer", "설치본", "release", "desktop", 15, "NSIS 기반 설치형 데스크톱 배포 산출물입니다.", [
    "시작 메뉴, 설치 경로, 제거 흐름 같은 일반 설치 경험을 제공합니다.",
    "portable exe와 데이터 저장 위치 충돌을 피해야 합니다.",
    "NSIS makensis가 필요합니다.",
  ], ["desktop-readme", "portable-exe", "release-matrix"]),
  node("portable-exe", "포터블 단일 exe", "release", "desktop", 16, "다운로드 파일 1개, 설치 없음 경험을 목표로 하는 데스크톱 배포 형태입니다.", [
    "Qt DLL/plugin을 순수 정적 단일 exe로 묶는 방식은 현재 kit에서는 현실적이지 않습니다.",
    "대신 exe가 런타임을 풀고 실행하며 exe 옆 데이터 폴더를 우선 사용합니다.",
    "읽기 전용 위치에서는 OS 사용자 데이터 경로 폴백을 사용합니다.",
  ], ["desktop-readme", "installer", "release-matrix"]),
  node("github-actions", "GitHub Actions", "release", "ci", 17, "웹 데모, Android, iOS, desktop, backend/frontend CI를 자동화하는 workflow 축입니다.", [
    "데모 배포는 CareerTunerPortfolios repo의 Pages 산출물을 갱신합니다.",
    "모바일/데스크톱은 태그 기반 release 흐름과 연결됩니다.",
    "PR 체크 실패는 로그 기반으로 원인을 확인합니다.",
  ], ["frontend-ci", "backend-ci", "demo-pages", "android-apk"]),
  node("frontend-ci", "Frontend CI", "release", "ci", 15, "TypeScript, 빌드, 정적 검증 등 프런트 변경의 기본 확인 흐름입니다.", [
    "타입체크 실패는 공개 데모 배포 전에도 원인 파악이 필요합니다.",
    "Vite build와 PWA asset 검증이 연결됩니다.",
    "mock 데이터 registry 변경도 타입 안정성이 중요합니다.",
  ], ["typescript", "vite", "github-actions"]),
  node("backend-ci", "Backend Test", "release", "ci", 15, "Spring Boot 테스트와 서비스 파이프라인 체크의 백엔드 검증 축입니다.", [
    "mapper, service, convention test를 통해 공통 계약을 고정합니다.",
    "AI provider primary 중복 같은 구조 오류도 테스트로 막습니다.",
    "PR 체크 실패 시 로그를 기준으로 수정합니다.",
  ], ["spring-api", "java21", "github-actions"]),

  node("ownership", "기능 소유권", "governance", "team", 18, "기능별 수직 분담과 공통 영역 변경 기준을 문서화해 충돌을 줄이는 협업 규칙입니다.", [
    "각 담당자는 사용자 프런트, 사용자 백엔드, 어드민 프런트, 어드민 백엔드를 함께 봅니다.",
    "공통 영역 변경은 팀장 승인 또는 팀 합의가 필요합니다.",
    "작업 브랜치에서 dev로 PR을 보내는 흐름을 기준으로 합니다.",
  ], ["feature-modules", "common-areas", "dev-pr-flow", "admin-completion"]),
  node("dev-pr-flow", "dev PR 흐름", "governance", "git", 16, "개인 브랜치에서 작업하고 dev로 PR을 보내는 기본 협업 흐름입니다.", [
    "dev, main, master, live에 직접 커밋하지 않는 규칙을 둡니다.",
    "PR 본문과 커밋 메시지는 실제 변경 범위만 설명합니다.",
    "원격 브랜치 fast-forward와 submodule pointer는 별도로 확인합니다.",
  ], ["protected-branches", "submodule-pointer", "ownership"]),
  node("protected-branches", "보호 브랜치", "governance", "git", 14, "dev, main, master, live 같은 주요 브랜치의 직접 push를 제한하는 운영 기준입니다.", [
    "합의된 예외가 아니라면 PR을 거칩니다.",
    "릴리즈와 배포 기준 브랜치의 안정성을 높입니다.",
    "원격 HEAD 동기화 작업과 구분해 봐야 합니다.",
  ], ["dev-pr-flow", "github-actions", "release-matrix"]),
  node("common-areas", "공통 영역", "governance", "risk", 17, "routes, schema, common, 인증, 공통 API, provider, prompt/log 구조처럼 영향이 큰 변경 영역입니다.", [
    "수정 전 영향 범위와 소유권을 확인합니다.",
    "단순 오타와 명백한 문서 오류는 예외가 될 수 있습니다.",
    "AI provider와 DB 구조 변경은 특히 충돌 위험이 큽니다.",
  ], ["api-response", "security", "provider-dispatcher", "mysql"]),
  node("admin-completion", "관리자 완료 기준", "governance", "quality", 16, "사용자 기능 완료 시 관련 관리자 화면과 관리자 API도 같은 릴리스 기준에 포함하는 규칙입니다.", [
    "운영자가 결과를 조회, 검색, 메모, 상태 변경할 수 있어야 합니다.",
    "분석/첨삭/지원 건/크레딧 등은 관리자 관점이 필수입니다.",
    "제품 완성도를 심사에서 설득하는 근거가 됩니다.",
  ], ["admin-ui", "admin-dashboard", "admin-fit-analysis", "admin-correction"]),
  node("submodule-pointer", "서브모듈 포인터", "governance", "repo", 15, "별도 repo에서 먼저 commit/push한 뒤 본체에서 pointer를 갱신하는 서브모듈 운용 방식입니다.", [
    "AI docs, AI artifacts, storyboard, Obsidian vault가 submodule 경계로 관리됩니다.",
    "원본 repo 변경과 본체 pointer 변경은 별도 커밋입니다.",
    "git submodule update --remote는 기본 최신화 명령이 아닙니다.",
  ], ["private-vault", "ai-reports", "ai-artifacts", "storyboard"]),
  node("ai-reports", "AI Reports", "governance", "repo", 15, "사람이 읽는 장문 실험 보고서와 누적 해석을 보관하는 AI 문서 submodule입니다.", [
    "긴 보고서는 본체 repo에 직접 누적하지 않습니다.",
    "C 영역 등 담당별 실험 해석을 area별로 모읍니다.",
    "공개본에는 경계 개념만 요약합니다.",
  ], ["ai-artifacts", "lora-qlora", "submodule-pointer"]),
  node("ai-artifacts", "AI Artifacts", "governance", "repo", 15, "raw output, benchmark result, manifests, ops scripts를 보관하는 artifact submodule입니다.", [
    "반복 실행 산출물과 대형 파일을 본체에서 분리합니다.",
    "4090/Ollama 운영 문서와 실험 결과가 이 경계로 이동합니다.",
    "공개 Pages에는 raw output을 직접 노출하지 않습니다.",
  ], ["ai-reports", "ollama", "submodule-pointer"]),
  node("storyboard", "Storyboard", "governance", "repo", 14, "담당자별 UI/UX, PPTX, PDF, DB/클래스 설계서 산출물 submodule입니다.", [
    "일반 개발에는 선택 다운로드 대상입니다.",
    "심사 자료와 산출물 재생성 파이프라인을 분리합니다.",
    "메인 repo 용량과 개발 속도를 보호합니다.",
  ], ["submodule-pointer", "product-structure", "release-matrix"]),

  node("architecture-doc", "ARCHITECTURE.md", "docs", "doc", 15, "시스템 경계, API, 데이터 모델, 기술 스택, 모바일/PWA 로드맵의 기준 문서입니다.", [
    "현재 구현과 목표 구조를 함께 다룰 수 있습니다.",
    "런타임 소스와 README가 현재 구현 상태를 설명합니다.",
    "공개 지식맵의 기술 키워드 대부분이 이 문서에서 출발합니다.",
  ], ["spring-api", "react-spa", "application-case", "ai-orchestrator"]),
  node("product-structure", "PRODUCT_STRUCTURE.md", "docs", "doc", 15, "사용자 관점 메뉴와 기능 구조를 공유하는 제품 정보 구조 문서입니다.", [
    "지원 건 중심 제품 판단을 명시합니다.",
    "공개, 인증, 사용자 메인, 관리자 영역을 구분합니다.",
    "기능 메뉴와 개발 모듈 구조가 완전히 같지는 않다는 점을 설명합니다.",
  ], ["application-case", "dashboard", "admin-ui"]),
  node("feature-structure-doc", "FEATURE_MODULE_STRUCTURE.md", "docs", "doc", 14, "기능별 폴더 구조와 충돌 주의 파일의 기준 문서입니다.", [
    "사용자/관리자 프런트와 백엔드 경로를 표준화합니다.",
    "담당별 자체 LLM provider와 prompt 경계를 함께 설명합니다.",
    "공통 영역 변경 판단의 실무 기준입니다.",
  ], ["feature-modules", "ownership", "common-areas"]),
  node("release-doc", "RELEASE.md", "docs", "doc", 14, "웹 데모, Android APK, iOS 릴리즈 절차와 mock 데이터 범위를 정리한 문서입니다.", [
    "데모 배포와 모바일 산출물 생성 절차를 설명합니다.",
    "공개 GitHub Pages 주소와 태그 릴리즈 흐름을 연결합니다.",
    "릴리즈 전 체크 포인트를 제공합니다.",
  ], ["release-matrix", "demo-pages", "android-apk"]),
  node("mobile-build-doc", "MOBILE_BUILD.md", "docs", "doc", 14, "PWA, Android, iOS 빌드와 테스트 방법을 정리한 모바일 문서입니다.", [
    "한 React 코드베이스와 dist를 여러 플랫폼이 공유합니다.",
    "mock demo APK, Capacitor sync, iOS sync 흐름을 설명합니다.",
    "모바일 정책과 설치 경험을 함께 다룹니다.",
  ], ["pwa", "capacitor", "android-apk", "ios-build"]),
  node("desktop-readme", "desktop README", "docs", "doc", 14, "데스크톱 앱 실행, zip/installer/portable 산출물 차이를 설명하는 문서입니다.", [
    "포터블 exe의 현실적 구현 방식과 데이터 폴더 정책을 기록합니다.",
    "설치본과 압축본의 사용자 경험 차이를 정리합니다.",
    "릴리즈 노트 작성의 기준이 됩니다.",
  ], ["desktop-zip", "installer", "portable-exe"]),
  node("obsidian-overlay", "Obsidian Overlay", "docs", "doc", 16, "정본 문서를 복사하지 않고 읽기 순서와 판단 맥락을 관리하는 overlay vault입니다.", [
    "전체 graph가 난잡하면 curated map을 먼저 사용합니다.",
    "결정 로그, modules, templates, web dashboard로 확장됩니다.",
    "비공개 원본과 공개 Pages 추출본을 분리했습니다.",
  ], ["public-knowledge-map", "private-vault", "decision-log"]),
  node("decision-log", "Decision Log", "docs", "doc", 14, "왜 그런 방향을 택했는지 축약해서 남기는 결정 기록입니다.", [
    "PR별 주요 판단을 나중에 재구성할 수 있게 합니다.",
    "문서 충돌이나 설계 변경의 이유를 보존합니다.",
    "공개 지식맵에서는 민감한 내부 판단을 제외하고 역할만 설명합니다.",
  ], ["obsidian-overlay", "ownership", "architecture-doc"]),
  node("mobile-policy", "모바일 정책", "docs", "policy", 14, "PWA 캐시, 민감 데이터, 반응형 화면 설계, 앱 패키징 원칙을 묶는 정책 축입니다.", [
    "개인·민감 데이터 장기 캐시를 피합니다.",
    "지원 건 작업공간을 모바일에서 1열과 Drawer로 접습니다.",
    "PWA, Android, iOS 전략을 단계적으로 둡니다.",
  ], ["pwa", "responsive-layout", "nonverbal-analysis", "mobile-build-doc"]),
  node("file-domain", "file 도메인", "docs", "domain", 13, "이력서, 포트폴리오, 공고 파일 같은 업로드 자료와 연결될 수 있는 파일 관리 도메인입니다.", [
    "원본 삭제 시 추가 분석과 재분석 가능 여부가 달라질 수 있습니다.",
    "PDF/이미지 공고 입력과도 연결됩니다.",
    "공개 데모에는 실사용 파일을 포함하지 않습니다.",
  ], ["pdf-image-upload", "profile", "mobile-policy"]),
  node("admin-dashboard", "관리자 대시보드", "docs", "admin", 13, "회원, 지원 건, 분석, 면접, AI 현황을 운영 관점에서 요약하는 관리자 랜딩입니다.", [
    "사용자 dashboard의 운영 짝입니다.",
    "처리 필요 작업과 실패 로그를 빠르게 보여줍니다.",
    "관리자 완료 기준을 설득하는 화면입니다.",
  ], ["admin-ui", "ai-usage-log", "admin-completion"]),
  node("admin-fit-analysis", "관리자 적합도", "docs", "admin", 13, "적합도 분석 목록, 상세, 스냅샷, 판단 JSON, 재분석 요청을 다루는 관리자 기능입니다.", [
    "C 영역 분석 결과의 운영 검토 표면입니다.",
    "사용자 기능과 같은 릴리스 기준으로 봅니다.",
    "분석 신뢰도와 메모 관리에 연결됩니다.",
  ], ["fit-analysis", "fit-analysis-table", "admin-completion"]),
  node("admin-correction", "관리자 첨삭", "docs", "admin", 13, "첨삭 성공 이력, 실패 로그, 메모 현황을 검색·집계하는 관리자 기능입니다.", [
    "성공 결과와 실패 로그 데이터 소스가 다를 수 있습니다.",
    "E 영역 첨삭 운영 상태를 확인합니다.",
    "관리자 기능 완료 기준의 대표 예입니다.",
  ], ["correction", "correction-request", "admin-completion"]),
  node("admin-permission-boundary", "29-code 관리자 권한", "backend", "security", 20, "8개 domain의 CRUD/READ 권한을 route, menu, action과 backend declaration에 함께 적용합니다.", [
    "7개 CRUD domain 28개와 AUDIT_READ를 합쳐 29개 code입니다.",
    "일반 ADMIN은 ADMIN_PERMISSION 계열을 위임받지 못하고 SUPER_ADMIN만 우회합니다.",
    "익명·일반 회원·조회 실패·선언 누락을 fail-closed합니다.",
  ], ["admin-ui", "security", "admin-completion", "demo-readiness-ledger"]),
  node("firebase-phone-trust", "Firebase 전화 인증", "backend", "identity", 17, "Client SMS/reCAPTCHA와 backend ID-token 검증을 분리한 phone identity 경계입니다.", [
    "Frontend는 named app lazy init과 E.164 정규화를 사용합니다.",
    "Backend는 revoked token과 phone_number claim을 검증합니다.",
    "공개 web config는 service-account secret이나 live 준비 증거가 아닙니다.",
  ], ["security", "native-auth-handoff", "demo-readiness-ledger"]),
  node("native-auth-handoff", "Native OAuth Handoff", "frontend", "identity", 19, "Capacitor OAuth를 PKCE와 one-time hashed handoff로 app에 되돌리는 경계입니다.", [
    "Verifier는 app storage에 10분, handoff code는 server에서 3분만 유효합니다.",
    "Token과 verifier 원문은 handoff table에 저장하지 않습니다.",
    "공식 provider HTTPS URL과 exact verified App/Universal Link만 허용합니다.",
  ], ["security", "capacitor", "firebase-phone-trust", "cross-platform-integration"]),
  node("lifecycle-integrity", "수명주기 무결성", "data", "integrity", 20, "Profile snapshot, 탈퇴 비식별화, soft delete, idempotency와 고아 data 방지를 교차 도메인에서 연결합니다.", [
    "Interview operation key와 client submission ID가 AI 사용량·평가·정산 중복을 막습니다.",
    "Voice/nonverbal result를 session·question·answer에 연결합니다.",
    "Notification destination은 ALL/WEB/MOBILE/DESKTOP으로 분리합니다.",
  ], ["user-profile-version", "application-case", "soft-delete", "interview-session", "correction-request", "cross-platform-integration"]),
  node("outage-demo-fallback", "AWS-first 장애 데모", "release", "resilience", 20, "정상 시 AWS API를 먼저 쓰고 readiness가 확인된 장애에서만 read-only mock으로 전환합니다.", [
    "Network/502/503/504만 장애 후보이며 upstream readiness와 DB DOWN을 추가 확인합니다.",
    "Constraint·bad SQL·application bug는 500으로 남겨 mock 성공으로 숨기지 않습니다.",
    "Outage mode는 저장되지 않음을 표시하고 OAuth·결제를 차단한 뒤 복구 시 reload합니다.",
  ], ["demo-pages", "mock-registry", "demo-readiness-ledger"]),
  node("model-evidence", "A-F 모델 증거", "ai", "verification", 21, "Fine-tuning, self-hosted integration, PoC와 미검증 provenance를 영역별 artifact 수준으로 구분합니다.", [
    "A~E는 서로 다른 LoRA/QLoRA·multimodal evidence와 남은 artifact gap이 있습니다.",
    "F careertuner-mod는 provenance가 없어 검증된 fine-tune 성과에서 제외합니다.",
    "C/D/E Qwen2.5-3B는 상업 배포 전 license gate가 필요합니다.",
  ], ["lora-qlora", "career-strategy-llm", "correction-llm", "interview-llm", "ai-reports", "demo-readiness-ledger"]),
  node("cross-platform-integration", "3개 플랫폼 연동", "release", "verification", 21, "Web·Android·iOS·Qt desktop의 공통 API와 platform별 인증·알림·handoff gate를 분리합니다.", [
    "Android signed App Link와 Qt package smoke는 PR #395 실행 원장에 PASS가 있습니다.",
    "iOS는 unsigned source/CI와 signed-device Universal Link gate를 구분합니다.",
    "Server-side prep job/device persistence는 미구현으로 명시합니다.",
  ], ["release-matrix", "android-apk", "ios-build", "desktop-zip", "native-auth-handoff", "lifecycle-integrity", "demo-readiness-ledger"]),
  node("demo-readiness-ledger", "시연 준비 원장", "governance", "verification", 22, "Source review, 실행 증거, targeted delta와 외부 live gate를 서로 다른 기준으로 기록합니다.", [
    "최신 source d00a57fc, synthesis 2c4b11a9, synthesis vault merge 114b6d91, latest projection merge 248e082b를 구분합니다.",
    "Full execution evidence는 PR #395의 30a5511a이며 최신 head 전체 rerun으로 과장하지 않습니다.",
    "PR #408 관리자 문구와 PR #409 community desktop 폭은 다음 candidate의 targeted UI gate입니다.",
  ], ["release-matrix", "admin-permission-boundary", "firebase-phone-trust", "outage-demo-fallback", "model-evidence", "cross-platform-integration"]),
];

const pinnedPositions = {
  "career-tuner": [640, 410],
  "public-knowledge-map": [640, 285],
  "private-vault": [640, 535],
  "application-case": [250, 170],
  "spring-api": [885, 175],
  "react-spa": [390, 665],
  "ai-orchestrator": [1025, 395],
  "release-matrix": [1110, 650],
  "ownership": [640, 105],
  "architecture-doc": [595, 720],
};

const edgeList = new Map();
nodes.forEach((item) => {
  item.links.forEach((target) => addEdge(item.id, target, item.radius >= 18 ? "strong" : "normal"));
});

const edges = [...edgeList.values()];
applyLayout();

const state = {
  area: "all",
  query: "",
  selected: nodes[0],
};

const graph = document.getElementById("graph");
const cards = document.getElementById("cards");
const filters = document.getElementById("areaFilters");
const searchInput = document.getElementById("searchInput");
const detailArea = document.getElementById("detailArea");
const detailTitle = document.getElementById("detailTitle");
const detailBody = document.getElementById("detailBody");
const detailPoints = document.getElementById("detailPoints");
const resultCount = document.getElementById("resultCount");
const selectionStatus = document.getElementById("selectionStatus");
const neighborList = document.getElementById("neighborList");
const zoomOutButton = document.getElementById("zoomOutButton");
const zoomInButton = document.getElementById("zoomInButton");
const fitButton = document.getElementById("fitButton");
const focusButton = document.getElementById("focusButton");
const graphViewBox = { width: 1360, height: 860 };
const graphBounds = computeGraphBounds();
const graphPan = {
  minScale: 0.62,
  maxScale: 2.4,
  scale: 0.86,
  x: 0,
  y: 0,
  dragging: false,
  dragStart: null,
  pointerId: null,
};

document.getElementById("metricNodes").textContent = String(nodes.length);
document.getElementById("metricEdges").textContent = String(edges.length);
document.getElementById("metricAreas").textContent = String(Object.keys(areas).length);

function node(id, title, area, kind, radius, summary, points, links) {
  return { id, title, area, kind, radius, summary, points, links };
}

function addEdge(from, to, strength = "normal") {
  if (!getNode(from) || !getNode(to) || from === to) return;
  const key = [from, to].sort().join("__");
  if (!edgeList.has(key)) edgeList.set(key, { from, to, strength });
}

function applyLayout() {
  Object.entries(pinnedPositions).forEach(([id, [x, y]]) => {
    const item = getNode(id);
    if (item) {
      item.x = x;
      item.y = y;
      item.pinned = true;
    }
  });

  Object.keys(areas).forEach((areaKey) => {
    const bucket = nodes.filter((item) => item.area === areaKey && !item.pinned);
    const area = areas[areaKey];
    bucket.forEach((item, index) => {
      const angle = (-Math.PI / 2) + (index / Math.max(bucket.length, 1)) * Math.PI * 2;
      const ring = 74 + (index % 3) * 33 + Math.floor(index / 10) * 18;
      item.x = Math.round(area.cx + Math.cos(angle) * ring);
      item.y = Math.round(area.cy + Math.sin(angle) * ring);
    });
  });
}

function getNode(id) {
  return nodes.find((item) => item.id === id);
}

function getNeighbors(id) {
  const ids = new Set();
  edges.forEach((edge) => {
    if (edge.from === id) ids.add(edge.to);
    if (edge.to === id) ids.add(edge.from);
  });
  return [...ids].map(getNode).filter(Boolean);
}

function matches(item) {
  const areaMatch = state.area === "all" || item.area === state.area;
  const terms = [
    item.title,
    item.kind,
    areas[item.area].label,
    item.summary,
    ...item.points,
    ...getNeighbors(item.id).map((neighbor) => neighbor.title),
  ].join(" ").toLowerCase();
  return areaMatch && terms.includes(state.query.trim().toLowerCase());
}

function isRelatedToSelection(item) {
  if (!state.selected) return false;
  return item.id === state.selected.id || getNeighbors(state.selected.id).some((neighbor) => neighbor.id === item.id);
}

function edgeTouchesVisible(edge) {
  const from = getNode(edge.from);
  const to = getNode(edge.to);
  return matches(from) || matches(to);
}

function edgeTouchesSelection(edge) {
  return edge.from === state.selected.id || edge.to === state.selected.id;
}

function selectNode(item) {
  state.selected = item;
  selectionStatus.textContent = `선택: ${item.title}`;
  detailArea.textContent = `${areas[item.area].label} · ${item.kind}`;
  detailTitle.textContent = item.title;
  detailBody.textContent = item.summary;
  detailPoints.textContent = "";
  item.points.forEach((point) => {
    const li = document.createElement("li");
    li.textContent = point;
    detailPoints.append(li);
  });
  renderNeighbors();
  render();
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function computeGraphBounds() {
  const padding = 70;
  return nodes.reduce((bounds, item) => ({
    minX: Math.min(bounds.minX, item.x - item.radius - padding),
    maxX: Math.max(bounds.maxX, item.x + item.radius + padding),
    minY: Math.min(bounds.minY, item.y - item.radius - padding),
    maxY: Math.max(bounds.maxY, item.y + item.radius + padding),
  }), {
    minX: Infinity,
    maxX: -Infinity,
    minY: Infinity,
    maxY: -Infinity,
  });
}

function getPanRange(axis) {
  const isX = axis === "x";
  const viewportSize = isX ? graphViewBox.width : graphViewBox.height;
  const minBound = isX ? graphBounds.minX : graphBounds.minY;
  const maxBound = isX ? graphBounds.maxX : graphBounds.maxY;
  const margin = 120;
  let min = viewportSize - maxBound * graphPan.scale - margin;
  let max = margin - minBound * graphPan.scale;

  if (min > max) {
    const center = (viewportSize - (minBound + maxBound) * graphPan.scale) / 2;
    min = center - margin;
    max = center + margin;
  }

  return { min, max };
}

function clampPan() {
  graphPan.scale = clamp(graphPan.scale, graphPan.minScale, graphPan.maxScale);
  const rangeX = getPanRange("x");
  const rangeY = getPanRange("y");
  graphPan.x = clamp(graphPan.x, rangeX.min, rangeX.max);
  graphPan.y = clamp(graphPan.y, rangeY.min, rangeY.max);
}

function getGraphPoint(event) {
  const rect = graph.getBoundingClientRect();
  return {
    x: ((event.clientX - rect.left) / rect.width) * graphViewBox.width,
    y: ((event.clientY - rect.top) / rect.height) * graphViewBox.height,
  };
}

function zoomGraph(factor, origin = { x: graphViewBox.width / 2, y: graphViewBox.height / 2 }) {
  const previousScale = graphPan.scale;
  const nextScale = clamp(previousScale * factor, graphPan.minScale, graphPan.maxScale);
  const ratio = nextScale / previousScale;
  graphPan.x = origin.x - ratio * (origin.x - graphPan.x);
  graphPan.y = origin.y - ratio * (origin.y - graphPan.y);
  graphPan.scale = nextScale;
  clampPan();
  updateGraphTransform();
}

function resetGraphView() {
  graphPan.scale = 0.86;
  graphPan.x = 0;
  graphPan.y = 0;
  clampPan();
  updateGraphTransform();
}

function focusSelectedNode() {
  if (!state.selected) return;
  graphPan.scale = Math.max(graphPan.scale, 1.08);
  graphPan.x = (graphViewBox.width / 2 - state.selected.x * graphPan.scale);
  graphPan.y = (graphViewBox.height / 2 - state.selected.y * graphPan.scale);
  clampPan();
  updateGraphTransform();
}

function updateGraphTransform() {
  const viewport = graph.querySelector(".graph-viewport");
  if (!viewport) return;
  viewport.setAttribute("transform", `translate(${graphPan.x} ${graphPan.y}) scale(${graphPan.scale})`);
}

function renderFilters() {
  const all = document.createElement("button");
  all.type = "button";
  all.className = "segment active";
  all.dataset.area = "all";
  all.textContent = "All";
  filters.append(all);

  Object.entries(areas).forEach(([key, area]) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "segment";
    button.dataset.area = key;
    button.textContent = area.label;
    filters.append(button);
  });

  filters.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-area]");
    if (!button) return;
    state.area = button.dataset.area;
    filters.querySelectorAll(".segment").forEach((item) => item.classList.toggle("active", item === button));
    render();
  });
}

function renderGraph() {
  graph.textContent = "";
  const fragment = document.createDocumentFragment();
  const viewport = document.createElementNS("http://www.w3.org/2000/svg", "g");
  viewport.classList.add("graph-viewport");

  edges.forEach((edge) => {
    const from = getNode(edge.from);
    const to = getNode(edge.to);
    const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
    line.setAttribute("x1", from.x);
    line.setAttribute("y1", from.y);
    line.setAttribute("x2", to.x);
    line.setAttribute("y2", to.y);
    line.setAttribute("stroke", edge.strength === "strong" ? "#7b8fa7" : "#66788e");
    line.setAttribute("vector-effect", "non-scaling-stroke");
    line.classList.add("edge", edge.strength);
    if (edgeTouchesSelection(edge)) line.classList.add("selected");
    if (!edgeTouchesVisible(edge)) line.classList.add("dimmed");
    viewport.append(line);
  });

  nodes.forEach((item) => {
    const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
    group.classList.add("node", `node-${item.kind}`);
    if (item.id === state.selected.id) group.classList.add("selected");
    if (isRelatedToSelection(item)) group.classList.add("related");
    if (!matches(item)) group.classList.add("dimmed");
    group.setAttribute("transform", `translate(${item.x}, ${item.y})`);
    group.setAttribute("tabindex", "0");
    group.setAttribute("role", "button");
    group.setAttribute("aria-label", item.title);
    group.setAttribute("aria-pressed", String(item.id === state.selected.id));
    group.addEventListener("pointerdown", (event) => event.stopPropagation());
    group.addEventListener("click", (event) => {
      event.stopPropagation();
      selectNode(item);
    });
    group.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        selectNode(item);
      }
    });

    const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    circle.setAttribute("r", item.radius);
    circle.setAttribute("fill", areas[item.area].color);

    const title = document.createElementNS("http://www.w3.org/2000/svg", "text");
    title.setAttribute("text-anchor", "middle");
    title.setAttribute("y", item.radius + 15);
    title.textContent = item.title;

    const area = document.createElementNS("http://www.w3.org/2000/svg", "text");
    area.setAttribute("text-anchor", "middle");
    area.setAttribute("y", item.radius + 29);
    area.classList.add("sub");
    area.textContent = areas[item.area].label;

    if (shouldShowLabel(item)) {
      group.append(circle, title, area);
    } else {
      group.append(circle);
    }
    viewport.append(group);
  });

  fragment.append(viewport);
  graph.append(fragment);
  updateGraphTransform();
}

function shouldShowLabel(item) {
  if (item.id === state.selected.id || isRelatedToSelection(item)) return true;
  if (!matches(item)) return false;
  return item.radius >= 17 || state.query.trim().length > 0 || state.area !== "all";
}

function renderCards() {
  cards.textContent = "";
  const visible = nodes.filter(matches);
  resultCount.textContent = `${visible.length} visible`;

  if (!visible.length) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = "검색 조건과 일치하는 공개 키워드가 없습니다.";
    cards.append(empty);
    return;
  }

  visible
    .sort((a, b) => b.radius - a.radius || a.title.localeCompare(b.title, "ko"))
    .forEach((item) => {
      const card = document.createElement("article");
      card.className = "card";
      card.tabIndex = 0;
      const heading = document.createElement("h3");
      heading.textContent = item.title;
      const body = document.createElement("p");
      body.textContent = item.summary;
      const meta = document.createElement("div");
      meta.className = "card-meta";
      const tag = document.createElement("span");
      tag.className = "tag";
      tag.textContent = areas[item.area].label;
      const degree = document.createElement("span");
      degree.className = "degree";
      degree.textContent = `${getNeighbors(item.id).length} links`;
      meta.append(tag, degree);
      card.append(heading, body, meta);
      card.addEventListener("click", () => selectNode(item));
      card.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          selectNode(item);
        }
      });
      cards.append(card);
    });
}

function renderNeighbors() {
  neighborList.textContent = "";
  const neighbors = getNeighbors(state.selected.id).sort((a, b) => areas[a.area].label.localeCompare(areas[b.area].label, "ko"));
  neighbors.forEach((neighbor) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "neighbor";
    button.innerHTML = `<span>${neighbor.title}</span><small>${areas[neighbor.area].label}</small>`;
    button.addEventListener("click", () => selectNode(neighbor));
    neighborList.append(button);
  });
}

function render() {
  renderGraph();
  renderCards();
}

searchInput.addEventListener("input", (event) => {
  state.query = event.target.value;
  render();
});

graph.addEventListener("pointerdown", (event) => {
  if (event.button !== 0 || event.target.closest?.(".node")) return;
  graphPan.dragging = true;
  graphPan.pointerId = event.pointerId;
  graphPan.dragStart = {
    x: event.clientX,
    y: event.clientY,
    panX: graphPan.x,
    panY: graphPan.y,
  };
  graph.classList.add("panning");
  graph.setPointerCapture(event.pointerId);
});

graph.addEventListener("pointermove", (event) => {
  if (!graphPan.dragging || graphPan.pointerId !== event.pointerId) return;
  const rect = graph.getBoundingClientRect();
  const dx = ((event.clientX - graphPan.dragStart.x) / rect.width) * graphViewBox.width;
  const dy = ((event.clientY - graphPan.dragStart.y) / rect.height) * graphViewBox.height;
  graphPan.x = graphPan.dragStart.panX + dx;
  graphPan.y = graphPan.dragStart.panY + dy;
  clampPan();
  updateGraphTransform();
});

function stopPanning(event) {
  if (graphPan.pointerId !== event.pointerId) return;
  graphPan.dragging = false;
  graphPan.pointerId = null;
  graphPan.dragStart = null;
  graph.classList.remove("panning");
  if (graph.hasPointerCapture(event.pointerId)) graph.releasePointerCapture(event.pointerId);
}

graph.addEventListener("pointerup", stopPanning);
graph.addEventListener("pointercancel", stopPanning);

graph.addEventListener("wheel", (event) => {
  event.preventDefault();
  const factor = event.deltaY < 0 ? 1.12 : 0.89;
  zoomGraph(factor, getGraphPoint(event));
}, { passive: false });

zoomOutButton.addEventListener("click", () => zoomGraph(0.86));
zoomInButton.addEventListener("click", () => zoomGraph(1.16));
fitButton.addEventListener("click", resetGraphView);
focusButton.addEventListener("click", focusSelectedNode);

renderFilters();
selectNode(nodes[0]);
