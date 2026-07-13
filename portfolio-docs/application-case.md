# 지원 건 · 공고 분석

CareerTuner의 모든 준비 활동은 개별 채용공고가 아니라 **지원 건(Application Case)** 을 축으로 묶입니다. 하나의 지원 건은 "이 회사, 이 직무에 지원한다"는 단위이며, 여기에 공고 원문·직무 분석·회사 분석·적합도 분석·면접 준비가 모두 매달립니다. 사용자는 공고를 URL·PDF·이미지·직접 입력 중 어떤 형태로든 등록하고, 시스템은 그로부터 텍스트를 추출해 품질을 검증한 뒤 후속 분석 파이프라인을 자동으로 이어갑니다.

핵심 난제는 "실제 채용공고는 대부분 캡처 이미지나 PDF"라는 점입니다. 이 도메인은 자체 호스팅 문서 추출 워커(PaddleOCR/PPStructure 기반)를 1차 경로로 두고, 추출 텍스트가 자동 분석에 쓸 만한지 품질 게이트로 판정하며, 애매하면 사용자 검수를 거쳐 잘못된 원문으로 잘못된 분석이 생성되는 것을 막습니다. 담당은 B입니다.

## 주요 기능

- 지원 건 생성: 공고 URL / PDF / 이미지 업로드 또는 텍스트 직접 입력으로 지원 건을 시작
- 이미지·PDF 공고문 OCR 추출: 자체 호스팅 Python 워커(PaddleOCR/PPStructure) 우선, 벤더 Vision은 정책 허용 시에만 폴백
- 비동기 추출 처리: 업로드 즉시 지원 건을 만들고, 추출·분석은 백그라운드 큐 워커가 폴링하며 처리
- 품질 게이트: 추출 텍스트를 `PASS` / `REVIEW_REQUIRED` / `FAILED`로 판정해 자동 분석 진행 여부 결정
- 사용자 검수 경로: `REVIEW_REQUIRED` 시 편집한 원문을 확정하면 파이프라인 재개, `FAILED` 시 재시도
- 공고 리비전 관리: 추출/편집마다 새 리비전을 저장해 분석 결과가 어느 원문에서 나왔는지 추적
- 추출 성공 후 자동 파이프라인: 직무 분석 → 회사 분석 → 적합도 분석 → 면접 준비 세션 생성까지 연쇄
- 알림: 추출 성공/실패/검수 필요를 사용자 알림으로 통지

## 핵심 구현

### 지원 건 단위와 생애주기

지원 건은 `com.careertuner.applicationcase` 도메인의 `ApplicationCase`(회사명·직무명·마감일·`sourceType`·`status`·즐겨찾기·아카이브/삭제 시각)로 표현됩니다. 컨트롤러 `ApplicationCaseController`(`/api/application-cases`)가 생성·조회·수정·삭제·복원과 공고 업로드·추출·분석 하위 엔드포인트를 모두 노출합니다.

지원 건 상태는 `DRAFT → ANALYZING → READY` 흐름을 가지며, 자동 파이프라인이 시작될 때 `ApplicationCaseAutoPipelineService`가 `markAnalysisStarted`로 진입 표시를 하고, 정상 종료 시 `markReadyAfterAnalysis`, 실패 시 `restoreAnalysisStatus`로 원 상태를 되돌립니다. 상태 전이가 원자적 UPDATE(이전 상태를 WHERE 조건에 포함)로 처리되어 동시 갱신에도 안전합니다.

업로드 진입점은 두 갈래입니다.

- `POST /api/application-cases/from-job-posting/upload` — 파일 하나로 지원 건을 새로 만들며 즉시 추출을 큐잉
- `POST /api/application-cases/{id}/job-posting/upload` — 기존 지원 건에 공고 파일을 붙이고 추출을 큐잉

두 경로 모두 `ApplicationCaseServiceImpl.queueExtraction(...)`으로 수렴합니다. 여기서 `ApplicationCaseExtraction` 레코드를 `status = QUEUED`(`EXTRACTION_STATUS_QUEUED`)로 삽입할 뿐, 추출·분석 자체는 그 자리에서 하지 않습니다. `TEXT`/`MANUAL` 직접 입력은 배경 추출이 불필요하므로(`needsBackgroundExtraction`) 큐잉 없이 처리됩니다.

### 비동기 추출 워커와 큐 처리

추출은 `ApplicationCaseExtractionWorker`가 스케줄러(`@Scheduled`, `initial-delay`/`fixed-delay` 기본 5초)로 폴링합니다. `ApplicationCaseExtractionSchedulingConfig`가 스케줄링을 활성화하며, 한 사이클은 다음을 수행합니다.

1. `expireStaleRunningExtractions` — `RUNNING` 상태로 타임아웃(기본 30분, `careertuner.extraction.worker.running-timeout-minutes`)을 넘긴 유령 작업을 `FAILED`로 정리
2. `findQueuedExtractions(BATCH_SIZE=5)` — `QUEUED` 작업을 배치로 조회
3. `claimQueuedExtraction` — 트랜잭션 안에서 `QUEUED → RUNNING`으로 원자적 클레임(`WHERE ... status = 'QUEUED'`, 영향 행 1일 때만 성공)해 중복 처리 방지
4. `processClaimed` — 추출·품질 판정·후속 분석까지 수행하고 결과를 `SUCCEEDED`/`FAILED`로 마감

마감 시점에도 `findRunningExtractionForUpdate`가 `FOR UPDATE`로 자기 작업을 다시 잠그고 결과를 기록하므로, 클레임–완료 구간이 다른 워커와 겹치지 않습니다. 상태 머신(`QUEUED`/`RUNNING`/`SUCCEEDED`/`FAILED`)과 클레임/타임아웃 정리 로직은 `ApplicationCaseExtractionMapper.xml`에 SQL로 구현되어 있습니다.

### 문서 추출 파이프라인 (OCR)

실제 텍스트 추출은 소스 타입별로 갈립니다(`ApplicationCaseExtractionWorker.extractPostingText`).

- `URL` → `JobPostingTextExtractor.extractUrl` : 직접 소켓 HTTP 페처로 페이지를 받아 Jsoup으로 본문 텍스트를 추출. 리다이렉트 제한(5회)·바디 크기 제한(1MB)·타임아웃(5초)에 더해, **SSRF 방어**로 localhost·사설망·링크로컬·CGNAT·클라우드 메타데이터(169.254.169.254) 등 위험 주소로의 접근을 차단합니다.
- `PDF` → 우선 PDFBox로 텍스트 레이어를 추출(`extractTextPdf`). 텍스트가 없는 스캔/이미지 PDF면 OCR 폴백으로 전환.
- `IMAGE` → 바로 OCR 폴백.

OCR 폴백(`JobPostingTextExtractor.ocrFallback`)은 다단 폴백 체인입니다.

1. **자체 호스팅 워커 우선**: `JobPostingAiWorkerClient`가 활성화되어 있으면(`extractFile`) `POST /extract/job-posting`으로 파일 참조를 넘겨 워커가 OCR을 수행. 워커는 기존 OCR 텍스트를 먼저 쓰고, 없으면 로컬 PaddleOCR(+PyMuPDF)로 처리합니다. 응답은 `text` + `meta`(strategy·qualityScore·qualityStatus·warnings 등) 계약을 따릅니다.
2. **Claude(Haiku) Vision**: 워커 미사용 로컬 경로에서 1차 OCR 폴백(선택적 주입 `BAnthropicClient`, 미설정이면 건너뜀).
3. **벤더 Vision(OpenAI)**: `JobPostingFallbackPolicy.allowed(stage)`가 허용할 때만. 스테이지 허용목록은 `JOB_POSTING_PDF_OCR` / `JOB_POSTING_IMAGE_OCR` 두 가지뿐입니다.
4. **최종 실패는 목업이 아님**: 모든 제공자가 불가하면 빈 텍스트 + `qualityStatus=FAILED`를 반환하고 "공고문을 텍스트로 직접 입력해 주세요"를 안내합니다. 가짜 공고문이 잘못된 분석으로 이어지는 것을 원천 차단하는 설계입니다.

벤더 폴백은 이중 조건입니다. 전역 설정(`JOB_POSTING_OPENAI_FALLBACK`, `ai_runtime_setting`에 영속, 관리자 화면에서 토글)이 켜져 있고 **동시에** 실패 스테이지가 허용목록에 있어야 합니다. 기본값은 비활성이며, 품질 실패만으로는 벤더 호출이 일어나지 않습니다.

### 품질 게이트

추출 텍스트가 자동 분석에 쓸 만한지 `ApplicationCaseExtractionQualityGate`가 판정합니다. 워커가 `qualityStatus`를 이미 반환했으면 그 값을 검증해 쓰고(알 수 없는 값은 `FAILED` 처리), 아니면 백엔드 규칙(`rules-v1`)으로 자체 채점합니다.

- **텍스트 길이**: 자동 분석 통과 최소 500자, 사용 가능 최소 200자
- **섹션 키워드**: 회사·직무·업무·자격·기술·근무조건·마감 등 7개 그룹(한/영 키워드)의 매칭 수
- **노이즈 경고**: 치환문자(`�`), 기호 비율 과다(>0.35), 텍스트 과소 등을 감점 요인으로 수집

점수와 조건을 합쳐 세 상태로 판정합니다.

| 상태 | 조건(요지) | 백엔드 동작 |
| --- | --- | --- |
| `PASS` | score ≥ 70, 길이 ≥ 500, 섹션 ≥ 2 | 추출 메타 저장 후 자동 분석 진행 |
| `REVIEW_REQUIRED` | 40 ≤ score < 70 | 추출 리비전 저장, 자동 분석 중단(사용자 검수 대기) |
| `FAILED` | score < 40, 길이 < 200, 빈 추출/파싱 실패 | 추출 실패 표시, 재시도 경로 노출 |

판정 결과는 `qualityScore`·`qualityStatus`·`qualityReportJson`(메트릭·경고·섹션 힌트)·`modelVersionsJson`으로 `ApplicationCaseExtraction`에 저장되어 왜 그 판정이 나왔는지 추적할 수 있습니다.

### 검수·재시도와 공고 리비전

`REVIEW_REQUIRED`/`FAILED`에 대한 사용자 조작 경로는 컨트롤러에 명시됩니다.

- `PATCH /{id}/job-posting/extraction/review` — 편집한 원문을 새 공고 리비전으로 저장하고 품질을 `PASS`(score 100)로 승격, 폴백 플래그를 초기화한 뒤 이어서 자동 분석을 재개
- `PATCH /{id}/job-posting/extraction/confirm` — 편집본 확정
- `POST /{id}/job-posting/extraction/retry` — 추출 재시도

공고 원문은 리비전으로 관리됩니다. 워커가 소스에서 새로 추출한 텍스트가 기존과 다르면(`shouldSaveExtractedPosting`) 새 리비전을 저장하고, 그 리비전 ID/번호가 이후 생성되는 직무·회사·적합도 분석에 스냅샷으로 함께 기록됩니다. 덕분에 "이 분석은 어느 시점 어느 원문에서 나왔는가"가 데이터로 남습니다.

### 추출 성공 후 자동 분석 파이프라인

품질이 `PASS`가 되면 `ApplicationCaseAutoPipelineService.runAfterExtractionPass`가 후속 분석을 연쇄합니다.

1. 지원 건 상태를 `ANALYZING`으로 표시
2. `BAnalysisGenerationService`로 직무 분석(`JobAnalysis`) 생성 — 고용형태·경력·필수/우대 스킬·업무·자격·난이도·근거
3. 공고 사실 기반 회사 분석(`CompanyAnalysis`) 생성 (`sourceType=JOB_POSTING`)
4. 적합도 분석(`FitAnalysis`) — 프로필 스킬과 대조해 매칭/미보유 스킬, 조건 매트릭스, 학습 로드맵 생성
5. `JOB` 모드 면접 세션과 예상 질문 시드 생성
6. 정상 종료 시 `READY`, 실패 시 상태 복원 + `ai_usage_log`에 실패 기록

직무·회사 분석(`BAnalysisGenerationService`)은 폴백 체인을 따릅니다. **자체모델(Ollama, B 파인튜닝) → Claude(Haiku) → OpenAI → 규칙 기반 `self-rules-v1`** 순으로, 각 단계에서 스키마/그라운딩 검증 실패·타임아웃·미설정이면 다음 단계로 내려갑니다. `self-rules-v1`은 외부 호출 없는 결정적 규칙 생성기라 어떤 상황에서도 예외로 끝나지 않는 최종 안전망입니다. 적합도 분석과 면접 질문 시드는 규칙/목업 생성기로 외부 호출 없이 만들어집니다. 파이프라인이 남기는 사용량은 모두 `credit_used=0`으로 `ai_usage_log`에 기록됩니다.

### 모델 선택, strict 재추출과 provenance

공고 등록 시 사용자는 OCR·공고 분석·기업 분석 모델을 각각 선택할 수 있습니다. 최초 파이프라인은 선택과 배포 정책을 claim해 한 번만 실행하고, 요청 provider·실제 provider·모델·시도 경로·fallback 이유를 결과에 남깁니다.

성공 결과도 다른 OCR 모델로 다시 추출할 수 있습니다. strict 재추출은 품질 비교가 목적이므로 선택한 provider가 실패했다고 임의로 다른 provider 결과를 섞지 않습니다. 검수·확정된 텍스트는 새 revision으로 downstream 분석에 전달됩니다.

업로드 한도는 관리자 runtime setting과 사용자 사전 조회가 같은 값을 사용합니다. 파일 저장은 로컬과 Cloudinary authenticated provider 경계를 가지며, 프런트에는 공개에 필요한 식별자만 전달합니다. worker Docker 이미지와 release readiness 검사가 OCR 의존성·모델 준비 상태를 확인합니다.

## 설계 결정과 트레이드오프

- **핵심 단위를 공고가 아닌 "지원 건"으로**: 같은 회사·직무라도 지원마다 맥락(프로필 시점, 원문 리비전)이 다릅니다. 지원 건을 축으로 두면 분석·면접 준비가 지원 맥락에 고정되지만, 공고 중복 저장이 늘어나는 비용을 감수합니다.
- **동기 응답 대신 비동기 큐**: 업로드 요청은 지원 건과 `QUEUED` 작업만 만들고 즉시 반환합니다. OCR·분석의 긴 처리 시간이 HTTP 요청을 붙잡지 않지만, 프런트가 상태를 폴링해야 하는 복잡도가 생깁니다. 유령 작업은 30분 타임아웃 정리로 방어합니다.
- **자체 호스팅 워커 우선, 벤더는 옵트인 폴백**: PaddleOCR 기반 자체 워커를 1차 경로로 두어 비용·데이터 주권을 확보하고, 벤더 Vision은 전역 설정 + 스테이지 허용목록의 이중 게이트로만 열립니다. 기본값이 "벤더 비활성"이라 의도치 않은 외부 호출을 막습니다.
- **실패 시 목업 대신 명시적 실패**: OCR이 모두 실패하면 가짜 텍스트를 만들지 않고 `FAILED`로 끝냅니다. 잘못된 공고문이 잘못된 직무·회사·면접 분석으로 전파되는 것을 막는 대신, 사용자에게 직접 입력이라는 수동 부담을 지웁니다.
- **품질 게이트로 자동/검수 분기**: `REVIEW_REQUIRED`에서 파이프라인을 멈춰 애매한 원문이 분석으로 새는 것을 차단합니다. 사용자가 한 번 편집·확정하면 `PASS`로 승격되어 그대로 이어집니다.
- **URL 추출의 SSRF 방어를 추출기 안에 내장**: 사용자가 임의 URL을 넣는 기능이라, 내부망·메타데이터 주소 차단을 별도 미들웨어가 아닌 추출기 레벨에서 강제합니다.

## 데이터 · 연동

주요 테이블

- `application_case` — 지원 건(회사·직무·마감·`sourceType`·`status`·즐겨찾기·아카이브/삭제)
- `application_case_extraction` — 추출 작업(상태·`extractionStrategy`·`qualityScore`·`qualityStatus`·`qualityReportJson`·`modelVersionsJson`·`fallbackEligible`·타임스탬프)
- `job_posting` — 공고 원문/리비전(원문·추출 텍스트·업로드 파일 참조·`sourceType`·revision)
- `job_analysis` / `company_analysis` / `fit_analysis` — 자동 파이프라인 산출물(원문 리비전 스냅샷 포함)
- `ai_runtime_setting` — 벤더 폴백 설정(`JOB_POSTING_OPENAI_FALLBACK`) 영속
- `ai_usage_log` — 공통 사용량 로깅(로컬/규칙 단계는 `credit_used=0`)

외부·내부 연동

- **자체 호스팅 문서 추출 워커** (`ml/job-posting-worker`, PaddleOCR/PPStructure + PyMuPDF): `POST /extract/job-posting`, `GET /health`. Spring은 `JOB_POSTING_AI_WORKER_*`(base URL·timeout·cache dir)로 연결하며 Docker Compose에서는 `job-posting-worker` 서비스로 기동
- **Claude(Haiku) Vision**: 로컬 경로 1차 OCR 폴백(선택 주입)
- **OpenAI Vision**: 스테이지 허용목록 + 전역 설정이 모두 켜졌을 때만 최종 폴백
- **직무/회사 분석 LLM 체인**: 자체모델(Ollama, B 파인튜닝) → Claude(Haiku) → OpenAI → `self-rules-v1` 규칙 폴백. 자체모델을 우선 경로로 두되 상위 실패 시 순차 폴백

파일 업로드 제약: 저장 디렉터리 `careertuner.uploads.job-posting-dir`(기본 `.uploads/application-postings`), 최대 크기 기본 5MB(`careertuner.uploads.max-file-size-bytes`).

## 사용 기술

- **백엔드**: Spring Boot 4 / Java 21, MyBatis(`@Mapper` + XML), `ApiResponse<T>` envelope, `@Scheduled` 폴링 워커 + `TransactionTemplate` 원자적 상태 전이
- **문서 처리**: Apache PDFBox(PDF 텍스트 레이어), Jsoup(HTML 본문), 직접 소켓 HTTP 페처(SSRF 방어 내장)
- **OCR/AI 워커**: 자체 호스팅 Python 워커(PaddleOCR/PPStructure, PyMuPDF), Docker
- **AI 연동**: OCR은 Claude/OpenAI Vision 폴백, 직무/회사 분석은 자체모델(Ollama, B 파인튜닝) → Claude → OpenAI → 규칙 기반 `self-rules-v1` 순차 폴백
- **데이터**: MySQL 8, 공고 리비전 및 추출 메타/품질 리포트 JSON 컬럼
- **프런트엔드**: React 19 / Vite / TypeScript — `features/applications`(추출 상태 모니터·배지, 공고/분석 패널, 검수 편집기)
