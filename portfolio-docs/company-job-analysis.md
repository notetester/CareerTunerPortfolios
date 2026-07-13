# 회사 · 직무 분석

CareerTuner의 모든 준비는 하나의 채용공고에서 출발합니다. 회사·직무 분석은 지원 건(Application Case)에 등록된 **공고문 한 장만을 입력**으로 받아, 회사의 사업·산업·경쟁사·이슈와 공고의 고용 형태·경력 수준·필수/우대 역량·담당 업무·자격 요건을 구조화된 데이터로 정리합니다. 이 결과는 이후 적합도 분석·면접 준비·자기소개서 첨삭 같은 다른 영역이 신뢰하고 소비하는 공통 입력이 됩니다.

이 도메인의 설계 목표는 "회사를 화려하게 설명하는 것"이 아니라 **"모르는 것을 모른다고 표시하면서, 공고에서 확인되는 것만 근거와 함께 분리해 저장하는 것"** 입니다. 취업이라는 의사결정에 LLM 환각이 섞이면 잘못된 지원 판단으로 번지기 때문에, 환각을 데이터 모델·프롬프트·검증 세 층에서 차단합니다. 담당 파트는 B입니다.

## 주요 기능

- **회사 분석(company analysis)**: 공고문에서 회사 요약, 산업, 경쟁사, 면접 준비 포인트를 정리하고 **검증된 사실**과 **AI 추론**을 컬럼 단위로 분리 저장
- **직무 분석(job analysis)**: 공고 원문에서 고용 형태, 경력 수준, 필수/우대 역량(배열), 담당 업무, 자격 요건, 난이도, 요약, 근거 인용을 추출
- **담당 업무 요약(duties summary)**: 산만한 "주요 업무" 설명을 짧은 텍스트 한 필드(`duties`)로 정제 — 별도 호출이 아니라 직무 분석 한 번에 묶여 산출
- **문장 분류 전처리**: 규칙 기반 분류기가 공고를 11개 라벨로 분류해 LLM에 힌트로 동봉하고, 폴백 시 원문 문장을 그대로 사용
- **환각 차단 3중 방어**: 시스템 프롬프트(외부검색 금지) + JSON Schema(구조 강제) + 페이로드 검증(빈 사실 거부)
- **다단 폴백 체인**: 자체모델(Ollama) → Claude(Haiku) → OpenAI → 결정적 규칙 생성기(`self-rules-v1`)
- **정보 신선도 관리**: 확인 시점(`checked_at`)·재조회 권장일(`checked_at + 30일`) 기록, 공고 revision 동결
- **사용자 검수**: 필드별 부분 수정과 확정(`confirmed_at`), JSON 필드는 키 스키마 검증 후 저장

## 핵심 구현

### 진입 경로와 서비스 계층

사용자 대상 엔드포인트는 지원 건 컨트롤러(`ApplicationCaseController`, `/api/application-cases`)에 얹혀 있고, 실제 로직은 도메인별 서비스로 위임됩니다. 회사·직무 분석은 각각 생성·조회·이력·검수 4종의 엔드포인트를 대칭으로 제공합니다.

```java
// ApplicationCaseController
@PostMapping("/{id}/job-analysis")        // 직무 분석 생성(재분석)
@GetMapping("/{id}/job-analysis")         // 최신 결과
@GetMapping("/{id}/job-analysis/history") // 이력
@PatchMapping("/{id}/job-analysis/{analysisId}/review") // 검수 저장/확정

@PostMapping("/{id}/company-analysis")    // 회사 분석 생성(재분석)
@GetMapping("/{id}/company-analysis")     // 최신 결과
@GetMapping("/{id}/company-analysis/history")
@PatchMapping("/{id}/company-analysis/{analysisId}/review")
```

`CompanyAnalysisService`·`JobAnalysisService`는 동일한 골격을 따릅니다. `ApplicationCaseAccessService.requireOwned`로 소유권을 검사하고, `ensureAnalysisRunnable`로 상태 가드(`DRAFT`/`READY`만 허용, `ANALYZING`이면 충돌)를 건 뒤 상태를 `ANALYZING`으로 전이합니다. 이후 엔진을 호출하고 결과 payload를 받은 다음에만 INSERT합니다.

### AI는 트랜잭션 밖 — 느린 호출과 DB 커넥션 분리

LLM 호출은 수 분이 걸릴 수 있으므로 DB 커넥션을 잡지 않도록 **트랜잭션 밖에서** 실행합니다. payload를 받은 뒤에만 `TransactionTemplate`으로 INSERT·상태 전이·사용량 로깅을 한 트랜잭션에 묶습니다(`CompanyAnalysisService.createCompanyAnalysis` 참조).

```java
GeneratedCompanyAnalysis generated = bAnalysisGenerationService.generateCompanyAnalysis(applicationCase, sourceText);
var payload = generated.payload();
LocalDateTime checkedAt = LocalDateTime.now();
return transactionTemplate.execute(status -> {
    CompanyAnalysis companyAnalysis = CompanyAnalysis.builder()
            // ... payload 필드 매핑 ...
            .sourceType("JOB_POSTING")
            .checkedAt(checkedAt)
            .refreshRecommendedAt(checkedAt.plusDays(30))
            .build();
    companyAnalysisMapper.insertCompanyAnalysis(companyAnalysis);
    // 상태 READY 전이 + 사용량 로깅
});
```

실패 시 `restorePreviousStatus`로 상태를 원복하고, `userFacingFailureMessage`가 `com.mysql`·`org.springframework`·`TimeoutException` 등 내부 스택트레이스를 사용자 메시지로 노출하지 않도록 마스킹합니다.

### 다단 폴백 체인 (`BAnalysisGenerationService`)

회사·직무 분석 모두 동일한 4단 폴백 체인으로 수렴합니다. 자동 파이프라인과 동기 단건 재생성이 둘 다 이 한 엔진을 호출하므로, 산출 로직은 한 곳에 있습니다.

```
자체모델(Ollama) → Claude(Haiku) → OpenAI → self-rules-v1
```

- 자체모델(직무 분석)은 `maxRetries` 설정에 따라 재시도합니다.
- 각 단계 실패 시 다음 provider로 넘어가며, `self-rules-v1`은 외부 호출이 없는 결정적 규칙 생성기라 **절대 예외로 끝나지 않는 최종 안전망**입니다.
- 어떤 provider도 시도되지 않은 경우(전부 비활성/미설정)는 의도된 기본 동작이라 폴백으로 표시하지 않고, 실제 실패로 인해 규칙 생성기로 떨어진 경우만 `fellBack()`으로 구분해 사유·시도 모델을 `recordFailure`에 남깁니다.

LLM 호출은 `BLocalLlmClient.chat`이 담당합니다. Ollama `/api/chat`에 `temperature=0`, `num_ctx=8192`, `think=false`로 요청하고, **JSON Schema를 `format` 파라미터로 전달**해 구조화 출력을 강제합니다.

### 문장 분류 전처리 (`BJobSentenceClassifier`)

LLM에 원문만 던지지 않고, 먼저 규칙 기반 분류기가 공고를 줄/문장 단위로 쪼갠 뒤 11개 라벨(`RESPONSIBILITY`, `REQUIRED`, `PREFERRED`, `QUALIFICATION`, `TECH_STACK`, `COMPANY_INFO` 등)을 부착합니다. 섹션 헤더("담당 업무", "우대 조건", "회사 소개" 등)를 감지하면 그 아래 줄에 컨텍스트를 전파합니다.

- **직무 분석**: 분류 결과 전체를 JSON으로 직렬화(4,000자 절단)해 프롬프트의 "문장 분류 신호" 블록에 동봉 — LLM은 "이 문장들이 어떤 영역으로 추정된다"는 힌트를 받은 상태에서 작성
- **회사 분석**: `COMPANY_INFO` 라벨 문장만 추려 프롬프트에 동봉
- **폴백 경로**: LLM 없이 `RESPONSIBILITY`(담당 업무)·`COMPANY_INFO` 등 원문 문장을 그대로 join → 환각 0

### 담당 업무 요약 — 배열이 아닌 텍스트 1필드

담당 업무는 독립 AI 호출이 아니라 직무 분석 payload(`JobAnalysisPayload` 레코드)의 한 필드 `duties`입니다. 필수/우대 역량은 정규화 가치가 있어 JSON 배열로 저장하지만, 업무 설명은 문장·단락 형태라 `MEDIUMTEXT` 한 필드로 둡니다. JSON Schema에서 이 차이가 그대로 드러납니다.

```java
properties.put("duties", stringSchema());              // 단일 문자열
properties.put("requiredSkills", stringArraySchema()); // 배열
properties.put("preferredSkills", stringArraySchema());
```

`parseLocalJobPayload`는 `duties`를 필수 필드로 읽고, `validateJobPayload`는 `duties`나 `qualifications`가 비면 예외를 던져 폴백을 유발합니다. LLM이 두 번 다 실패하면 `selfRulesJobAnalysis`가 분류기의 `RESPONSIBILITY` 문장(최대 8개)을 이어 붙여 채우고, 그것도 없으면 공고 앞 2문장으로 임시 대체합니다.

### 환각 차단 3중 방어 (회사 분석)

1. **프롬프트** — `CompanyAnalysisPromptCatalog.SYSTEM_PROMPT`가 "외부 웹 검색 금지, 모델이 아는 회사 정보를 사실로 쓰지 말 것, `verifiedFacts`에는 입력에서 직접 확인되는 사실만, 대표자·설립일·매출 등 입력에 없는 정보 작성 금지, `source`는 회사명/직무명/채용공고 중 하나"를 명문화합니다(`VERSION = "b-v1"`).
2. **JSON Schema** — `verifiedFacts=[{fact, source}]`, `aiInferences=[{inference, basis}]` 형태를 스키마로 강제합니다. "추론에는 근거(basis)를 붙여라"가 구조로 박혀 있습니다.
3. **페이로드 검증** — `validateCompanyPayload`가 요약 길이·면접 포인트 유무를 점검하고, **검증 가능한 사실이 하나도 없고 회사명조차 없으면** LLM 결과를 버리고 규칙 폴백으로 떨어집니다.

직무 분석 쪽 대응은 `validateGrounding`입니다. LLM이 반환한 필수/우대 스킬 토큰이 공고 원문에 실제로 등장하는지 비율을 계산하고, 설정된 grounding 임계값 미만이면 예외를 던져 폴백시킵니다. 담당 업무(`duties`)에는 이 토큰 매칭이 걸리지 않고, 분류 신호와 원문 폴백으로 환각을 막습니다.

### R1 모델 출력 보정

자체 로컬 모델(R1 계열)의 비표준 출력을 후처리로 교정합니다.

- `normalizeExperienceLevel` — `"intermediate"`, `"MEDIUM"`, 숫자 등 비표준 값을 `JUNIOR`/`MID`/`SENIOR`로 정규화(분류 불가 값은 `MID`로 수렴)
- `reconcileExperienceLevel` — 공고 원문에서 정규식으로 경력 연차를 파싱(경력 키워드와 결합된 숫자만 인정, "2024년"·"설립 10년차"·"운영 5년" 같은 오탐 배제)해 5년 이상이면 `SENIOR`로 보정
- `filterSkillItems` — `"결제 시스템 백엔드 API 설계 및 개발"` 같은 업무 문장이 스킬 배열에 섞이면 길이·단어 수·문장 패턴으로 걸러 냄

## 설계 결정과 트레이드오프

**외부 웹 검색을 의도적으로 하지 않습니다.** 기업 분석이라면 보통 뉴스·재무 크롤링을 떠올리지만, 이 기능의 입력은 오직 회사명·직무명·공고문뿐입니다. 실시간 정보를 포기하는 대신 환각·오래된 정보·SSRF/법적 리스크를 차단하고, 무비용·오프라인으로 동작합니다. "최신 이슈는 사용자가 직접 확인하라"는 신호를 `recent_issues` 폴백 문구와 `refresh_recommended_at`(재조회 권장일)에 데이터로 남깁니다.

**요약 텍스트 1개 대신 "사실/추론 2분할"을 선택했습니다.** 가장 쉬운 구현은 `summary` 한 컬럼이지만, 그러면 사용자가 어디까지 공고에서 확인된 사실이고 어디부터 모델의 추측인지 구별할 수 없습니다. 그래서 스키마 자체를 `verified_facts`와 `ai_inferences` 별도 컬럼으로 쪼개, 데이터 모델이 곧 "사실 vs 추론" 경계를 강제하게 했습니다.

**담당 업무를 텍스트 1필드로, 역량만 배열로 분리했습니다.** 항목별 검색·필터가 필요한 필수/우대 역량만 JSON 배열로 두고, 문장 형태인 담당 업무·자격 요건은 `MEDIUMTEXT`로 둡니다. 프런트도 이를 반영해 역량은 칩, 담당 업무는 텍스트 블록으로 렌더링합니다.

**6개 AI 기능을 LLM 호출 2번으로 묶었습니다.** 담당 업무·필수/우대 역량·난이도 등을 개별 호출로 나누지 않고 직무 분석 한 번(`generateJobAnalysis`), 회사 요약·산업·경쟁사·면접 포인트를 회사 분석 한 번(`generateCompanyAnalysis`)에 묶어 비용·지연을 줄였습니다. 대신 특정 필드만 따로 재생성하는 세밀한 제어는 포기했고, 그 부분은 사용자 검수(필드별 수정)로 보완합니다.

**공고 revision을 분석 시점에 동결합니다.** INSERT 시 `job_posting_id`와 `job_posting_revision`을 함께 저장하므로, 나중에 공고가 새 revision으로 바뀌면 이 분석은 "이전 공고 버전 기준"임을 추적할 수 있습니다. `job_posting_id`는 원문이 삭제돼도 분석 결과가 남도록 `ON DELETE SET NULL`로 설계됩니다.

## 데이터 · 연동

### 테이블

`company_analysis` — 회사 분석 결과 한 행. 핵심 컬럼:

| 컬럼 | 타입 | 의미 |
| --- | --- | --- |
| `company_summary`, `recent_issues`, `interview_points` | MEDIUMTEXT | 회사 요약·최근 이슈·면접 포인트 |
| `industry` | VARCHAR(100) | 산업 (길이 제한, 서비스단에서 100자 컴팩트) |
| `competitors`, `sources` | JSON | 경쟁사 배열, 출처 `[{type,label}]` |
| `verified_facts` | JSON | 검증된 사실 `[{fact, source}]` |
| `ai_inferences` | JSON | AI 추론 `[{inference, basis}]` |
| `checked_at`, `refresh_recommended_at` | DATETIME | 확인 시점, 재조회 권장일(=확인+30일) |
| `job_posting_id`, `job_posting_revision` | BIGINT / INT | 분석 시점 공고 동결 |
| `confirmed_at`, `admin_memo` | DATETIME / VARCHAR | 사용자 확정 시점, 운영 메모 |

`job_analysis` — 직무 분석 결과 한 행. `required_skills`/`preferred_skills`는 JSON, `duties`/`qualifications`/`summary`는 텍스트, `evidence`/`ambiguous_conditions`는 JSON 배열(`[{field,quote}]`, `[{condition,assumption}]`), `experience_level`/`difficulty`는 enum 문자열.

매퍼(`CompanyAnalysisMapper.xml`, `JobAnalysisMapper.xml`)는 최신 1건(`findLatest...`, `ORDER BY created_at DESC, id DESC LIMIT 1`)·이력 전체·검수 UPDATE를 제공합니다. 검수 UPDATE는 요약·근거 필드만 갱신하고 동결 컬럼과 메타데이터는 건드리지 않습니다.

### AI provider

- **자체모델**: Ollama `/api/chat`, JSON Schema 구조화 출력 강제 (`BLocalLlmClient`)
- **폴백**: Claude(Haiku, `BAnthropicClient`) → OpenAI(`OpenAiResponsesClient`) → 규칙 생성기(`self-rules-v1`)
- 자체모델·규칙 생성기는 무과금이라 `AiUsageLogService.recordLocalSuccess`로 크레딧 0을 `COMPANY_RESEARCH`/`JOB_ANALYSIS` feature에 로깅하고, 폴백 발생 시 시도 모델·사유를 `recordFailure`로 함께 남겨 운영자가 폴백 비율을 추적합니다.

### 검수와 JSON 검증

`reviewCompanyAnalysis`·`reviewJobAnalysis`는 필드별 부분 수정을 허용합니다. null로 온 필드는 기존값을 유지하고, JSON 근거 필드(`verified_facts`/`ai_inferences`/`evidence`/`ambiguous_conditions`)는 `BAnalysisJsonValidator`가 배열·객체·필수 키(`{fact,source}` 등) 스키마를 검증한 뒤에만 저장합니다. `confirmed=true`면 `confirmed_at`에 확정 시점을 기록합니다.

## 사용 기술

- **백엔드**: Spring Boot 4 / Java 21, MyBatis(`@Mapper` + XML), MySQL 8
- **AI**: 자체 파인튜닝 LLM(Ollama, JSON Schema 구조화 출력), Anthropic Claude(Haiku) / OpenAI 폴백, 결정적 규칙 생성기(`self-rules-v1`)
- **전처리**: 규칙 기반 문장 분류기(11 라벨), 정규식 경력 연차 파서, 스킬 grounding 토큰 검증
- **아키텍처**: `controller → service → mapper → domain/dto` 4계층, `ApiResponse<T>` envelope, `TransactionTemplate`으로 AI 호출과 트랜잭션 분리
- **데이터**: `company_analysis` / `job_analysis` 테이블, JSON 컬럼으로 근거 배열 보관, 공고 revision 동결
