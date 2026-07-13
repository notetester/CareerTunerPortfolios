window.SECOND_BRAIN_GRAPH = (() => {
  const groups = {
    hub: { label: "Hub", color: "#4fd1c5", cx: 640, cy: 390 },
    product: { label: "Product", color: "#f2c14e", cx: 250, cy: 190 },
    backend: { label: "Backend", color: "#5eead4", cx: 960, cy: 190 },
    frontend: { label: "Frontend", color: "#79a7ff", cx: 330, cy: 585 },
    admin: { label: "Admin", color: "#60a5fa", cx: 560, cy: 665 },
    ai: { label: "AI", color: "#b794f4", cx: 815, cy: 575 },
    ml: { label: "ML", color: "#c084fc", cx: 1045, cy: 620 },
    data: { label: "Data", color: "#8bd17c", cx: 1090, cy: 390 },
    release: { label: "Release", color: "#fb7185", cx: 690, cy: 130 },
    docs: { label: "Docs", color: "#a3e635", cx: 455, cy: 115 },
    wiki: { label: "Wiki", color: "#f59e0b", cx: 135, cy: 410 },
    ops: { label: "Ops", color: "#f97316", cx: 850, cy: 740 },
  };

  const nodes = [
    n("career-tuner", "CareerTuner", "hub", "portfolio-root", 36, "채용공고, 지원자 스펙, AI 분석, 면접, 첨삭, 릴리즈 산출물을 하나의 지원 건 중심으로 묶은 AI 취업 전략 플랫폼입니다.", ["지원 건이 제품·DB·AI 파이프라인의 중심 단위입니다.", "백엔드, 프런트, 관리자, ML 실험, 데이터 수명주기와 다중 플랫폼 배포가 같은 graph 안에서 연결됩니다.", "공개 projection은 credential과 개인 데이터를 제외하고 source 기준·실행 증거·미완료 gate를 분리합니다."], "README.md", ["application-case", "spring-api", "react-spa", "ai-orchestrator", "graphify-extract", "obsidian-wiki", "web-demo", "demo-readiness-ledger"]),
    n("portfolio-graph", "Portfolio Knowledge Graph", "hub", "public-view", 30, "CareerTunerPortfolio에 공개하는 포트폴리오용 wiki/Graphify 시각화입니다.", ["단순 개념도가 아니라 실제 문서·코드 경로·Graphify 추출 수치를 함께 보여줍니다.", "검색, 그룹 필터, 상세 패널, wiki/code 카드로 리뷰어가 구조를 따라갈 수 있습니다.", "민감값은 제외하지만 기술 구조와 구현 범위는 적극적으로 공개합니다."], "Obsidian/SecondBrain", ["career-tuner", "graphify-extract", "wiki-index", "architecture-doc", "code-map"]),
    n("code-map", "Code Map", "hub", "curated-graph", 26, "Graphify code-only AST 결과를 사람이 읽기 좋은 포트폴리오 graph로 압축한 지도입니다.", ["원본 graph.json은 75MB 규모라 public demo에는 요약 graph만 싣습니다.", "절대경로와 환경값을 제거하고 repo-relative 경로와 집계 수치를 사용합니다.", "실제 추출 수치와 curated node를 같이 노출해 과장 없이 강점을 보여줍니다."], "Obsidian/SecondBrain/graph-data.js", ["graphify-extract", "backend-graph", "frontend-graph", "ml-graph"]),

    n("application-case", "지원 건", "product", "core-domain", 28, "CareerTuner의 핵심 업무 단위입니다. 한 기업·직무·공고 조합에 공고 분석, 기업 분석, 적합도, 면접, 첨삭 기록이 모입니다.", ["공고 여러 개를 묶는 단위가 아니라 같은 공고 revision과 분석 이력을 관리합니다.", "archived_at/deleted_at으로 보관과 삭제를 분리합니다.", "사용자 화면과 관리자 운영 화면의 기준이 됩니다."], "docs/ARCHITECTURE.md", ["applicationcase-api", "job-posting", "analysis-run", "soft-delete", "dashboard"]),
    n("job-posting", "공고문/Revision", "product", "input", 20, "텍스트, URL, PDF, 이미지 공고를 지원 건에 붙이고 revision으로 관리합니다.", ["텍스트 저장, 파일 추출, 실패 재시도, 검수 확정 흐름이 있습니다.", "공고 분석과 기업 분석의 출발점입니다.", "worker와 백엔드 API가 함께 다루는 범위입니다."], "backend/src/main/java/com/careertuner/applicationcase/service/ApplicationCaseExtractionWorker.java", ["job-posting-worker", "jobanalysis-api", "applicationcase-api"]),
    n("company-analysis", "기업 분석", "product", "analysis", 20, "공고와 웹 근거를 바탕으로 기업 현황과 출처 메타데이터를 분리해 다루는 분석 영역입니다.", ["검색 호출은 플래그로 게이트됩니다.", "근거 URL, 확인 시점, 재조회 권장 시점을 저장할 수 있습니다.", "관리자 화면에서 출처 메타데이터 보정이 가능합니다."], "backend/src/main/java/com/careertuner/companyanalysis", ["web-search", "evidence-gate", "admin-application"]),
    n("fit-analysis", "적합도 분석", "product", "analysis", 26, "C 영역 핵심 기능입니다. 요구조건과 사용자 스펙을 비교해 점수, 신뢰도, 지원 판단, 액션 보드를 만듭니다.", ["Evidence gate와 skill alias normalizer로 근거 없는 매칭을 줄입니다.", "사용자 상세 화면과 관리자 검토 화면이 짝으로 존재합니다.", "학습 과제와 톤별 전략까지 연결됩니다."], "backend/src/main/java/com/careertuner/fitanalysis/service/FitAnalysisServiceImpl.java", ["evidence-gate", "career-strategy-llm", "fit-analysis-ui", "admin-fit-analysis", "fit-analysis-table"]),
    n("strategy", "지원 전략", "product", "output", 18, "공고와 사용자 역량 차이를 바탕으로 지원 방향, 어필 포인트, 보완 우선순위를 제안합니다.", ["Career Strategy LLM과 연결됩니다.", "학습 과제, 예상 질문, 액션 보드로 이어집니다."], "ml/career-strategy-llm/README.md", ["career-strategy-llm", "learning-task", "interview-prep"]),
    n("learning-task", "학습 과제", "product", "output", 16, "부족 역량을 보완하기 위한 학습·자격증·프로젝트 과제를 지원 건 안에 생성합니다.", ["대시보드와 액션 보드에서 완료 상태를 추적합니다.", "분석 결과를 실제 행동으로 바꾸는 장치입니다."], "backend/src/main/java/com/careertuner/fitanalysis/dto/FitAnalysisLearningTaskResponse.java", ["fit-analysis", "dashboard"]),
    n("interview-prep", "AI 면접", "product", "feature", 21, "지원 건의 공고·스펙·전략을 바탕으로 예상 질문과 모의면접 흐름을 제공합니다.", ["텍스트 면접, 답변 평가, 향후 음성/비언어 분석으로 확장됩니다.", "면접 agent orchestrator와 자체 fine-tune 실험이 연결됩니다."], "backend/src/main/java/com/careertuner/interview/service/InterviewAgentOrchestrator.java", ["interview-finetune", "nonverbal", "interview-ui"]),
    n("correction", "AI 첨삭", "product", "feature", 20, "자기소개서와 답변 문장을 공고 요구사항과 사용자 맥락에 맞춰 개선하는 기능입니다.", ["E correction LLM과 백엔드 correction API가 연결됩니다.", "관리자 성공/실패 이력 화면이 운영 증거가 됩니다."], "ml/correction-llm/README.md", ["correction-llm", "correction-api", "admin-correction", "correction-ui"]),
    n("dashboard", "대시보드", "product", "screen", 18, "지원 현황, 최근 AI 분석, 부족 역량, 오늘의 할 일, 크레딧을 모아 보여주는 요약 화면입니다.", ["독립 도메인이 아니라 여러 도메인의 요약입니다.", "mock demo와 관리자 홈 모두에서 체감되는 화면입니다."], "frontend/src/app/pages/Dashboard.tsx", ["dashboard-ui", "career-analysis", "billing"]),
    n("career-analysis", "장기 취업 분석", "product", "analysis", 18, "여러 지원 건을 종합해 반복 강점, 약점, 직무별 준비도, 추천 방향을 도출합니다.", ["분석 fingerprint 캐시와 재생성 흐름이 있습니다.", "C 영역의 장기 포트폴리오 증거입니다."], "backend/src/main/resources/mapper/analysis", ["analysis-run", "admin-analytics", "career-strategy-llm"]),
    n("billing", "구독/크레딧", "product", "business", 18, "AI 기능 사용권, 크레딧, 환불 정책, 관리자 환불 처리를 관리합니다.", ["기능별 benefit policy와 actionKey 기반 차감 검증을 둡니다.", "포트폴리오에서는 수익화·운영 도메인 구현 범위를 보여줍니다."], "backend/src/main/java/com/careertuner/billing/service/BillingPolicyService.java", ["billing-api", "payment-credit", "admin-refunds"]),
    n("support", "고객센터/챗봇", "product", "support", 17, "FAQ, 공지, 문의, 챗봇, 미답변 질문을 관리하는 사용자/관리자 지원 도메인입니다.", ["프런트 기능 모듈과 백엔드 support mapper/controller가 함께 존재합니다.", "운영형 SaaS 포트폴리오에서 제품 완성도를 보여줍니다."], "frontend/src/features/support/pages/SupportHomePage.tsx", ["support-api", "support-ui", "admin-support"]),

    n("spring-api", "Spring API", "backend", "stack", 28, "Spring Boot 4.1, Java 21, MyBatis, MySQL 8 기반 REST API 서버입니다.", ["모든 컨트롤러는 /api/** 하위입니다.", "응답은 ApiResponse envelope를 사용합니다.", "Graphify backend 추출: 1,787 code files, 13,548 nodes, 47,689 edges."], "backend/README.md", ["api-response", "mybatis", "security-config", "auth-api", "applicationcase-api", "fitanalysis-api"]),
    n("api-response", "ApiResponse envelope", "backend", "contract", 16, "모든 REST 응답을 success/code/message/data 형식으로 감싸는 공통 계약입니다.", ["프런트와 관리자 화면이 같은 오류·성공 처리 구조를 공유합니다."], "backend/src/main/java/com/careertuner/common/web/ApiResponse.java", ["spring-api", "react-spa"]),
    n("security-config", "SecurityConfig", "backend", "security", 18, "JWT/stateless 인증, 공개 엔드포인트, CORS, SSE와 관리자 권한 interceptor 경계를 담당합니다.", ["일반 ADMIN의 선언되지 않은 관리자 API는 backend에서 fail-closed합니다.", "민감값은 환경변수로 주입하고 공개 데모에는 값 자체를 싣지 않습니다."], "backend/src/main/java/com/careertuner/common/config/SecurityConfig.java", ["auth-api", "spring-api", "admin-permission-boundary"]),
    n("mybatis", "MyBatis Mapper", "backend", "persistence", 22, "CareerTuner의 영속성 접근 방식입니다. JPA 없이 @Mapper 인터페이스와 XML mapper를 사용합니다.", ["SQL 흐름이 명시적이라 팀 분담과 리뷰에 유리합니다.", "도메인별 mapper XML과 Java mapper가 대응됩니다."], "backend/src/main/resources/mapper", ["schema", "fit-analysis-table", "application-case-table"]),
    n("auth-api", "Auth API", "backend", "api", 18, "회원가입, 로그인, refresh 회전, logout, me, 이메일·소셜·전화 인증을 담당합니다.", ["JWT access와 opaque refresh token을 분리합니다.", "Firebase ID token 검증과 native PKCE handoff는 browser callback과 별도 trust boundary로 관리합니다."], "backend/src/main/java/com/careertuner/auth/service/AuthServiceImpl.java", ["security-config", "users", "firebase-phone-trust", "native-auth-handoff"]),
    n("applicationcase-api", "Application Case API", "backend", "api", 24, "지원 건 생성, 목록, 상세, 수정, soft delete, 공고문 저장과 추출 상태를 다룹니다.", ["CareerTuner 핵심 작업공간 API입니다.", "공고문 추출 worker, B 분석, 관리자 상세와 연결됩니다."], "backend/src/main/java/com/careertuner/applicationcase/service/ApplicationCaseServiceImpl.java", ["application-case", "job-posting", "jobanalysis-api", "admin-application"]),
    n("jobanalysis-api", "Job/Company Analysis API", "backend", "api", 20, "공고 분석과 기업 분석 생성·조회·이력·검수 확정을 담당합니다.", ["자체 규칙, 로컬 LLM, 웹 근거, fallback 경로가 연결됩니다."], "backend/src/main/java/com/careertuner/applicationcase/service/BAnalysisGenerationService.java", ["company-analysis", "web-search", "ai-usage-log"]),
    n("fitanalysis-api", "Fit Analysis API", "backend", "api", 23, "지원 건별 적합도 분석 생성, 조회, 이력, 학습 과제 토글을 담당합니다.", ["C 영역의 가장 중요한 사용자 API입니다.", "관리자 분석 검토 API와 같은 데이터 모델을 공유합니다."], "backend/src/main/java/com/careertuner/fitanalysis/controller/FitAnalysisController.java", ["fit-analysis", "evidence-gate", "admin-fit-analysis"]),
    n("correction-api", "Correction API", "backend", "api", 17, "자기소개서/답변 첨삭 요청과 결과 이력을 다룹니다.", ["E correction LLM과 관리자 첨삭 로그가 연결됩니다."], "backend/src/main/resources/mapper/correction/CorrectionMapper.xml", ["correction", "correction-llm"]),
    n("billing-api", "Billing API", "backend", "api", 18, "구독 플랜, 사용권, 차감 미리보기, 환불 요청과 관리자 환불 처리를 담당합니다.", ["AI 기능의 비용·사용권·환불 고지를 제품화합니다."], "backend/src/main/java/com/careertuner/billing", ["billing", "admin-refunds", "payment-credit"]),
    n("support-api", "Support API", "backend", "api", 17, "공지, FAQ, 문의, 챗봇 FAQ, 미답변 질문 mapper와 controller를 포함합니다.", ["사용자 지원과 관리자 운영의 연결 지점입니다."], "backend/src/main/resources/mapper/support", ["support", "support-ui", "admin-support"]),
    n("jackson-convention", "Jackson 3 Convention", "backend", "guardrail", 16, "Spring Boot 4 환경에서 Jackson 3 ObjectMapper 사용 규칙을 테스트로 고정합니다.", ["직접 new ObjectMapper와 Jackson 2 import를 차단합니다."], "backend/src/test/java/com/careertuner/JacksonUsageConventionTests.java", ["spring-api"]),

    n("react-spa", "React SPA", "frontend", "stack", 27, "React 19.2.7, Vite 8.1.4, TypeScript 7.0.2, Tailwind v4 기반 사용자/관리자 반응형 SPA입니다.", ["Graphify frontend/src 추출: 571 code files, 4,448 nodes, 13,356 edges.", "사용자와 관리자 화면을 한 앱에서 관리합니다.", "mock demo, PWA, Capacitor 패키징이 같은 dist를 공유합니다."], "frontend/package.json", ["routes", "mock-registry", "admin-ui", "pwa-capacitor"]),
    n("routes", "Routes", "frontend", "routing", 18, "사용자 라우트와 관리자 라우트를 연결하는 SPA 진입 구조입니다.", ["라우팅은 공통 영향이 큰 영역입니다.", "공개 demo와 404 fallback에서도 중요합니다."], "frontend/src/app/routes.ts", ["react-spa", "admin-ui"]),
    n("app-layout", "App Layout", "frontend", "ui", 16, "Header, Footer, Root, 공통 UI primitive를 통해 제품 경험을 구성합니다.", ["Figma Make 초안을 점진적으로 기능 모듈 구조로 옮깁니다."], "frontend/src/app/components", ["react-spa", "responsive"]),
    n("mock-registry", "Mock Registry", "frontend", "demo", 22, "정적 체험용 mock과 장애 시 read-only fallback 데이터를 제공하는 API registry입니다.", ["정적 mock build와 AWS-first outage fallback은 서로 다른 mode입니다.", "일반 Sites build는 실제 API를 먼저 사용하고 readiness가 확인된 장애에서만 mock으로 전환합니다."], "frontend/src/app/lib/mock", ["web-demo", "android-apk", "dashboard-ui", "admin-ui", "outage-demo-fallback"]),
    n("dashboard-ui", "Dashboard UI", "frontend", "screen", 17, "지원 현황, 최근 분석, 할 일, 크레딧을 보여주는 사용자 요약 화면입니다.", ["mock demo에서 제품 첫 인상을 담당합니다."], "frontend/src/app/pages/Dashboard.tsx", ["dashboard", "mock-registry"]),
    n("fit-analysis-ui", "Fit Analysis UI", "frontend", "screen", 20, "적합도 분석 결과, 비교 매트릭스, 액션 보드, 톤별 전략을 보여주는 사용자 화면입니다.", ["C 영역 백엔드와 포트폴리오 핵심 흐름입니다."], "frontend/src/features/applications/types/analysis.ts", ["fit-analysis", "fitanalysis-api"]),
    n("interview-ui", "Interview UI", "frontend", "screen", 16, "AI 면접 시작, 진행, 결과 확인 사용자 흐름을 담당합니다.", ["향후 음성/비언어 분석 확장과 연결됩니다."], "frontend/src/app/pages", ["interview-prep", "interview-finetune"]),
    n("correction-ui", "Correction UI", "frontend", "screen", 16, "첨삭 기능을 지원 건 상세와 독립 첨삭 흐름에 연결합니다.", ["E correction LLM 결과를 사용자 경험으로 보여줍니다."], "frontend/src/app/pages", ["correction", "correction-api"]),
    n("support-ui", "Support UI", "frontend", "feature", 17, "고객센터, FAQ, 공지, 문의, 챗봇 페이지를 사용자 기능으로 제공합니다.", ["운영형 서비스의 완성도를 보여주는 기능군입니다."], "frontend/src/features/support/pages/SupportHomePage.tsx", ["support", "support-api"]),
    n("admin-ui", "Admin UI", "admin", "surface", 24, "같은 React 앱 안에서 /admin/** 라우트로 운영자 화면을 제공합니다.", ["29개 세부 권한에 따라 route, navigation과 mutation action을 숨기거나 차단합니다.", "익명·일반 회원과 권한 조회 실패는 fail-closed이고, 허용 전 route module을 lazy import하지 않습니다.", "AI 상담 운영 화면은 FAQ 임계값 초과 여부와 실제 상담 라우팅 사유를 구분해 표시합니다."], "frontend/src/admin/routes.ts", ["admin-application", "admin-fit-analysis", "admin-analytics", "admin-correction", "admin-refunds", "admin-support", "admin-permission-boundary"]),
    n("admin-application", "Admin Application Cases", "admin", "feature", 17, "지원 건 운영 목록·상세·상태 변경·B 분석 이력을 확인합니다.", ["사용자 지원 건 API의 운영 짝입니다."], "frontend/src/admin", ["applicationcase-api", "jobanalysis-api"]),
    n("admin-fit-analysis", "Admin Fit Analysis", "admin", "feature", 19, "적합도 분석 목록, 상세, 스냅샷, 품질 플래그, 운영 메모를 다룹니다.", ["C 영역 분석 품질을 운영자가 검토할 수 있습니다."], "backend/src/main/java/com/careertuner/admin", ["fit-analysis", "fitanalysis-api", "admin-analytics"]),
    n("admin-analytics", "Admin Analytics", "admin", "feature", 18, "장기 분석 실행 이력, 품질 큐, 사용자별 타임라인, 운영 메모를 다룹니다.", ["분석 결과를 운영 가능한 제품으로 만드는 영역입니다."], "backend/src/main/resources/mapper/analysis", ["career-analysis", "ai-usage-log"]),
    n("admin-correction", "Admin Correction", "admin", "feature", 16, "첨삭 성공 이력과 실패 로그를 운영자가 확인합니다.", ["E 영역 운영 가시성을 제공합니다."], "backend/src/main/resources/mapper/correction/CorrectionMapper.xml", ["correction", "correction-api"]),
    n("admin-refunds", "Admin Refunds", "admin", "feature", 16, "환불 요청의 자동 판정 근거와 승인/거절 흐름을 제공합니다.", ["구독제와 AI 사용권이 실제 운영 정책으로 연결됩니다."], "backend/src/main/java/com/careertuner/billing", ["billing", "billing-api"]),
    n("admin-support", "Admin Support", "admin", "feature", 16, "공지, FAQ, 문의, 챗봇 미답변 질문을 운영자가 관리합니다.", ["고객지원 도메인의 운영 표면입니다."], "backend/src/main/resources/mapper/support", ["support", "support-api"]),
    n("pwa-capacitor", "PWA + Capacitor", "frontend", "mobile", 19, "반응형 웹에서 PWA, Android/iOS 패키징으로 확장하는 모바일 전략입니다.", ["같은 React dist를 웹과 앱 산출물이 공유합니다.", "PKCE, 검증된 App/Universal Link, 플랫폼별 알림 destination을 native 경계로 둡니다."], "frontend/MOBILE_BUILD.md", ["android-apk", "ios-build", "mobile-native", "native-auth-handoff", "cross-platform-integration"]),
    n("mobile-native", "Native Bridge", "frontend", "mobile", 16, "Capacitor 기반 카메라, 푸시, 딥링크, 앱잠금, haptics 같은 플랫폼 기능을 캡슐화합니다.", ["웹과 앱의 경계를 platform 모듈로 분리합니다.", "Native OAuth callback은 exact host/path와 one-time handoff code를 검증합니다."], "frontend/src/platform", ["pwa-capacitor", "native-auth-handoff"]),
    n("responsive", "Responsive UX", "frontend", "ux", 16, "3열 작업공간을 모바일에서 1열, drawer, 접이식 카드로 접는 UX 원칙입니다.", ["포트폴리오에서 모바일 대응성을 설명하는 근거입니다."], "docs/planning/모바일 고려.md", ["react-spa", "pwa-capacitor"]),

    n("ai-orchestrator", "AI Orchestrator", "ai", "pipeline", 26, "사용자 요청을 인테이크 대화, planner, 병렬 도메인 호출, SSE 진행 상황으로 자동 준비하는 AI 파이프라인입니다.", ["프로필, 공고, 적합도, 자소서, 면접, 커뮤니티 영역을 동적으로 실행합니다.", "fallback과 skip으로 일부 미완이어도 완주하는 구조입니다."], "docs/AI_ORCHESTRATOR.md", ["autoprep", "provider-dispatcher", "prompt-templates", "ai-usage-log"]),
    n("autoprep", "Auto Prep", "ai", "workflow", 19, "한 줄 요청에서 부족 정보를 수집하고 필요한 파트를 선택해 자동 준비를 실행합니다.", ["백엔드 ai/autoprep와 프런트 features/autoprep가 연결됩니다."], "backend/src/main/java/com/careertuner/ai/autoprep", ["ai-orchestrator", "react-spa"]),
    n("provider-dispatcher", "Provider Dispatcher", "ai", "runtime", 20, "자체 모델, Claude, OpenAI, 규칙/mock fallback 우선순위를 한 지점에서 선택하는 원칙입니다.", ["여러 @Primary 구현체 충돌을 방지합니다.", "AI provider 변경 시 호출부 영향도를 낮춥니다."], "docs/ARCHITECTURE.md", ["openai-provider", "ollama-provider", "ai-usage-log"]),
    n("evidence-gate", "Evidence Gate", "ai", "quality", 22, "근거 없는 스킬 매칭과 환각을 줄이기 위한 검증 계층입니다.", ["fit analysis에서 user evidence와 job evidence를 구분합니다.", "C 영역 RAG/grounding 실험 보고서와 연결됩니다."], "backend/src/main/java/com/careertuner/fitanalysis/service/EvidenceGateService.java", ["fit-analysis", "career-strategy-llm", "evaluation-reports"]),
    n("openai-provider", "OpenAI Provider", "ai", "provider", 16, "구조화 분석과 요약을 위한 외부 provider 경로입니다.", ["키 값은 GitHub Secrets 또는 환경변수에서 주입하고 공개 파일에는 싣지 않습니다."], "backend/src/main/java/com/careertuner/applicationcase/service/OpenAiResponsesClient.java", ["provider-dispatcher"]),
    n("ollama-provider", "Ollama Local LLM", "ai", "provider", 18, "공유 4090 또는 로컬 fallback Ollama를 사용하는 자체 모델 실행 경로입니다.", ["B 분석, F 검열, 담당별 모델 실험과 연결됩니다.", "서버 주소 값은 공개 demo에 노출하지 않습니다."], "docs/ENVIRONMENTS.md", ["provider-dispatcher", "4090-ops"]),
    n("web-search", "Company Web Search", "ai", "evidence", 16, "기업 분석용 외부 검색 근거 수집 경계입니다.", ["기본 비활성 플래그로 게이트합니다.", "검색 키 값은 환경변수로만 주입합니다."], "backend/src/main/java/com/careertuner/companyanalysis", ["company-analysis", "evidence-gate"]),
    n("prompt-templates", "Prompt Templates", "ai", "prompt", 17, "도메인별 system prompt와 strictness prompt를 resources에 분리합니다.", ["프롬프트도 코드처럼 버전 관리되는 포트폴리오 자산입니다."], "backend/src/main/resources/prompts", ["ai-orchestrator", "correction"]),
    n("ai-usage-log", "AI Usage Log", "ai", "ops", 17, "AI 호출 성공/실패, 비용, fallback 상태를 운영 관점에서 추적합니다.", ["관리자 화면과 크레딧 차감 정책의 기반입니다."], "backend/src/main/resources/mapper/ai", ["billing", "admin-analytics"]),

    n("career-strategy-llm", "Career Strategy LLM", "ml", "model-area", 24, "C 영역 Qwen2.5-3B QLoRA/평가 실험입니다. 지원 전략과 적합도 분석 품질 개선의 핵심 근거입니다.", ["Rank 8/16/32와 runtime parameter·F16/Q4 trade-off를 같은 contract gate로 비교합니다.", "현재 단순 RAG는 비활성이고 evidence gate와 semantic judge를 별도 검증합니다."], "ml/career-strategy-llm/README.md", ["fit-analysis", "strategy", "evaluation-reports", "model-evidence"]),
    n("correction-llm", "Correction LLM", "ml", "model-area", 20, "E 영역 Delivery-s QLoRA 첨삭 모델 산출물입니다. 자기소개서/답변 문장 개선 기능과 연결됩니다.", ["F16 gate 증거와 최신 F16/Q4 비교 실패를 분리해 기록합니다.", "완주하지 못한 비교 run에는 성능 수치를 만들지 않습니다."], "ml/correction-llm/README.md", ["correction", "admin-correction", "model-evidence"]),
    n("interview-finetune", "Interview Fine-tune", "ml", "model-area", 18, "D 영역 Qwen2.5-3B text QLoRA와 voice/visual 모델을 분리한 면접 평가 실험입니다.", ["Golden20의 F16/Q4 MAE와 latency trade-off를 기록합니다.", "LightGBM nonverbal과 faster-whisper는 LLM과 다른 모델 계열로 관리합니다."], "ml/interview-finetune/README.md", ["interview-prep", "model-evidence"]),
    n("nonverbal", "Nonverbal Interview", "ml", "model-area", 17, "표정·시선·자세 분석을 면접 태도 개선 참고자료로 다루는 ML 영역입니다.", ["합격/불합격 판단이 아니라 피드백 보조로 제한합니다."], "ml/interview-nonverbal/README.md", ["interview-prep"]),
    n("job-posting-worker", "Job Posting Worker", "ml", "worker", 21, "PDF/이미지/문서 텍스트 추출과 공고문 처리 안정화를 담당하는 Python worker입니다.", ["Graphify ML 추출에도 많은 테스트와 scripts가 잡힙니다.", "OCR runtime smoke, Docker smoke, release readiness 검사가 있습니다."], "ml/job-posting-worker/README.md", ["job-posting", "jobanalysis-api"]),
    n("qlora-profile", "Profile QLoRA Training", "ml", "training", 16, "A 영역 Qwen3-4B Profile LoRA v4 학습 실험입니다.", ["3,000개 학습 data와 0.8145% trainable parameter 증거를 기록합니다.", "Artifact는 저장소 밖이고 runtime 기본 OFF라 재현·운영 완료로 과장하지 않습니다."], "docs/ai-training/README.md", ["evaluation-reports", "model-evidence"]),
    n("4090-ops", "4090 Ops", "ops", "infrastructure", 18, "공유 GPU, Ollama, OpenSSH, Tailscale 운영 스크립트와 상태 문서를 관리합니다.", ["AI 실험과 로컬 LLM provider를 실제 운영 환경으로 연결합니다."], privateEvidence("private evidence (not published): 4090 operations status"), ["ollama-provider", "evaluation-reports"]),
    n("evaluation-reports", "AI Reports", "ml", "evidence", 20, "C career strategy와 기타 AI 실험의 반복 평가 보고서를 submodule에 축적합니다.", ["장문 보고서는 main repo가 아니라 ai-reports submodule에 둡니다.", "포트폴리오에서는 모델 개선 과정을 보여주는 강점입니다."], privateEvidence("private evidence (not published): AI evaluation reports"), ["career-strategy-llm", "ai-boundaries"]),
    n("ml-graph", "Graphify ML Extract", "ml", "graphify-scope", 18, "Graphify code-only 추출 결과: ml 204 files, 2,016 nodes, 4,830 edges.", ["calls 1,531, contains 1,048, imports 621, references 539를 확인했습니다."], "Obsidian/SecondBrain/graph-data.js", ["graphify-extract", "career-strategy-llm", "job-posting-worker"]),

    n("schema", "DB Schema", "data", "database", 24, "users, application_case, analysis_run, fit_analysis, payment, credit, consent와 플랫폼 handoff를 포함한 핵심 데이터 모델입니다.", ["지원 건 중심의 1:N 분석 이력과 immutable profile snapshot을 허용합니다.", "탈퇴·soft delete·idempotency·derived media relation을 patch와 mapper predicate로 함께 관리합니다."], "backend/src/main/resources/db/schema.sql", ["users", "application-case-table", "analysis-run", "fit-analysis-table", "payment-credit", "consent", "lifecycle-integrity"]),
    n("users", "Users/Profile", "data", "table-family", 16, "회원, 프로필, 프로필 버전을 공고 분석 비교 기준으로 사용합니다.", ["인증, 지원 건, AI 분석의 기준 엔터티입니다."], "backend/src/main/resources/mapper/user", ["auth-api", "schema"]),
    n("application-case-table", "application_case", "data", "table-family", 20, "기업·직무·공고 조합의 작업공간을 저장합니다.", ["보관과 삭제는 별도 시각 컬럼으로 관리합니다."], "docs/ARCHITECTURE.md", ["application-case", "soft-delete", "job-posting-revision"]),
    n("job-posting-revision", "job_posting revision", "data", "table-family", 17, "같은 지원 건의 공고문 수정 이력을 저장합니다.", ["분석 재현성과 사용자 검수 흐름에 필요합니다."], "docs/ARCHITECTURE.md", ["job-posting", "analysis-run"]),
    n("analysis-run", "analysis_run", "data", "table-family", 18, "어떤 프로필 버전과 공고 revision으로 분석했는지 묶는 재현성 단위입니다.", ["fit/job/company/career analysis 결과를 같은 맥락으로 연결합니다."], "backend/src/main/resources/mapper/analysis", ["career-analysis", "fit-analysis-table"]),
    n("fit-analysis-table", "fit_analysis", "data", "table-family", 19, "적합도 분석 결과, condition match, 학습 과제, 히스토리를 저장합니다.", ["C 영역 사용자/관리자 기능의 핵심 테이블군입니다."], "backend/src/main/resources/mapper/analysis", ["fit-analysis", "admin-fit-analysis"]),
    n("payment-credit", "Payment/Credit", "data", "table-family", 17, "구독, 결제, 크레딧, 사용권, 환불 정책을 저장합니다.", ["AI 기능 비용 구조를 데이터로 관리합니다."], "backend/src/main/resources/mapper/credit", ["billing", "admin-refunds"]),
    n("consent", "Consent/Legal", "data", "table-family", 15, "약관/동의 이력을 버전별로 저장합니다.", ["AI 데이터 사용 동의와 개인정보 처리 흐름의 근거입니다."], "backend/src/main/resources/mapper/consent", ["support", "schema"]),
    n("soft-delete", "Archive/Delete Policy", "data", "policy", 16, "지원 건과 C/D/E/F 도메인의 보관·삭제·재활성화를 분리하는 데이터 정책입니다.", ["사용자 삭제는 개인정보를 비식별화하면서 공개 content와 audit FK graph를 보존합니다.", "Relation/reaction은 동일 row 재활성화와 active predicate로 counter를 대사합니다."], "docs/ARCHITECTURE.md", ["application-case", "application-case-table", "lifecycle-integrity"]),

    n("web-demo", "GitHub Pages Web Demo", "release", "channel", 22, "정적 mock 체험과 AWS-first 장애 fallback을 구분해 공개하는 웹 데모입니다.", ["정상 상태에서는 AWS API가 우선이며 network/502/503/504와 readiness DOWN이 함께 확인될 때만 read-only mock으로 전환합니다.", "이번 SecondBrain 화면도 같은 공개 경로에서 제공됩니다."], "docs/RELEASE.md", ["pages-deploy", "mock-registry", "portfolio-graph", "outage-demo-fallback"]),
    n("android-apk", "Android APK", "release", "channel", 20, "동일 React build를 Capacitor Android 앱으로 패키징하는 release channel입니다.", ["PR #395 기준 emulator와 live-signed verified App Link가 PASS했습니다.", "최신 frontend candidate는 변경 UI targeted smoke를 별도 수행해야 합니다."], "frontend/MOBILE_BUILD.md", ["pwa-capacitor", "github-actions", "cross-platform-integration"]),
    n("ios-build", "iOS Build", "release", "channel", 16, "macOS/Xcode에서 Capacitor iOS project와 Universal Link contract를 검증하는 channel입니다.", ["Unsigned source/CI build 경로는 확인됐습니다.", "Team ID와 signed device에서 두 exact Universal Link를 검증하는 live gate가 남아 있습니다."], "frontend/MOBILE_BUILD.md", ["pwa-capacitor", "github-actions", "cross-platform-integration"]),
    n("desktop-release", "Desktop Release", "release", "channel", 16, "Qt desktop의 zip, setup, portable 실행 파일과 web handoff를 제공하는 channel입니다.", ["PR #395에서 Release/CTest/package/login/theme/8 handoff를 확인했습니다.", "Server-side prep job/device persistence는 미구현 gate로 명시합니다."], "desktop/README.md", ["github-actions", "cross-platform-integration"]),
    n("github-actions", "GitHub Actions", "release", "automation", 18, "CI, demo deploy, Android release, iOS build, Pages deployment를 자동화합니다.", ["태그/브랜치 기반 산출물 배포의 근거입니다."], ".github/workflows", ["web-demo", "android-apk", "ios-build", "pages-deploy"]),
    n("pages-deploy", "CareerTunerPortfolio Deploy", "release", "automation", 18, "정화된 공개 소스에서 Pages 산출물을 빌드하고 배포합니다.", ["기존 Obsidian/ 경로를 보존하도록 배포 workflow가 정리돼 있습니다."], ".github/workflows/pages.yml", ["web-demo", "portfolio-graph"]),

    n("admin-permission-boundary", "Admin Permission Boundary", "admin", "authorization", 27, "8개 domain의 29개 권한 code를 route·menu·action과 backend declaration에 함께 적용하는 관리자 권한 경계입니다.", ["USER, SECURITY, BILLING, CONTENT, AI, POLICY, ADMIN_PERMISSION은 CRUD이고 AUDIT은 READ-only입니다.", "일반 ADMIN은 ADMIN_PERMISSION 권한을 위임받을 수 없고 SUPER_ADMIN만 granular permission을 우회합니다.", "권한 조회 실패, 선언 누락과 mock mutation도 fail-closed합니다."], "backend/src/main/java/com/careertuner/admin/permission/catalog/AdminPermissionCatalog.java", ["admin-ui", "security-config", "demo-readiness-ledger"]),
    n("firebase-phone-trust", "Firebase Phone Trust", "backend", "identity", 22, "Client SMS/reCAPTCHA와 server-side Firebase ID-token 검증을 분리한 전화 인증 경계입니다.", ["Frontend는 named app을 lazy initialize하고 한국 번호를 E.164로 정규화합니다.", "Backend Admin SDK는 revoked token과 phone_number claim을 검증합니다.", "공개 web config는 service-account secret과 다른 범주이며 실제 provider 준비 상태를 증명하지 않습니다."], "backend/src/main/java/com/careertuner/sms/FirebaseAuthClient.java", ["auth-api", "native-auth-handoff", "demo-readiness-ledger"]),
    n("native-auth-handoff", "Native OAuth Handoff", "frontend", "identity", 24, "Capacitor social login을 PKCE와 one-time hashed handoff code로 browser OAuth에서 app으로 안전하게 되돌리는 경계입니다.", ["64-byte verifier에서 SHA-256 challenge를 만들고 verifier는 app storage에 10분만 둡니다.", "Backend handoff는 code hash/challenge와 3분 expiry만 저장하며 token·verifier 원문을 남기지 않습니다.", "공식 provider HTTPS host/path와 exact verified App/Universal Link만 허용합니다."], "frontend/src/platform/nativeOAuthCore.mjs", ["auth-api", "mobile-native", "pwa-capacitor", "cross-platform-integration"]),
    n("lifecycle-integrity", "Lifecycle Integrity", "data", "integrity", 26, "Profile snapshot, 탈퇴 비식별화, soft delete, idempotency와 derived media relation을 교차 도메인에서 일관되게 유지합니다.", ["AI operation key와 client submission ID가 model usage·평가·정산 중복을 막습니다.", "Interview media result는 session뿐 아니라 question/answer에 연결하고 orphan·kind 중복을 정리합니다.", "Notification destination은 ALL/WEB/MOBILE/DESKTOP으로 분리합니다."], "backend/src/main/resources/db/patches", ["schema", "soft-delete", "application-case", "cross-platform-integration", "demo-readiness-ledger"]),
    n("outage-demo-fallback", "AWS-first Outage Demo", "release", "resilience", 25, "정상 시 AWS API를 우선하고 실제 서비스 장애가 확인된 경우에만 read-only mock으로 전환하는 공개 demo 경계입니다.", ["Network error 또는 502/503/504만 장애 후보이며 backup health의 upstream readiness와 DB DOWN 확인이 추가로 필요합니다.", "DB connection/resource만 503이고 constraint·bad SQL·application bug는 500으로 남겨 결함을 mock 성공으로 숨기지 않습니다.", "Outage mode는 저장되지 않음을 표시하고 OAuth·결제를 차단하며 readiness 복구 뒤 real mode로 reload합니다."], "frontend/src/app/lib/outageFallback.ts", ["web-demo", "mock-registry", "demo-readiness-ledger"]),
    n("model-evidence", "A-F Model Evidence", "ml", "verification", 28, "A~F AI를 fine-tuning, 자체 hosting, PoC와 미검증 provenance로 구분해 실제 artifact와 gate 수준만 공개합니다.", ["A Profile LoRA, B extraction, C strategy QLoRA, D interview multimodal, E correction QLoRA, F self-hosted integration의 증거 수준이 서로 다릅니다.", "C/D/E Qwen2.5-3B는 상업 배포 전 license gate가 필요합니다.", "E 최신 F16/Q4 비교 실패와 F careertuner-mod provenance 누락을 성공 수치로 포장하지 않습니다."], "docs/AI_REPORT/CAREERTUNER_SELF_AI_MODEL_DEEP_DIVE.md", ["qlora-profile", "career-strategy-llm", "interview-finetune", "correction-llm", "evaluation-reports", "demo-readiness-ledger"]),
    n("cross-platform-integration", "Cross-platform Integration", "release", "verification", 27, "Web·Android·iOS·Qt desktop이 같은 API와 인증·알림·handoff 계약을 공유하면서 platform별 live gate를 분리합니다.", ["Android signed App Link와 Qt package smoke는 PR #395 실행 원장에 PASS가 있습니다.", "iOS는 unsigned source/CI 확인과 signed-device Universal Link 검증을 구분합니다.", "Mobile answer idempotency와 ALL/WEB/MOBILE/DESKTOP notification destination이 공통 data contract를 지킵니다."], "frontend/MOBILE_BUILD.md", ["android-apk", "ios-build", "desktop-release", "native-auth-handoff", "lifecycle-integrity", "demo-readiness-ledger"]),
    n("demo-readiness-ledger", "Demo Readiness Ledger", "docs", "verification", 30, "Source review, 과거 실행 증거, 최신 targeted delta와 외부 provider live gate를 한 PASS로 섞지 않는 시연 준비 원장입니다.", ["최신 제품 source는 d00a57fc, Obsidian synthesis baseline은 2c4b11a9, synthesis vault merge는 114b6d91, latest projection merge는 248e082b입니다.", "전체 실행 증거 baseline은 PR #395의 30a5511a이며 최신 head 전체 suite를 다시 실행했다고 주장하지 않습니다.", "PR #408은 AI 상담 공백 사유, PR #409는 커뮤니티 desktop 폭을 수정했으므로 두 path의 targeted UI smoke가 다음 candidate gate입니다."], "docs/verification/DEMO_READINESS_LEDGER.md", ["admin-permission-boundary", "firebase-phone-trust", "lifecycle-integrity", "outage-demo-fallback", "model-evidence", "cross-platform-integration", "graph-report"]),

    n("architecture-doc", "ARCHITECTURE.md", "docs", "canon", 22, "기술 스택, API, 데이터 모델, 시스템 경계의 정본 문서입니다.", ["이번 graph의 주요 code/domain 해석 기준입니다."], "docs/ARCHITECTURE.md", ["career-tuner", "spring-api", "schema"]),
    n("product-structure", "PRODUCT_STRUCTURE.md", "docs", "canon", 20, "사용자 관점 메뉴와 기능 구조를 정리한 문서입니다.", ["지원 건, 대시보드, AI 면접, 첨삭, 커뮤니티, 결제 구조를 설명합니다."], "docs/PRODUCT_STRUCTURE.md", ["application-case", "dashboard", "interview-prep"]),
    n("feature-module", "FEATURE_MODULE_STRUCTURE.md", "docs", "canon", 18, "frontend/backend/admin 기능 모듈 표준 경로와 충돌 주의 파일을 정의합니다.", ["팀 분담과 코드 graph 탐색의 기준입니다."], "docs/FEATURE_MODULE_STRUCTURE.md", ["react-spa", "spring-api", "admin-ui"]),
    n("team-ownership", "TEAM_WORK_DISTRIBUTION.md", "docs", "canon", 18, "6명 수직 분담과 담당 AI 기능, 주요 DB를 정리합니다.", ["공통 영역 변경 기준과 소유권을 확인합니다."], "docs/TEAM_WORK_DISTRIBUTION.md", ["admin-ui", "ai-orchestrator"]),
    n("planning-docs", "Planning Docs", "docs", "planning", 17, "기획, 디자인 분석, 모바일 고려, 자체 LLM 운영안을 포함한 목표 상태 문서군입니다.", ["제품 방향과 구현 현황을 구분해 읽습니다."], "docs/planning", ["product-structure", "responsive", "provider-dispatcher"]),
    n("release-doc", "RELEASE.md", "docs", "runbook", 17, "웹 데모, Android APK, iOS 빌드 산출물 생성과 배포 절차입니다.", ["포트폴리오 산출물을 실제로 배포하는 절차입니다."], "docs/RELEASE.md", ["web-demo", "android-apk", "ios-build"]),
    n("ai-boundaries", "AI Repository Boundaries", "docs", "boundary", 17, "AI reports/artifacts/Obsidian vault submodule의 저장 경계를 정의합니다.", ["main repo 오염을 줄이고 산출물을 성격별로 보관합니다."], "docs/AI_REPOSITORY_BOUNDARIES.md", ["evaluation-reports", "obsidian-wiki", "graphify-extract"]),

    n("obsidian-wiki", "Obsidian Wiki", "wiki", "vault", 26, "CareerTuner 작업 맥락을 Obsidian/LLM Wiki 방식으로 축적하는 submodule입니다.", ["정본 문서를 복사하지 않고 읽기 순서와 판단 맥락을 관리합니다.", "이번 공개 demo는 이 vault의 포트폴리오 projection입니다.", "원문 기반 20개 공개 문서가 architecture, concept, operation으로 연결됩니다."], "docs/obsidian-vault", ["wiki-index", "wiki-log", "raw-sources", "graph-report", "agent-ladder", "llm-wiki-architecture", "obsidian-workflow"]),
    n("wiki-index", "wiki/index.md", "wiki", "index", 20, "LLM Wiki의 content-oriented catalog입니다.", ["agent와 사람이 query 시작점으로 사용합니다.", "Category, page summary와 connection으로 후보 source를 좁힙니다."], "docs/obsidian-vault/wiki/index.md", ["obsidian-wiki", "agent-ladder", "wiki-search-tooling"]),
    n("wiki-log", "wiki/log.md", "wiki", "log", 18, "ingest, query, lint 이력을 일관된 heading으로 남기는 timeline입니다.", ["최근 지식 갱신 맥락을 빠르게 파악합니다.", "Source와 결과 page를 Git diff까지 추적합니다."], "docs/obsidian-vault/wiki/log.md", ["obsidian-wiki", "ingest-query-lint", "llm-wiki-ingest"]),
    n("raw-sources", "raw sources", "wiki", "source", 17, "LLM Wiki 원문과 Graphify/Obsidian 관련 외부 source index를 보관합니다.", ["원문과 해석을 분리합니다."], "docs/obsidian-vault/raw", ["llm-wiki", "source-index"]),
    n("llm-wiki", "LLM Wiki Pattern", "wiki", "concept", 23, "Raw와 중요한 query 결과를 persistent Markdown synthesis로 계속 갱신하는 지식관리 패턴입니다.", ["RAG처럼 매번 재발견하지 않고 비교, cross-link와 모순 검토를 축적합니다.", "사람은 source와 질문을 고르고 agent는 반복 bookkeeping을 맡습니다."], "docs/obsidian-vault/wiki/concepts/llm-wiki.md", ["raw-sources", "wiki-index", "ingest-query-lint", "llm-wiki-architecture", "compounding-knowledge"]),
    n("llm-wiki-architecture", "LLM Wiki Architecture", "wiki", "system", 21, "Query-time retrieval 위에 raw, wiki, schema 세 계층의 지속 synthesis를 둡니다.", ["Wiki는 source를 대체하지 않고 반복 분석 결과를 저장합니다.", "CareerTuner source와 정본 문서가 구현 사실의 최종 기준입니다."], "docs/obsidian-vault/wiki/systems/llm-wiki-architecture.md", ["llm-wiki", "raw-sources", "wiki-schema", "compounding-knowledge"]),
    n("wiki-schema", "Wiki Schema", "wiki", "governance", 19, "Agent의 page class, provenance, cross-link, ingest/query/lint 완료 조건을 정의합니다.", ["한 source의 영향을 여러 page에 통합합니다.", "Page 수가 아니라 근거와 재사용 가능한 연결을 우선합니다."], "docs/obsidian-vault/wiki/systems/wiki-schema.md", ["llm-wiki-architecture", "source-provenance", "llm-wiki-ingest", "llm-wiki-query", "llm-wiki-lint"]),
    n("source-provenance", "Source Provenance", "wiki", "policy", 17, "각 synthesis를 source_count와 source path로 raw 근거까지 추적합니다.", ["공개본에는 검토된 source label과 공개 URL만 표시합니다."], "docs/obsidian-vault/wiki/systems/wiki-schema.md", ["wiki-schema", "raw-sources", "public-export"]),
    n("compounding-knowledge", "Compounding Knowledge", "wiki", "concept", 20, "이미 발견한 연결과 비교를 다음 작업의 출발점으로 남겨 탐색 비용을 누적 절감합니다.", ["Chat에 머물던 결과를 canonical page로 승격합니다.", "Memex의 associative trail을 Wiki link와 graph edge로 구현합니다."], "docs/obsidian-vault/wiki/concepts/compounding-knowledge.md", ["llm-wiki", "llm-wiki-query", "human-review"]),
    n("graphify-extract", "Graphify Extract Result", "wiki", "graphify", 28, "실제 Graphify code-only AST 추출 결과입니다. 전체 repo 기준 2,870 code files, 26,886 nodes, 91,616 edges를 생성했습니다.", ["API 키 없이 AST 기반으로 실행했습니다.", "backend/frontend/ml 별도 추출 수치를 공개 화면에 반영했습니다.", "원본 graph의 절대경로는 공개하지 않고 공개 graph data 경로와 요약만 사용합니다."], "Obsidian/SecondBrain/graph-data.js", ["backend-graph", "frontend-graph", "ml-graph", "graph-report", "code-map"]),
    n("backend-graph", "Backend Graphify Scope", "wiki", "graphify-scope", 20, "backend 범위 code-only 추출: 1,787 files, 13,548 nodes, 47,689 edges.", ["calls 14,098, references 12,543, imports 12,217를 확인했습니다."], "Obsidian/SecondBrain/graph-data.js", ["spring-api", "mybatis"]),
    n("frontend-graph", "Frontend Graphify Scope", "wiki", "graphify-scope", 20, "frontend/src 범위 code-only 추출: 571 files, 4,448 nodes, 13,356 edges.", ["contains 3,821, imports 3,701, calls 2,829를 확인했습니다."], "Obsidian/SecondBrain/graph-data.js", ["react-spa", "mock-registry"]),
    n("graph-report", "GRAPH_REPORT.md", "wiki", "report", 18, "Graphify hub, bridge, surprising link, agent 시작 prompt를 요약한 report입니다.", ["실제 code-only 결과와 curated wiki report를 함께 사용합니다."], "docs/obsidian-vault/graphify-out/GRAPH_REPORT.md", ["graphify-extract", "agent-ladder"]),
    n("agent-ladder", "3-layer Query Rule", "wiki", "protocol", 22, "Graph query → Obsidian wiki search → raw/source reading 순서로 agent 탐색 비용을 줄입니다.", ["코드를 덜 읽는 것이 아니라 읽을 코드를 빠르게 좁히는 전략입니다."], "docs/obsidian-vault/wiki/systems/agent-memory-protocol.md", ["wiki-index", "graph-report", "code-map", "llm-wiki-query", "wiki-search-tooling"]),
    n("ingest-query-lint", "Ingest / Query / Lint", "wiki", "operation", 20, "Source 통합, 검증된 질문, 결과 승격과 knowledge health check를 연결합니다.", ["Wiki를 살아있는 지식 베이스로 유지하고 다음 agent의 반복 분석을 줄입니다."], "docs/obsidian-vault/wiki/operations/ingest-query-lint.md", ["wiki-log", "llm-wiki", "llm-wiki-ingest", "llm-wiki-query", "llm-wiki-lint"]),
    n("llm-wiki-ingest", "LLM Wiki Ingest", "wiki", "operation", 19, "Immutable raw를 보존하고 source impact를 관련 page, index, log와 public projection에 통합합니다.", ["중요한 source는 one-by-one human review가 기본입니다.", "10~15 page는 영향 범위 예시이며 목표 숫자가 아닙니다."], "docs/obsidian-vault/wiki/operations/llm-wiki-ingest.md", ["ingest-query-lint", "wiki-schema", "source-provenance", "human-review"]),
    n("llm-wiki-query", "LLM Wiki Query", "wiki", "operation", 19, "Graph, Wiki, source 순으로 citation 가능한 답을 만들고 재사용 결과를 다시 Wiki에 승격합니다.", ["시간에 민감한 구현 상태는 current source를 검증합니다."], "docs/obsidian-vault/wiki/operations/llm-wiki-query.md", ["ingest-query-lint", "agent-ladder", "compounding-knowledge", "wiki-search-tooling"]),
    n("llm-wiki-lint", "LLM Wiki Lint", "wiki", "operation", 19, "Contradiction, stale claim, orphan, dead link, provenance와 public boundary를 검사합니다.", ["Data gap은 다음 query와 source 후보로 전환합니다."], "docs/obsidian-vault/wiki/operations/llm-wiki-lint.md", ["ingest-query-lint", "wiki-schema", "source-provenance", "public-export"]),
    n("wiki-search-tooling", "Wiki Search Ladder", "wiki", "search", 18, "Index와 rg에서 시작해 graph query와 선택적 local hybrid search로 확장합니다.", ["검색 infrastructure는 실제 recall 병목이 생길 때 도입합니다."], "docs/obsidian-vault/wiki/operations/wiki-search-and-tooling.md", ["wiki-index", "agent-ladder", "graph-report", "qmd-search"]),
    n("qmd-search", "qmd Hybrid Search", "wiki", "tool", 15, "규모가 커질 때 평가할 local BM25/vector search와 LLM re-ranking 선택지입니다.", ["CLI와 MCP interface를 제공하지만 현재는 도입 기준만 문서화했습니다."], "docs/obsidian-vault/wiki/operations/wiki-search-and-tooling.md", ["wiki-search-tooling", "agent-ladder"]),
    n("obsidian-workflow", "Obsidian Knowledge Workflow", "wiki", "operation", 18, "Web Clipper, local image, Graph View, Dataview, Marp와 Git review를 연결합니다.", ["Obsidian은 사람이 agent의 Wiki diff를 실시간 탐색하는 IDE입니다."], "docs/obsidian-vault/wiki/operations/obsidian-knowledge-workflow.md", ["obsidian-wiki", "human-review", "llm-wiki-ingest", "public-export"]),
    n("human-review", "Human Review", "wiki", "workflow", 18, "사람이 source 선택, 강조점, 모순과 공개 여부를 결정하고 agent의 bookkeeping을 통제합니다.", ["자동 생성 결과를 정본이나 공개본으로 바로 승격하지 않습니다."], "docs/obsidian-vault/wiki/operations/obsidian-knowledge-workflow.md", ["obsidian-workflow", "llm-wiki-ingest", "compounding-knowledge"]),
    n("public-export", "Public Export", "wiki", "boundary", 18, "Private vault에서 credential과 private raw를 제외한 구조와 지식만 portfolio demo로 투영합니다.", ["Public graph와 Wiki는 secret scan과 사람 review를 통과합니다."], "docs/obsidian-vault/wiki/operations/public-export.md", ["source-provenance", "llm-wiki-lint", "obsidian-workflow", "portfolio-graph"]),
    n("source-index", "Source Index", "wiki", "source", 16, "Graphify, LLM Wiki, Obsidian agent setup 자료의 링크와 적용점을 기록합니다.", ["외부 글 전문 대신 출처와 적용점을 기록합니다."], "docs/obsidian-vault/raw/web-sources/source-index.md", ["raw-sources", "llm-wiki"]),
  ];

  const highlights = [
    h("시연 준비 경계", "최신 source, Obsidian synthesis, synthesis merge, latest projection merge와 과거 full execution baseline을 분리해 검증 범위를 과장하지 않습니다.", "d00a57fc source"),
    h("Graphify 실제 추출", "API 키 없이 code-only AST 추출을 실행해 전체 repo에서 26,886 nodes / 91,616 edges를 확인했습니다.", "2,870 code files"),
    h("백엔드 구현 밀도", "Spring Boot 4.1, Java 21, MyBatis 기반 backend만 별도 추출해 13,548 nodes / 47,689 edges가 나왔습니다.", "1,787 files"),
    h("프런트/관리자 표면", "React SPA의 사용자/관리자 화면과 mock registry를 frontend/src 단위로 추출했습니다.", "4,448 nodes"),
    h("ML 실험 증거", "career-strategy, correction, interview, nonverbal, job-posting-worker가 각각 문서와 실행 스크립트를 갖습니다.", "5 model areas"),
    h("배포 산출물", "GitHub Pages, Android APK, iOS simulator, desktop release 전략까지 같은 지식맵 안에서 추적합니다.", "4 channels"),
  ];

  const graphifyRuns = [
    run("whole repo", "전체 repo code-only", 2870, 26886, 91616, "generated/mobile/desktop build 산출물도 일부 포함된 1차 전체 스캔"),
    run("backend", "Spring/MyBatis backend", 1787, 13548, 47689, "calls 14,098 · references 12,543 · imports 12,217"),
    run("frontend/src", "React/Vite source", 571, 4448, 13356, "contains 3,821 · imports 3,701 · calls 2,829"),
    run("ml", "ML scripts and tests", 204, 2016, 4830, "calls 1,531 · contains 1,048 · imports 621"),
  ];

  const wikiPages = [
    page("CareerTuner Project Overview", "지원 건 중심 제품 가치, end-to-end workflow와 구현 platform.", "docs/obsidian-vault/wiki/project/project-overview.md", "project/overview"),
    page("Application Case and Product Flow", "공고 revision, 분석 workspace와 lifecycle의 핵심 aggregate.", "docs/obsidian-vault/wiki/project/application-case-and-product-flow.md", "project/application-case"),
    page("User and Admin Experience", "사용자 IA, responsive UX와 paired admin operation.", "docs/obsidian-vault/wiki/project/user-and-admin-experience.md", "project/user-admin"),
    page("Backend and API Platform", "Spring/MyBatis 4계층, API family와 provider boundary.", "docs/obsidian-vault/wiki/project/backend-and-api-platform.md", "engineering/backend-api"),
    page("Frontend and Demo Platform", "React feature/admin, mock registry와 PWA surface.", "docs/obsidian-vault/wiki/project/frontend-and-demo-platform.md", "engineering/frontend-demo"),
    page("Data Model and Lifecycle", "Profile version, Application Case, analysis run과 policy history.", "docs/obsidian-vault/wiki/project/data-model-and-lifecycle.md", "engineering/data-lifecycle"),
    page("AI Orchestration and Evidence", "Auto Prep planner, provider fallback와 evidence gate.", "docs/obsidian-vault/wiki/project/ai-orchestration-and-evidence.md", "ai-ml/orchestration-evidence"),
    page("Model Training Portfolio", "A~F 자체 모델, evaluation과 artifact boundary.", "docs/obsidian-vault/wiki/project/model-training-portfolio.md", "ai-ml/model-portfolio"),
    page("Mobile Desktop and Distribution", "PWA, Android, iOS와 Qt desktop package.", "docs/obsidian-vault/wiki/project/mobile-desktop-and-distribution.md", "delivery/mobile-desktop"),
    page("Release CI and Public Demo", "Pages/APK/iOS/desktop channel과 release gate.", "docs/obsidian-vault/wiki/project/release-ci-and-public-demo.md", "delivery/release-demo"),
    page("Repository and Knowledge Boundaries", "Main, submodule와 public Demo의 저장 책임.", "docs/obsidian-vault/wiki/project/repository-and-knowledge-boundaries.md", "governance/repository-boundaries"),
    page("Team Ownership and Governance", "A~F vertical ownership와 common owner boundary.", "docs/obsidian-vault/wiki/project/team-ownership-and-governance.md", "governance/team-ownership"),
    page("Demo Readiness Refresh", "Admin/auth/data/outage/A~F/platform gate와 최신 UI delta를 source·실행 증거별로 분리한 공개 원장.", "docs/verification/DEMO_READINESS_LEDGER.md", "project/readiness-refresh"),
    page("Portfolio Evidence Map", `Graphify extraction과 ${nodes.length}-node evidence navigation.`, "Obsidian/SecondBrain/graph-data.js", "evidence/portfolio-map"),
    page("CareerTuner Second Brain", "Obsidian, Graphify, LLM Wiki, public demo를 연결하는 전체 구조.", "docs/obsidian-vault/wiki/systems/careertuner-second-brain.md", "systems/careertuner-second-brain"),
    page("Agent Memory Protocol", "Codex/Claude Code류 agent가 graph/wiki/source를 읽는 우선순위.", "docs/obsidian-vault/wiki/systems/agent-memory-protocol.md", "systems/agent-memory-protocol"),
    page("LLM Wiki Architecture", "Query-time retrieval 위에 raw/wiki/schema의 persistent synthesis를 두는 구조.", "docs/obsidian-vault/wiki/systems/llm-wiki-architecture.md", "systems/llm-wiki-architecture"),
    page("Wiki Schema", "Page class, provenance, cross-link와 multi-page update 계약.", "docs/obsidian-vault/wiki/systems/wiki-schema.md", "systems/wiki-schema"),
    page("LLM Wiki", "Source와 중요한 query 결과를 persistent Markdown으로 누적하는 패턴.", "docs/obsidian-vault/wiki/concepts/llm-wiki.md", "concepts/llm-wiki"),
    page("Compounding Knowledge", "이전 탐색과 비교 결과를 다음 작업의 출발점으로 재사용하는 원리.", "docs/obsidian-vault/wiki/concepts/compounding-knowledge.md", "concepts/compounding-knowledge"),
    page("Wiki Index and Log", "내용 지도인 index와 시간 지도인 log의 서로 다른 책임.", "docs/obsidian-vault/wiki/concepts/wiki-index-and-log.md", "concepts/wiki-index-log"),
    page("Graphify", "code/docs를 graph로 추출하고 query 가능한 memory로 쓰는 도구.", "docs/obsidian-vault/wiki/concepts/graphify.md", "concepts/graphify"),
    page("Obsidian Vault", "사람이 graph, metadata와 Markdown diff를 검토하는 IDE.", "docs/obsidian-vault/wiki/concepts/obsidian-vault.md", "concepts/obsidian-vault"),
    page("Ingest Query Lint", "Source 통합, 검증된 질문과 knowledge health check의 반복 workflow.", "docs/obsidian-vault/wiki/operations/ingest-query-lint.md", "operations/ingest-query-lint"),
    page("LLM Wiki Ingest", "Source impact 분석부터 여러 page 통합과 완료 조건까지의 runbook.", "docs/obsidian-vault/wiki/operations/llm-wiki-ingest.md", "operations/llm-wiki-ingest"),
    page("LLM Wiki Query", "Graph/wiki/source 3계층 검증과 query-result promotion 절차.", "docs/obsidian-vault/wiki/operations/llm-wiki-query.md", "operations/llm-wiki-query"),
    page("LLM Wiki Lint", "구조, 최신성, provenance와 공개 경계를 검사하는 health check.", "docs/obsidian-vault/wiki/operations/llm-wiki-lint.md", "operations/llm-wiki-lint"),
    page("Wiki Search and Tooling", "Index와 rg부터 Graphify와 선택적 qmd/MCP까지의 search ladder.", "docs/obsidian-vault/wiki/operations/wiki-search-and-tooling.md", "operations/wiki-search-tooling"),
    page("Obsidian Knowledge Workflow", "Web Clipper, image, Graph View, Dataview, Marp와 Git review.", "docs/obsidian-vault/wiki/operations/obsidian-knowledge-workflow.md", "operations/obsidian-knowledge-workflow"),
    page("Graphify Runbook", "설치, 실행, 산출물 관리, 민감값 검사 절차.", "docs/obsidian-vault/wiki/operations/graphify-runbook.md", "operations/graphify-runbook"),
    page("Public Export", "Private vault에서 검토된 portfolio graph와 Wiki만 내보내는 기준.", "docs/obsidian-vault/wiki/operations/public-export.md", "operations/public-export"),
  ];

  const codeCards = [
    code("Fit Analysis Service", "C 영역 핵심 분석 서비스. Evidence gate, 학습 과제, 액션 보드와 연결됩니다.", "backend/src/main/java/com/careertuner/fitanalysis/service/FitAnalysisServiceImpl.java", ["Backend", "AI"]),
    code("Application Case Service", "지원 건 CRUD, 공고문 revision, soft delete 정책의 중심 서비스입니다.", "backend/src/main/java/com/careertuner/applicationcase/service/ApplicationCaseServiceImpl.java", ["Backend", "Product"]),
    code("B Analysis Generation", "공고/기업 분석 생성과 fallback 경로를 묶는 서비스입니다.", "backend/src/main/java/com/careertuner/applicationcase/service/BAnalysisGenerationService.java", ["Backend", "AI"]),
    code("OpenAI Responses Client", "구조화 분석 provider 경로. 실제 키 값은 공개하지 않고 주입 방식만 사용합니다.", "backend/src/main/java/com/careertuner/applicationcase/service/OpenAiResponsesClient.java", ["Backend", "AI"]),
    code("Mock Registry", "웹 데모/APK를 백엔드 없이 자체완결로 실행하는 핵심 프런트 장치입니다.", "frontend/src/app/lib/mock", ["Frontend", "Demo"]),
    code("Admin Routes", "사용자 기능과 짝을 이루는 관리자 콘솔 라우팅 표면입니다.", "frontend/src/admin/routes.ts", ["Frontend", "Admin"]),
    code("Job Posting Worker", "문서 추출, OCR smoke, Docker smoke, release readiness 검사를 가진 Python worker입니다.", "ml/job-posting-worker/README.md", ["ML", "Worker"]),
    code("Career Strategy Reports", "RAG hardcase, evidence gate, semantic judge 등 C 모델 개선 이력이 누적됩니다.", privateEvidence("private evidence (not published): AI evaluation reports"), ["ML", "Docs"]),
  ];

  const sources = [
    source("Karpathy LLM Wiki", "raw/wiki/schema, ingest/query/lint, index/log 패턴을 CareerTuner vault에 적용했습니다.", "https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f"),
    source("Graphify", "code-only AST 추출로 실제 CareerTuner graph 수치를 생성하고, 공개용으로 압축했습니다.", "https://github.com/safishamsi/graphify"),
    source("Graphify PyPI", "공식 패키지 graphifyy와 CLI graphify 설치 경로를 runbook에 반영했습니다.", "https://pypi.org/project/graphifyy/"),
    source("CareerTuner 공개 Architecture Projection", "지원 건 중심 도메인, Spring/MyBatis, React/Admin, AI 경계를 공개 graph data로 투영했습니다.", "https://github.com/notetester/CareerTunerPortfolio/blob/dev/Obsidian/SecondBrain/graph-data.js"),
    source("CareerTuner 공개 Obsidian Wiki", "비공개 원본에서 선별한 wiki/index, GRAPH_REPORT, log의 공개 설명 계층입니다.", "https://github.com/notetester/CareerTunerPortfolio/tree/dev/Obsidian/Wiki"),
  ];

  return {
    meta: {
      name: "CareerTuner Portfolio Knowledge Graph",
      updated: "2026-07-13",
      visibility: "portfolio-public",
      graphifyExtracted: true,
      latestSourceSha: "d00a57fc8d1e3499ba6c23acec498c47ac0d5d4c",
      synthesisSourceSha: "2c4b11a9b39d2bc34343797887722616091203e3",
      synthesisVaultMergeSha: "114b6d91aeef6fb4f3399bad2d7030ca8256d96e",
      latestProjectionMergeSha: "248e082b0ccf4f17e39a7cdc3728b1a8fe2ee3ec",
      executionBaselineSha: "30a5511a13a6a304fdf13231bfea1afe7a335c2e",
    },
    groups,
    nodes,
    highlights,
    graphifyRuns,
    wikiPages,
    codeCards,
    sources,
  };

  function n(id, label, group, type, weight, summary, points, evidenceValue, links) {
    const evidence = normalizeEvidence(evidenceValue);
    return { id, label, group, type, weight, summary, points, path: evidence.path, evidence, links };
  }

  function h(title, summary, metric) {
    return { title, summary, metric };
  }

  function run(scope, label, files, nodes, edges, note) {
    return { scope, label, files, nodes, edges, note };
  }

  function page(title, summary, path, wikiId) {
    return { title, summary, path, wikiId };
  }

  function code(title, summary, evidenceValue, tags) {
    const evidence = normalizeEvidence(evidenceValue);
    return { title, summary, path: evidence.path, evidence, tags };
  }

  function privateEvidence(label) {
    return { visibility: "private", label, path: "" };
  }

  function normalizeEvidence(value) {
    if (typeof value === "string") {
      return { visibility: "public", label: "published evidence", path: value };
    }
    if (value && typeof value === "object") {
      return {
        visibility: value.visibility || "public",
        label: value.label || "published evidence",
        path: value.path || "",
      };
    }
    return { visibility: "unrecorded", label: "evidence not recorded", path: "" };
  }

  function source(title, summary, href) {
    return { title, summary, href };
  }
})();
