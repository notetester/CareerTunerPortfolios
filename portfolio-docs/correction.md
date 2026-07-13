# 답변 첨삭

작성한 면접 답변, 자기소개서, 이력서 문장, 포트폴리오 설명을 채용공고와 지원자 프로필에 맞춰 AI로 다듬는 기능입니다. CareerTuner의 핵심 단위인 "지원 건(Application Case)"에 첨삭 요청을 연결해, 그 공고의 회사·직무·요구 역량과 지원자의 실제 스펙을 함께 근거로 사용합니다.

첨삭의 설계 원칙은 "없는 사실을 만들지 않는다"입니다. 원문과 프로필에 존재하는 정보만 근거로 표현을 개선하고, 근거가 부족한 강화 문장은 실제 내용에 추가하지 않고 제안(suggestion)으로만 남깁니다. 이 원칙은 프롬프트, 응답 스키마, 출력 검증 파서 세 단계에서 반복적으로 강제됩니다.

## 주요 기능

- 4개 첨삭 유형 지원: 자기소개서(`SELF_INTRO`), 면접 답변(`INTERVIEW_ANSWER`), 이력서(`RESUME`), 포트폴리오 설명(`PORTFOLIO`)
- 지원 건 연결 시 해당 공고의 직무 분석 결과(요구·우대 기술, 담당 업무, 자격요건)를 첨삭 컨텍스트로 주입
- 지원자 프로필(경력·프로젝트·기술·자격증 등)을 사실 목록으로 주입해 근거 기반 첨삭
- 구조화된 결과 반환: 개선문(`improvedText`), 요약, 이슈/리스크, 변경 사유, 추천 키워드/제안
- 자체 파인튜닝 LLM 우선, 실패 시 Anthropic → OpenAI 순 다단계 폴백
- 자체 모델 사전 워밍업(warmup) 엔드포인트로 첫 호출 지연 완화
- 첨삭 이력 저장 및 조회(유형·지원 건별 필터, 최근순)
- 요청별 토큰 사용량을 `ai_usage_log`에 기록하고 크레딧 산정
- C 적합도와 D 면접 원문을 소유권 검증된 ID로 가져오는 인계
- 사용자가 실행 모델을 선택하고 요청 모델·실제 provider provenance를 확인
- 실행 전 사용권·크레딧 preview와 사용자 확인, 성공 결과에만 멱등 정산
- 첨삭 결과 soft delete와 활성 이력 기본 조회

## 핵심 구현

### REST API와 요청 처리 흐름

`CorrectionController`(`/api/corrections`)가 진입점이며, 응답은 전부 `ApiResponse<T>` envelope로 감쌉니다.

- `POST /api/corrections/warmup` — 자체 모델 워밍업 트리거
- `POST /api/corrections` — 첨삭 실행
- `GET /api/corrections` — 첨삭 이력 목록(`applicationCaseId`, `correctionType`, `limit` 필터)
- `GET /api/corrections/{id}` — 단건 조회

`CorrectionService.create()`가 전체 흐름을 조율합니다. 입력 정규화(`correctionType`/`sourceType`/`originalText` 검증, 원문 최대 12,000자), 지원 건 소유권 확인(`ApplicationCaseAccessService.requireOwned`), 컨텍스트 조립, AI 호출, 결과 영속화 순으로 진행합니다.

주목할 설계는 트랜잭션 경계입니다. AI 호출은 트랜잭션 **밖**에서 수행하고(수십 초가 걸릴 수 있는 외부 I/O가 DB 커넥션을 붙잡지 않도록), 호출 성공 후 `TransactionTemplate.execute()` 안에서 사용량 로그 기록과 `correction_request` INSERT만 원자적으로 처리합니다. AI 호출이 예외로 실패하면 `usageLogService.recordFailure(...)`로 실패 로그를 남긴 뒤 예외를 재던집니다.

### 컨텍스트 조립 — 공고 분석 + 프로필 사실 주입

`CorrectionContextService.build()`가 첨삭 근거가 되는 `SelfCorrectionInput`을 구성합니다.

- 지원 건이 연결된 경우 `JobAnalysisService`에서 직무 분석을 조회해 회사명, 직무, 고용형태, 요구/우대 기술, 담당 업무, 자격요건, 요약을 `job_context`로 넣습니다. 단, 프롬프트에서 `job_context`는 "표현 방향"에만 쓰고 지원자의 실제 경험처럼 서술하지 못하게 제약합니다.
- `ProfileService`에서 희망 직무·산업, 학력, 경력, 프로젝트, 보유 기술, 자격증, 언어 등을 사실 목록(`user_profile_facts`)으로 변환합니다. 각 사실은 1,200자로 잘리고 최대 30개까지만 포함합니다.
- 이력서/자기소개서 원문이 첨삭 대상 원문과 동일하면 중복 주입을 피합니다.
- 제약(`constraints`)으로 톤(professional), 최대 글자수(원문 길이 기반 동적 산정), `preserve_facts_only=true`를 함께 전달합니다.

### AI 다단계 폴백 체인

`CorrectionAiClient.correct()`가 provider 선택과 폴백을 담당합니다. 설정(`careertuner.correction.ai.provider`)이 `self`이고 자체 모델 base-url이 구성돼 있으면 다음 순서로 시도합니다.

1. **자체 파인튜닝 모델(primary)** — `SelfLlmCorrectionProvider`, 기본 `careertuner-e-correction:8b`
2. **자체 모델 fallback** — 기본 `careertuner-e-correction-3b:latest`
3. **Anthropic** — `AnthropicCorrectionProvider`(키 구성 시), 기본 모델 `claude-haiku-4-5-20251001`
4. **OpenAI** — `OpenAiCorrectionProvider`(최종 폴백)

전체 시도는 `totalTimeBudget`(기본 30초) 안에서 이뤄지며, 남은 예산을 계산해 개별 호출 타임아웃을 좁히고 재시도 백오프도 예산을 초과하지 않게 sleep합니다. primary/fallback은 재시도 횟수(`primaryMaxAttempts=2`, `fallbackMaxAttempts=1`)를 갖고, 재시도 가능 여부는 예외 종류로 구분합니다. 자체 모델 워밍업이 진행 중이면 첫 호출 전 `warmupService.awaitIfInProgress(...)`로 잠시 대기합니다.

`provider`와 사용자의 모델 선택에 따라 허용 경로가 달라집니다. AUTO는 배포 정책의 다단계 경로를 쓰고, 사용자가 특정 모델을 명시한 재실행은 그 선택을 요청에 유지합니다. 실제 가용성과 구조 검증 실패는 결과 provenance에 남깁니다.

### 자체 모델 호출과 출력 검증

`SelfLlmCorrectionProvider`는 OpenAI 호환 `/v1/chat/completions` 규격으로 자체 서버(Ollama)에 요청합니다. 요청에는 한국어 시스템 프롬프트(`CorrectionPromptCatalog.SELF_SYSTEM_PROMPT`)와 함께, task_type별로 동적 생성한 `json_schema` `response_format`(strict)을 실어 출력 형태를 강하게 유도합니다.

응답은 `SelfCorrectionOutputParser`가 재검증합니다. 스키마만 믿지 않고 파서에서 다시 다음을 강제합니다.

- 루트 객체가 정확히 10개 키만 가질 것, `status`는 `"ok"`, `task_type`이 요청과 일치할 것
- `changes` 배열은 1개 이상이고 각 항목의 `evidence_source`는 `original_text`/`user_profile_facts`/`job_context` 중 하나
- **`added_facts`는 반드시 비어 있을 것** — 원문/프로필에 없는 사실을 결과에 넣지 못하게 하는 핵심 가드
- `confidence`는 0~1 숫자, `preserved_meaning`은 boolean

모델이 `<think>...</think>`나 코드펜스를 붙여 응답해도 `extractJsonSpan()`으로 JSON 구간만 추출해 파싱합니다. 검증 실패(`InvalidOutputException`)나 재시도 가능한 호출 실패는 상위 클라이언트의 재시도·폴백 로직으로 흡수됩니다.

### 결과 영속화와 조회

첨삭 결과는 `correction_request` 테이블에 저장됩니다. 원문(`original_text`, MEDIUMTEXT), 개선문(`improved_text`), 출처 ID와 요청 모델, 그리고 요약·이슈·변경사유·제안·모델 원본결과를 담은 `result_json`을 함께 보관합니다. `CorrectionService.resultJson()`이 직렬화하고 조회 시 다시 검증합니다. 삭제는 soft delete로 처리해 기본 목록에서는 제외하되 감사·운영 수명주기를 보존합니다.

`CorrectionMapper.xml`의 조회 쿼리는 항상 `user_id`로 스코프를 걸어 타 사용자 데이터 접근을 차단하며, 목록은 최근순으로 페이징합니다.

### 과금 preview와 멱등 정산

첨삭 버튼을 누르자마자 차감하지 않습니다. `AiChargePreviewService`가 사용권과 예상 크레딧을 계산해 먼저 보여주고, 확인된 요청만 실행합니다. 성공 결과가 저장된 뒤 `AiChargeRequestSettlementService`가 action/request key를 기준으로 한 번만 정산합니다. timeout 뒤 같은 요청을 재전송해도 첨삭과 과금이 두 번 생기지 않습니다. 사용권이 없으면 정책에 따라 크레딧으로 폴백합니다.

## 설계 결정과 트레이드오프

- **AI 호출을 트랜잭션 밖으로 분리**: 외부 LLM 응답이 느려도 DB 커넥션을 오래 점유하지 않습니다. 대신 "AI 성공 → 로그·저장"이 별도 단계로 나뉘어, 성공 payload 확보 후 짧은 트랜잭션으로 커밋하는 구조를 택했습니다.
- **스키마와 파서의 이중 검증**: strict `json_schema`로 형태를 유도하면서도 파서에서 다시 전수 검증합니다. 자체 파인튜닝 모델은 상용 모델보다 포맷 이탈 가능성이 있어, 잘못된 출력을 저장하기 전에 걸러내고 재시도/폴백으로 넘기기 위한 방어입니다.
- **사실 날조 방지의 다중 방어선**: "없는 경력/수치/성과 추가 금지"를 프롬프트 지시, 응답 스키마, `added_facts` 비어있음 강제 세 곳에 겹쳐 배치했습니다. 첨삭 도구가 지원자 이력을 과장하는 위험을 구조적으로 낮춥니다.
- **다단계 폴백 + 시간 예산**: 자체 모델을 우선하되 가용성을 상용 모델로 보완합니다. 전체 시간 예산 안에서 재시도·폴백을 순차 진행해, 한 provider의 지연이 사용자 응답 시간을 무한정 끌지 않도록 상한을 둡니다.
- **네 유형의 한 계약**: 자기소개서·면접 답변·이력서·포트폴리오는 화면과 출처가 다르지만 같은 구조화 결과·소유권·과금 계약을 사용합니다. 프런트는 실행, 이력, 상세, 모델 선택, 삭제까지 실제 API와 연결됩니다.

## 데이터 · 연동

- **테이블**: `correction_request`(첨삭 요청·결과 저장), `ai_usage_log`(공통 사용량 로그), 조회 시 `application_case`·`user_profile` 참조. `correction_request`는 `user_id`/`application_case_id`/`ai_usage_log_id`에 외래키를 걸고, `result_json`은 MySQL JSON 타입입니다.
- **AI Provider**: 자체 파인튜닝 LLM(Ollama, OpenAI 호환 API) → Anthropic Messages API → OpenAI Responses API 폴백 체인.
- **내부 연동**: `JobAnalysisService`(공고 분석 컨텍스트), `ProfileService`(프로필 사실), `ApplicationCaseAccessService`(지원 건 소유권 검증).
- **사용량·크레딧**: 성공/실패 사용량 로그, 실행 전 preview, 결과 성공 후 요청 키 기반 실제 정산을 분리합니다.
- API 키 등 민감 값은 설정 프로퍼티(`careertuner.correction.ai.*`, `careertuner.anthropic.*`)로 주입하며 코드에 하드코딩하지 않습니다.

## 사용 기술

- **백엔드**: Spring Boot 4 / Java 21, `java.net.http.HttpClient` 기반 LLM 호출, `@ConfigurationProperties`, `TransactionTemplate`(트랜잭션 경계 제어), MyBatis(`@Mapper` + XML)
- **AI**: 자체 파인튜닝 LLM(LoRA/Ollama 서빙), OpenAI 호환 chat completions + strict `json_schema`, Anthropic Messages API, OpenAI Responses API
- **데이터**: MySQL 8(`correction_request` JSON 컬럼), Jackson 기반 결과 직렬화/역직렬화
- **프런트엔드**: React 19 / TypeScript, 네 유형 입력·원문 인계·모델 선택·preview·이력·상세·soft delete
