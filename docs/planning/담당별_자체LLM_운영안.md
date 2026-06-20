# CareerTuner — 담당별 자체 LLM 운영안

> 작성 기준: 기존 `README.md`, `backend/README.md`, `frontend/README.md`, `docs/ARCHITECTURE.md`, `docs/TEAM_WORK_DISTRIBUTION.md`, `docs/FEATURE_MODULE_STRUCTURE.md`, `docs/planning/자체LLM_팀_도입안.md`, 현재 런타임 소스를 함께 검토해 정리한 실행 운영안.
> 문서 목적: A~F 담당자가 각자 자기 도메인용 자체 모델을 운용할 때 필요한 모델 범위, 입력/출력, 검증, 저장, fallback, 산출물 기준을 프로젝트 구조에 맞춰 통일한다.
> 문서 성격: `docs/planning/자체LLM_팀_도입안.md`가 선택 근거와 팀 결정표라면, 이 문서는 결정 이후 실제 작업과 인수인계에 쓰는 상세 운영 기준이다.

---

## 0. 읽는 순서와 책임 범위

자체 LLM 관련 문서는 역할이 다르므로 다음 순서로 읽는다.

| 문서 | 책임 |
| --- | --- |
| `docs/planning/자체LLM_팀_도입안.md` | 왜 자체 LLM을 도입하는지, 어떤 모델·비용·일정·위험을 선택했는지 설명하는 논의/결정 문서 |
| `docs/planning/담당별_자체LLM_운영안.md` | 담당별 모델을 실제로 어떻게 학습·서빙·검증·폴백·시연할지 정하는 상세 운영 문서 |
| `docs/TEAM_WORK_DISTRIBUTION.md` | A~F 담당자별 사용자 기능, 관리자 기능, AI 기능, 주요 DB 책임 |
| `docs/FEATURE_MODULE_STRUCTURE.md` | 런타임 소스 경로와 공통 영역 변경 승인 규칙 |
| `backend/README.md` | 현재 구현된 백엔드 API, 설정, 시드 계정, 실제 동작 상태 |
| `ml/**/README.md` | 도메인별 모델 학습 실험이나 파인튜닝 스크립트 사용법 |

이 문서는 다음 내용을 결정한다.

- 담당자별 자체 모델의 역할과 1순위 task
- 공통 모델 호출 흐름과 `/health`, `/generate` 인터페이스
- JSON 스키마 검증, 원문 근거 검증, 정책 보정, fallback 기준
- 모델 산출물과 발표/시연 완료 기준
- E 담당 첨삭 모델의 강화된 모델 선택, 검증, 저장, 크레딧 연계 기준

이 문서는 다음을 직접 변경하지 않는다.

- 공통 `ai/common`, `ai/prompt`, `application.yaml`, `schema.sql` 구현
- 실제 모델 가중치, LoRA adapter, GGUF 파일
- 운영 DB 구조

위 영역은 `AGENTS.md`와 `FEATURE_MODULE_STRUCTURE.md`의 공통 영역 규칙에 따라 팀장 승인 또는 팀 합의 후 별도 PR로 변경한다.

---

## 1. 현재 저장소 상태 요약

문서 작성 시점의 런타임 상태는 다음과 같다. 목표 운영안과 현재 구현 상태를 혼동하지 않기 위한 기록이다.

| 영역 | 현재 상태 |
| --- | --- |
| 백엔드 AI 기본값 | `careertuner.openai.*` 설정으로 OpenAI Responses API 사용. 기본 모델은 `OPENAI_MODEL:gpt-5` |
| 공통 Ollama 설정 | `ai.ollama.*` 설정 존재. 현재 기본값은 `AI_OLLAMA_BASE_URL`, `AI_OLLAMA_MODEL:gemma4` |
| F 커뮤니티 검열 | `backend/src/main/java/com/careertuner/community/moderation`에 Ollama client, structured output, 검열 서비스 구현 |
| `ai/common` | 현재는 `package-info.java` 수준의 골격. 전 팀 공통 클라이언트 승격은 목표 상태 |
| D 면접 파인튜닝 | `ml/interview-finetune`에 D 면접 답변 평가용 LoRA/vLLM 실험 스크립트 존재 |
| E 첨삭/결제/크레딧 | 백엔드 도메인은 `package-info.java` 골격 중심. 프런트 `/correction`, `/billing` 화면은 프로토타입 성격 |
| 사용량 로그 | `ai_usage_log`가 여러 도메인에서 사용되며, E가 전체 사용량/크레딧 조회 화면 책임 |

따라서 본 문서의 공통 모델 라우팅, 담당별 환경변수, 상태 API는 목표 운영 기준이다. 현재 구현을 바꾸려면 별도 작업으로 진행한다.

---

## 2. 공통 운영 원칙

### 2.1 1인 1모델 원칙

CareerTuner의 자체 LLM 운영은 각 담당자가 자기 도메인에 특화된 모델을 1개 이상 보유하는 것을 원칙으로 한다. 목적은 외부 API 호출만으로 구성된 AI 기능을 넘어, 담당자가 데이터셋 설계, 모델 선택, 파인튜닝, 서빙, 검증, fallback까지 경험했음을 포트폴리오로 증명하는 것이다.

각 담당 모델은 다음 기준을 만족해야 한다.

- 담당 도메인에 맞는 입력/출력 task를 정의한다.
- 담당 도메인용 학습 또는 평가 JSONL 데이터를 보유한다.
- 공개 베이스 모델을 그대로 쓰는 경우에도 담당 도메인용 프롬프트, JSON 스키마, 검증 로직, fallback을 별도로 구성한다.
- 가능하면 LoRA 또는 QLoRA 방식으로 담당 도메인 adapter를 학습한다.
- 최종 시연에서는 adapter 직접 적용 또는 merge GGUF 모델 중 환경에 맞는 방식을 사용한다.
- 모델 실패 시 서비스 전체가 중단되지 않도록 OpenAI, 규칙 기반 엔진, mock, 캐시 중 하나 이상의 fallback을 둔다.

### 2.2 LLM의 역할 제한

자체 LLM은 모든 판단을 단독으로 확정하는 엔진이 아니다. CareerTuner에서 LLM은 다음 역할을 맡는다.

```text
원문/구조화 데이터 이해
→ 후보 추출
→ 설명 생성
→ 개선안 생성
→ 근거와 confidence 제공
```

최종 점수, 노출 여부, 위험 판정, 지원 판단, 첨삭 반영 여부, 크레딧 차감 여부는 서버의 규칙, 점수 정책, 검증 로직, 사용자 선택을 거쳐 확정한다.

### 2.3 공통 처리 흐름

담당별 모델은 모두 다음 흐름을 기준으로 한다.

```text
입력 데이터 수집
→ 전처리 및 구조화
→ 담당 모델 호출
→ JSON 스키마 검증
→ 원문 근거 검증
→ 도메인별 후처리 및 점수/정책 보정
→ 결과 저장
→ 화면 표시
→ 실패 시 fallback
```

분석 결과는 원본 데이터를 덮어쓰지 않는다. 공고 원문, 프로필 원문, 면접 답변 원본, 자기소개서 원문, 커뮤니티 원문은 보존하고, AI 결과는 별도 결과 또는 이력으로 저장한다.

### 2.4 개인정보와 동의

AI 기능은 사용자 데이터, 자기소개서, 면접 답변, 영상/음성 특징, 커뮤니티 글을 처리할 수 있으므로 다음 기준을 지킨다.

- AI 데이터 사용 동의가 없거나 철회된 사용자는 선택 AI 기능을 실행하지 않는다.
- 학습용 export에는 운영 DB의 개인정보를 그대로 넣지 않는다.
- 합성 데이터는 운영 DB에 넣지 않고 파일로 관리한다.
- 실사용 데이터를 학습에 쓰려면 비식별화, 동의, 보관 기간, 삭제 요청 대응 방침을 먼저 확정한다.
- 영상 원본, 음성 원본, 자기소개서 원문처럼 민감도가 높은 데이터는 학습 산출물에 직접 포함하지 않는다.
- 모델 출력은 사실과 추론을 구분하고, 원문에 없는 회사명·기술명·수치·경력·자격증을 추가하지 못하게 검증한다.

---

## 3. 공통 서빙과 백엔드 연동 기준

### 3.1 권장 서빙 방식

팀 공통 시연 기준은 Ollama를 우선한다. Ollama는 로컬·무료·설치 난도가 낮고, F 커뮤니티 검열에서 이미 패턴이 존재한다. 다만 D처럼 vLLM 기반 OpenAI 호환 서버 실험이 이미 있는 도메인은 실험 자산을 유지할 수 있다.

권장 우선순위는 다음과 같다.

| 우선순위 | 방식 | 용도 |
| --- | --- | --- |
| 1 | Ollama + GGUF/merge 모델 | 팀 공통 로컬 시연, 담당별 1모델 운영 |
| 2 | vLLM/TGI OpenAI 호환 서버 | GPU 서버가 필요한 D 면접 평가, 고성능 실험 |
| 3 | 외부 API(OpenAI 등) | 자체 모델 실패 시 품질 보장 fallback |
| 4 | 규칙 기반/mock/cache | API 키가 없거나 모델 서버가 꺼진 개발/데모 환경 |

### 3.2 공통 모델 서버 인터페이스

담당자별 모델 서버는 최소한 다음 인터페이스를 제공한다.

```http
GET /health
POST /generate
```

`/health`는 모델 상태와 버전을 반환한다.

```json
{
  "domain": "E",
  "model": "careertuner-e-correction",
  "base_model": "qwen3-8b-instruct",
  "adapter": "e-correction-lora",
  "available": true,
  "version": "v1",
  "served_by": "ollama",
  "updated_at": "2026-06-16T09:00:00+09:00"
}
```

`/generate`는 `task_type`과 입력 JSON을 받아 담당 모델의 구조화 결과를 반환한다.

```json
{
  "request_id": "ct-e-20260616-0001",
  "domain": "E",
  "task_type": "E_SELF_INTRO_CORRECTION",
  "input": {
    "question": "지원 동기를 작성해주세요.",
    "original_text": "저는 귀사의 성장 가능성을 보고 지원했습니다.",
    "job_context": {
      "company_name": "예시기업",
      "job_title": "백엔드 개발자",
      "required_skills": ["Java", "Spring Boot", "MySQL"]
    }
  },
  "options": {
    "strict_json": true,
    "max_output_tokens": 1600,
    "temperature": 0.2
  }
}
```

응답은 다음 형태를 권장한다.

```json
{
  "success": true,
  "model": "careertuner-e-correction",
  "provider": "oss",
  "task_type": "E_SELF_INTRO_CORRECTION",
  "output": {
    "status": "SUCCESS",
    "confidence": 0.84
  },
  "usage": {
    "input_tokens": 820,
    "output_tokens": 430,
    "total_tokens": 1250
  },
  "latency_ms": 2430
}
```

### 3.3 백엔드 설정 목표

현재 `application.yaml`에는 `careertuner.openai.*`, `careertuner.interview.eval.*`, `ai.ollama.*`가 있다. 담당별 자체 모델이 들어오면 다음처럼 도메인별 설정을 목표로 한다.

```yaml
ai:
  domains:
    a:
      provider: ${A_AI_PROVIDER:oss}
      base-url: ${A_AI_BASE_URL:http://localhost:11434}
      model: ${A_AI_MODEL:careertuner-a-profile}
      fallback: ${A_AI_FALLBACK:openai}
    b:
      provider: ${B_AI_PROVIDER:oss}
      base-url: ${B_AI_BASE_URL:http://localhost:11434}
      model: ${B_AI_MODEL:careertuner-b-jobposting}
      fallback: ${B_AI_FALLBACK:openai}
    c:
      provider: ${C_AI_PROVIDER:oss}
      base-url: ${C_AI_BASE_URL:http://localhost:11434}
      model: ${C_AI_MODEL:careertuner-c-career-strategy}
      fallback: ${C_AI_FALLBACK:mock}
    d:
      provider: ${D_AI_PROVIDER:oss}
      base-url: ${D_AI_BASE_URL:http://localhost:11434}
      model: ${D_AI_MODEL:careertuner-d-interview}
      fallback: ${D_AI_FALLBACK:openai}
    e:
      provider: ${E_AI_PROVIDER:oss}
      base-url: ${E_AI_BASE_URL:http://localhost:11434}
      model: ${E_AI_MODEL:careertuner-e-correction}
      fallback: ${E_AI_FALLBACK:openai}
    f:
      provider: ${F_AI_PROVIDER:oss}
      base-url: ${F_AI_BASE_URL:http://localhost:11434}
      model: ${F_AI_MODEL:gemma4}
      fallback: ${F_AI_FALLBACK:rule}
```

공통 `ai.domains` 구조를 실제로 도입하면 `backend/src/main/resources/application.yaml`, 설정 바인딩 클래스, 공통 AI client가 바뀌므로 공통 영역 합의가 필요하다.

### 3.4 백엔드 호출 구조 목표

도메인 서비스는 모델 서버를 직접 하드코딩하지 않는다. 목표 구조는 다음과 같다.

```text
<Domain>Service
→ <Domain>AiService
→ DomainPromptBuilder
→ DomainJsonValidator
→ ai/common/AiClient
→ provider(oss/openai/rule/mock/cache)
→ DomainResultMapper
→ ai_usage_log + domain result/history table
```

도메인별 구현 위치는 다음을 따른다.

| 담당 | 사용자 AI 서비스 위치 | 관리자 프롬프트 위치 |
| --- | --- | --- |
| A | `backend/src/main/java/com/careertuner/profile/ai` | `backend/src/main/java/com/careertuner/admin/prompt/profile` |
| B | `backend/src/main/java/com/careertuner/jobanalysis/ai`, `companyanalysis/ai` | `admin/prompt/jobanalysis`, `admin/prompt/companyanalysis` |
| C | `backend/src/main/java/com/careertuner/fitanalysis/ai`, `analysis/ai`, `dashboard/ai` | `admin/prompt/fitanalysis`, `admin/prompt/analytics` |
| D | `backend/src/main/java/com/careertuner/interview/ai` | `admin/prompt/interview` |
| E | `backend/src/main/java/com/careertuner/correction/ai` | `admin/prompt/correction` |
| F | `backend/src/main/java/com/careertuner/community/ai`, `support/ai` | `admin/prompt/community`, `admin/prompt/support` |

### 3.5 상태 API와 관리자 화면

통합 시연과 운영 확인을 위해 목표 상태에서는 `/api/admin/ai/status` 또는 관리자 AI 상태 화면을 둔다.

예시 응답:

```json
{
  "A": { "model": "careertuner-a-profile", "available": true, "fallback": "openai", "lastCheckedAt": "2026-06-16T09:00:00" },
  "B": { "model": "careertuner-b-jobposting", "available": true, "fallback": "openai", "lastCheckedAt": "2026-06-16T09:00:00" },
  "C": { "model": "careertuner-c-career-strategy", "available": true, "fallback": "mock", "lastCheckedAt": "2026-06-16T09:00:00" },
  "D": { "model": "careertuner-d-interview", "available": false, "fallback": "openai", "lastCheckedAt": "2026-06-16T09:00:00" },
  "E": { "model": "careertuner-e-correction", "available": true, "fallback": "openai", "lastCheckedAt": "2026-06-16T09:00:00" },
  "F": { "model": "gemma4", "available": true, "fallback": "rule", "lastCheckedAt": "2026-06-16T09:00:00" }
}
```

관리자 화면에서는 모델명, provider, 최근 성공/실패, 평균 지연 시간, fallback 발생률, 최근 검증 실패 사유를 확인할 수 있어야 한다.

---

## 4. 데이터셋과 모델 산출물 관리

### 4.1 저장 위치

도메인별 모델 산출물은 `ml/<domain>` 하위에 둔다. 현재는 `ml/interview-finetune`만 있으므로, 새 모델 작업 시 다음 구조를 권장한다.

```text
ml/
 ├─ profile-llm/          A
 ├─ jobposting-llm/       B
 ├─ career-strategy-llm/  C
 ├─ interview-finetune/   D
 ├─ correction-llm/       E
 └─ community-llm/        F
```

각 폴더는 다음 구조를 권장한다.

```text
ml/<domain>/
 ├─ README.md
 ├─ data/
 │   ├─ train.sample.jsonl
 │   ├─ eval.sample.jsonl
 │   └─ schema.json
 ├─ scripts/
 │   ├─ generate_synthetic_data.*
 │   ├─ prepare_data.*
 │   ├─ finetune_lora.*
 │   └─ evaluate.*
 ├─ model-card.md
 └─ reports/
     ├─ baseline-vs-lora.md
     └─ eval-results.md
```

대용량 원본 데이터, adapter, GGUF 파일은 repo에 바로 넣기 전에 용량과 라이선스를 확인한다. 용량이 큰 파일은 git LFS 또는 외부 보관소를 사용하고, repo에는 재현 가능한 메타데이터와 샘플만 둔다.

### 4.2 JSONL 학습 데이터 형식

합성 데이터와 평가 데이터는 공통적으로 JSONL을 사용한다. 한 줄은 하나의 학습 예시다.

```json
{
  "task_type": "E_SELF_INTRO_CORRECTION",
  "domain": "E",
  "messages": [
    {
      "role": "system",
      "content": "너는 CareerTuner의 원문 보존형 자기소개서 첨삭 모델이다. 원문에 없는 사실을 추가하지 말고 JSON만 반환한다."
    },
    {
      "role": "user",
      "content": "{\"question\":\"지원 동기\",\"original_text\":\"...\",\"job_context\":{...}}"
    },
    {
      "role": "assistant",
      "content": "{\"status\":\"SUCCESS\",\"corrected_text\":\"...\",\"changes\":[...]}"
    }
  ],
  "metadata": {
    "source": "synthetic",
    "teacher_model": "gemini-flash-lite",
    "created_at": "2026-06-16",
    "license": "synthetic-internal"
  }
}
```

### 4.3 데이터 금지 항목

학습/평가 데이터에는 다음을 넣지 않는다.

- 실제 사용자 이메일, 전화번호, 주소, 링크 토큰, 계정 식별자
- 실명이나 회사 내부 정보가 포함된 원문
- 결제 식별자, 카드 정보, 환불 계좌
- 면접 영상 원본, 음성 원본
- 공개 라이선스가 불명확한 웹 문서 원문 대량 복제

실사용 데이터 재학습은 별도 동의와 비식별화 절차를 확정한 뒤 진행한다.

---

## 5. 공통 검증 기준

모든 담당 모델은 최소한 다음 검증을 수행한다.

### 5.1 구조 검증

- 응답이 JSON으로 파싱되는지 확인한다.
- 필수 필드가 존재하는지 확인한다.
- enum 값이 허용 목록에 포함되는지 확인한다.
- 점수와 confidence가 허용 범위 안인지 확인한다.
- 리스트 길이, 문자열 길이, 중첩 객체 깊이를 제한한다.
- `status`, `errors`, `warnings`, `confidence` 필드를 공통적으로 둔다.

### 5.2 근거 검증

- 원문에 없는 회사명, 기술명, 수치, 경력, 자격증이 추가되지 않았는지 확인한다.
- 분석 결과가 참조한 근거 문장이나 원문 섹션을 저장한다.
- 기업 정보처럼 외부 출처가 필요한 내용은 출처 URL, 확인 시점, AI 추론 여부를 분리한다.
- 첨삭처럼 생성형 결과는 원문 의미가 바뀌었는지 확인한다.

### 5.3 안전 검증

- 합격 보장, 합격률 단정, 채용 차별 조장, 허위 경력 작성 유도 표현을 금지한다.
- 자기소개서·이력서 첨삭에서는 과장 표현과 거짓 경험 생성을 금지한다.
- 커뮤니티·고객센터에서는 개인정보 노출, 욕설, 광고, 악성 표현을 탐지한다.
- 영상/음성 평가는 합격/불합격 판단 근거가 아니라 태도 개선 참고자료로만 표시한다.

### 5.4 fallback 조건

다음 조건 중 하나라도 발생하면 fallback을 수행한다.

- 모델 서버 health check 실패
- timeout 또는 네트워크 오류
- JSON 파싱 실패
- 필수 필드 누락
- score/confidence 범위 오류
- 원문에 없는 사실 추가
- 금지 표현 포함
- confidence가 도메인별 기준 미만
- 동일 요청 재시도 후에도 품질 기준 미달

fallback 결과도 동일한 JSON 스키마를 통과해야 저장한다.

---

## 6. 담당별 자체 모델 요약

| 담당 | 도메인 | 자체 모델 역할 | 주요 모델/기술 후보 | 주요 출력 |
| --- | --- | --- | --- | --- |
| A | 회원/프로필/설정 | 프로필 요약, 직무군 분류, 역량 추출 | Qwen3-4B Instruct 계열 또는 팀 공통 Qwen2.5-3B | 직무군, 역량, 강점, 보완점, 프로필 완성도 |
| B | 지원 건/공고 분석 | 공고 OCR 후 문장 분류, 공고 분석 JSON 생성 | PaddleOCR, KLUE-RoBERTa, Qwen/Gemma | 필수 역량, 우대 조건, 주요 업무, 기술스택 카드 |
| C | 홈/스펙 비교/취업 분석/대시보드 | 커리어 전략, 적합도 설명, 부족 역량, 다음 행동 추천 | Qwen/Gemma 계열 C 전용 모델 | 적합도 설명, 역량 gap, 학습 로드맵, 대시보드 요약 |
| D | 가상 면접/면접 리포트 | 텍스트 면접관, 답변 평가, 비언어 평가 보조 | Qwen2.5/3B LoRA, MediaPipe, Whisper, LightGBM | 질문, 꼬리질문, 답변 채점, 리포트, 종합 인상 점수 |
| E | 첨삭/결제/크레딧 | 자기소개서·면접답변·이력서·포트폴리오 원문 보존형 첨삭 | Qwen3-8B LoRA, Qwen3-4B fallback, 규칙 기반 검증 | 개선안, 변경 이유, 위험 표현, 첨삭 이력 |
| F | 커뮤니티/고객센터/공지/알림 | 검열, 태깅, 신고분류, 면접질문 추출, 안내 챗봇 | Gemma 4, BGE-M3 | 검열 결과, 자동 태그, 신고 유형, 면접 질문, RAG 답변 |

공통 베이스 모델을 Qwen2.5-3B로 통일하는 팀 도입안은 학습 절차 통일을 위한 기본값이다. E처럼 생성 품질이 특히 중요한 첨삭 도메인은 팀 합의하에 Qwen3-8B 또는 Qwen3-4B를 도메인 예외 후보로 사용할 수 있다. 이 경우 모델 카드에 예외 사유와 서빙 사양을 명시한다.

---

## 7. A 파트 — 회원/프로필/설정 + AI 이력서·스펙 추출

### 7.1 담당 범위

A 파트는 사용자의 계정 정보, 이력서, 자기소개서 기본 정보, 기술스택, 자격증, 경력, 프로젝트, 희망 직무 등 CareerTuner의 기반 프로필 데이터를 담당한다. 이 데이터는 B의 공고 분석, C의 적합도 분석과 커리어 전략 추천, D의 면접 질문 생성, E의 첨삭 기능에서 공통으로 참조하는 원천 데이터다.

A 파트 자체 모델은 사용자 프로필 데이터를 구조화하고, 직무군 분류와 직무역량 추출을 수행한다.

### 7.2 주요 task

```text
A_PROFILE_SUMMARY
A_JOB_GROUP_CLASSIFICATION
A_SKILL_EXTRACTION
A_STRENGTH_WEAKNESS_EXTRACTION
A_PROFILE_COMPLETENESS_DIAGNOSIS
```

| task | 입력 | 출력 |
| --- | --- | --- |
| `A_PROFILE_SUMMARY` | 이력서 원문, 자기소개서 요약, 경력, 프로젝트 | 사용자 프로필 요약, 직무 방향 요약 |
| `A_JOB_GROUP_CLASSIFICATION` | 희망 직무, 기술스택, 프로젝트 설명 | 백엔드, 프론트엔드, 데이터, AI, 기획 등 직무군 후보 |
| `A_SKILL_EXTRACTION` | 이력서 원문, 프로젝트 설명, 포트폴리오 설명 | 기술명 후보, 숙련도 힌트, 근거 문장 |
| `A_STRENGTH_WEAKNESS_EXTRACTION` | 경력, 프로젝트, 자격증, 기술스택 | 강점, 보완점, 근거 |
| `A_PROFILE_COMPLETENESS_DIAGNOSIS` | 프로필 입력 상태, 필수 항목 누락 여부 | 완성도 진단, 보완 필요 항목 |

### 7.3 검증 및 후처리

- 직무군은 사전 정의 enum 안에서만 허용한다.
- 기술스택명은 서버의 기술스택 catalog와 매칭한다.
- AI가 추출한 기술명은 사용자가 확인하거나 서버 검증을 거친 뒤 기준 데이터에 반영한다.
- 숙련도 점수는 허용 범위를 벗어나면 보정하거나 폐기한다.
- 근거 문장이 없는 강점·보완점은 낮은 신뢰도로 처리한다.

### 7.4 fallback

자체 모델 호출 실패, JSON 검증 실패, 점수 범위 오류, 응답 품질 미달 시 OpenAI 또는 규칙 기반 엔진으로 fallback한다. fallback 결과도 동일한 JSON 스키마를 통과해야 저장한다.

---

## 8. B 파트 — 지원 건/공고문/공고·기업 분석 + AI 공고 분석

### 8.1 담당 범위

B 파트는 사용자가 업로드한 채용공고 이미지 또는 문서를 텍스트화하고, 공고 내용을 분석하여 지원자가 이해하기 쉬운 구조로 변환하는 영역이다. B의 결과는 C의 공고-스펙 적합도 분석, D의 예상 면접 질문 생성, E의 자기소개서 첨삭에서 공통 입력으로 사용된다.

### 8.2 처리 파이프라인

```text
공고 이미지/PDF 업로드
→ PDFBox 또는 PaddleOCR로 텍스트화
→ 문장 단위 분리
→ KLUE-RoBERTa 또는 규칙 기반 문장 분류
→ Qwen/Gemma 계열 모델이 분석 JSON 생성
→ 필수 역량, 우대 조건, 주요 업무, 기술스택 카드로 변환
→ 사용자 검수와 관리자 운영 로그로 연결
```

현재 백엔드는 텍스트 PDF는 PDFBox로 먼저 추출하고, 텍스트가 없는 PDF와 이미지는 OpenAI OCR을 사용하는 흐름을 가진다. 자체 LLM 운영안에서는 OCR 이후 문장 분류와 구조화 추출을 B 모델의 1차 목표로 둔다.

### 8.3 주요 task

```text
B_JOB_POSTING_STRUCTURE
B_REQUIRED_CONDITION_EXTRACTION
B_PREFERRED_CONDITION_EXTRACTION
B_DUTY_SUMMARY
B_TECH_STACK_CARD_GENERATION
B_INTERVIEW_POINT_EXTRACTION
```

| task | 입력 | 출력 |
| --- | --- | --- |
| `B_JOB_POSTING_STRUCTURE` | OCR 텍스트, 문장 분류 결과 | 공고 전체 구조화 JSON |
| `B_REQUIRED_CONDITION_EXTRACTION` | 필수 조건 문장 | 필수 역량, 경력 조건, 학력 조건, 언어 조건 |
| `B_PREFERRED_CONDITION_EXTRACTION` | 우대 조건 문장 | 우대 기술, 우대 경험, 자격증, 도메인 경험 |
| `B_DUTY_SUMMARY` | 담당 업무 문장 | 주요 업무 요약, 업무 키워드 |
| `B_TECH_STACK_CARD_GENERATION` | 기술 관련 문장 | 기술스택 카드, 필수/우대 구분 |
| `B_INTERVIEW_POINT_EXTRACTION` | 공고 전체 분석 결과 | 예상 질문 소재, 면접 대비 포인트 |

### 8.4 검증 및 후처리

- 필수 조건과 우대 조건의 혼동 여부를 검증한다.
- 기술스택명은 서버 catalog와 매칭한다.
- OCR 오류로 의미가 불명확한 문장은 낮은 confidence로 표시한다.
- 문장 분류 confidence가 낮은 경우 LLM 판단만으로 확정하지 않는다.
- 공고 원문에 없는 기술, 경력, 자격증을 임의로 추가하지 못하게 한다.
- 회사명, 직무명, 근무지, 고용형태 등 핵심 필드는 원문 근거를 요구한다.

### 8.5 fallback

OCR 실패 시 사용자가 직접 텍스트를 붙여 넣을 수 있게 한다. KLUE-RoBERTa 문장 분류 결과가 불안정하면 규칙 기반 키워드 분류와 LLM 구조화 결과를 함께 사용한다. LLM JSON 생성 실패 시 기존 OpenAI 기반 분석 또는 단순 키워드 추출 방식으로 fallback한다.

---

## 9. C 파트 — 홈/스펙 비교/취업 분석/대시보드 + AI 커리어 전략 추천

### 9.1 담당 범위

C 파트는 CareerTuner의 커리어 전략 판단과 사용자 대시보드 인사이트를 담당한다. A의 프로필·스펙 데이터, B의 공고 분석 결과, D의 면접 평가 결과, E의 첨삭 결과, 사용자의 학습 진행 상황을 종합하여 지원자의 현재 준비 상태와 다음 행동을 제안한다.

C 파트 자체 모델은 단순 요약 모델이 아니라 여러 도메인에서 생성된 데이터를 모아 지원 전략을 설명하는 커리어 전략 모델이다. 최종 점수와 판정은 모델이 단독으로 결정하지 않고, 서버의 점수 정책과 검증 로직을 거쳐 확정한다.

### 9.2 입력 데이터

```text
A 파트:
- 사용자 희망 직무
- 기술스택
- 경력/프로젝트 요약
- 자격증/학력
- 프로필 완성도
- 강점/보완점

B 파트:
- 공고 직무명
- 필수 역량
- 우대 조건
- 주요 업무
- 기술스택 카드
- 면접 포인트

D 파트:
- 면접 답변 점수
- 반복 약점
- 답변 근거 부족 여부
- 비언어 평가 요약

E 파트:
- 자기소개서 첨삭 결과
- 중복 표현
- 직무 연관성 부족 항목
- 과장 위험 표현

C 자체 입력:
- 최근 지원 건 수
- 평균 적합도
- 지원 직무 분포
- 학습 진행률
- 자격증 보유 현황
- 사용자의 최근 행동 로그
```

### 9.3 주요 task

```text
C_APPLICATION_STRATEGY_ANALYSIS
C_GAP_AND_ROADMAP_RECOMMENDATION
C_DASHBOARD_INSIGHT
C_CAREER_TREND_ANALYSIS
```

| task | 포함 기능 | 입력 | 출력 |
| --- | --- | --- | --- |
| `C_APPLICATION_STRATEGY_ANALYSIS` | 공고-스펙 적합도 분석, 지원 전략 | 사용자 스펙, 공고 분석, fitScore 후보 | 적합도 설명, 강점, 위험 요인, 지원 판단 근거 |
| `C_GAP_AND_ROADMAP_RECOMMENDATION` | 부족 역량 추천, 학습 로드맵, 자격증 추천 | 부족 기술, 학습 이력, 자격증 catalog | 핵심 gap, 학습 순서, 추천 자격증, 예상 기간 |
| `C_DASHBOARD_INSIGHT` | 대시보드 AI 요약 | 평균 적합도, 지원 건 수, 학습률, 면접률 | 현재 상태 요약, 경고 수준, 다음 행동 |
| `C_CAREER_TREND_ANALYSIS` | 장기 취업 경향, 다음 지원 방향 | 최근 지원 이력, 반복 부족 역량, 면접 약점 | 장기 경향, 직무 방향성, 다음 지원 우선순위 |

### 9.4 서버 검증 및 정책 보정

- `fitScore` 최종값은 LLM이 아니라 서버의 점수 정책으로 계산한다.
- 지원 판단은 `APPLY`, `COMPLEMENT_BEFORE_APPLY`, `HOLD` 같은 enum으로 제한한다.
- 필수 역량 미충족 항목이 많으면 지원 추천 문구를 제한한다.
- 합격 가능성, 합격률, 반드시 합격 같은 표현을 금지한다.
- 자격증 추천은 서버의 자격증 catalog에 존재하는 항목만 허용한다.
- 학습 로드맵은 서버의 학습 항목 catalog 또는 팀이 정의한 학습 후보 목록 안에서만 확정한다.
- 공고 원문과 사용자 프로필에 없는 기술, 프로젝트, 경력, 수치를 추가하지 못하게 한다.

### 9.5 fallback

```text
1. 최근 성공한 C 분석 결과 캐시
2. 규칙 기반 커리어 전략 엔진
3. OpenAI 또는 외부 API 기반 분석
4. mock 결과
```

---

## 10. D 파트 — 가상 면접/면접 리포트 + AI 면접관

### 10.1 담당 범위

D 파트는 가상 면접, AI 면접관, 예상 질문, 꼬리 질문, 면접 답변 채점, 면접 리포트, 비언어 평가를 담당한다. D 파트는 텍스트 기반 면접 모델과 영상·음성 기반 비언어 평가 모델을 분리하여 운용한다.

### 10.2 텍스트 면접관 모델

텍스트 면접관은 **Qwen2.5-3B-Instruct를 LoRA 파인튜닝**하여 운용한다(2026-06-19 학습 완료, train_loss 0.71). 합성 데이터는 Claude Code 워크플로우(Opus 생성·Sonnet 확대)로 직접 생성했다(Haiku API Batch 구안은 폐기). 서빙은 팀 공통 **Ollama**(merge → GGUF) 흐름을 따르며, 원격 RTX 4090에 상시 기동한다. (구안의 Qwen2.5-7B + vLLM 실험 스크립트는 폐기.) 상세 학습 절차는 `ml/interview-finetune/TRAINING.md`, 배포 토폴로지는 `docs/planning/면접 자율 에이전트 로드맵.md` 12장.

주요 task는 다음과 같다.

```text
D_EXPECTED_QUESTION_GENERATION
D_FOLLOWUP_QUESTION_GENERATION
D_INTERVIEW_DIALOGUE_CONTROL
D_ANSWER_SCORING
D_INTERVIEW_REPORT_GENERATION
D_NEXT_ACTION_PLANNER
```

1순위 task는 답변 채점이다. 질문 생성과 꼬리 질문은 공고 분석(B), 프로필(A), 커뮤니티 질문 추출(F), 이전 답변(D)을 함께 참조한다.

### 10.3 비언어 평가 모델

영상·음성 면접의 비언어 평가는 별도 자체 모델을 둔다. 이 모델은 LLM이 아니라 특징 추출과 LightGBM 평가 헤드를 결합한 구조를 목표로 한다.

```text
종료 후 음성/영상 원본 서버 전송 (동의 모달)
→ 서버 Python: MediaPipe 표정 blendshape·자세 landmark + 음성 특징(피치·속도·필러) 추출
→ LightGBM 평가 헤드
→ 종합 면접 인상 점수 예측
```

표정, 시선, 자세, 말속도 같은 코칭 지표는 기존 규칙 점수를 UI에 노출하고, 자체 모델은 규칙 지표 위에 종합 인상 점수를 얹는 역할을 담당한다.

### 10.4 개인정보 및 추론 위치

비언어 모델의 추론은 **학습과 동일한 서버 Python 코드**로 수행한다(train/serve skew 차단, ADR-006). 음성/영상 원본은 종료 후 서버로 전송하며, 전송 전 **동의 모달**로 사용자 동의를 받는다. 포트폴리오 범위이므로 온디바이스 프라이버시보다 구현 단순성·정확성을 택했다. 점수(JSON)만 저장하고 원본은 분석 후 보관하지 않는다. 피처 추출기는 Python 한 벌로 학습·추론이 공유하므로 JS 추출·ONNX 변환은 두지 않는다.

### 10.5 fallback

아바타 면접관의 얼굴 렌더링은 자체 생성이 비현실적이므로 HeyGen을 유지한다. 자체 모델이 실패하면 규칙 점수와 외부 API 기반 평가로 fallback한다.

---

## 11. E 파트 — 첨삭/결제/크레딧 + AI 답변·자소서 첨삭

### 11.1 담당 범위

E 파트는 사용자의 자기소개서, 면접 답변, 이력서 표현, 포트폴리오 설명을 분석하고 개선안을 생성하는 영역이다. 결제·크레딧과 사용량 관리도 E 파트와 연결되므로, AI 첨삭 호출 기록과 사용량 기록의 정확성이 중요하다.

E 모델은 “원문 보존형 개선 후보 생성기”다. 사용자의 경험을 새로 만들어내지 않고, 이미 존재하는 원문과 A/B/D/C의 구조화 맥락을 바탕으로 더 명확한 문장, 더 설득력 있는 근거 배치, 위험 표현 수정 방향을 제안한다.

### 11.2 모델 방향

E 파트의 주력 베이스 모델은 Qwen3-8B 계열 instruction/chat 모델을 우선 후보로 둔다. E 파트는 단순 분류보다 자기소개서와 면접 답변을 실제 문장으로 고치는 생성형 첨삭 task가 중심이므로, 3B급 초경량 모델보다 문장 생성력과 지시 이행력이 높은 8B급 모델이 유리하다.

다만 개인 PC 또는 무료 서버 사양이 부족한 경우에는 Qwen3-4B-Instruct 계열 모델을 경량 fallback 후보로 둔다. 팀 공통 베이스가 Qwen2.5-3B로 확정된 상태에서 E가 Qwen3-8B를 사용한다면, 이는 “첨삭 품질 때문에 허용한 도메인 예외”로 모델 카드에 기록한다.

E 모델명은 다음처럼 관리한다.

```text
careertuner-e-correction
```

E 파트 모델은 하나의 모델 안에서 `task_type`을 분리하여 운용한다. 자기소개서 첨삭, 면접 답변 첨삭, 이력서 표현 개선, 포트폴리오 설명 개선은 모두 원문 보존형 개선이라는 공통 성격을 가지므로, 별도 모델을 여러 개 두지 않고 E 전용 통합 첨삭 모델 1개로 관리한다.

```text
공통 또는 E 전용 베이스 모델
→ E 파트 첨삭 데이터셋으로 LoRA/QLoRA 학습
→ careertuner-e-correction adapter 생성
→ 필요 시 merge GGUF로 변환
→ Ollama에서 careertuner-e-correction 모델명으로 서빙
```

### 11.3 E 모델 우선순위

| 우선순위 | 모델/방식 | 용도 | 판단 기준 |
| --- | --- | --- | --- |
| 1 | Qwen3-8B 계열 E LoRA 모델 | E 파트 주력 첨삭 모델 | 문장 품질과 첨삭 이유의 구체성이 필요한 경우 |
| 2 | Qwen3-4B-Instruct 계열 E LoRA 모델 | 경량 자체 모델 | 개인 PC, 무료 서버, 저사양 시연 환경 |
| 3 | 팀 공통 Qwen2.5-3B E LoRA 모델 | 공통 노트북/절차 재사용 | 통일 파이프라인 검증과 빠른 콜드스타트 |
| 4 | 규칙 기반 첨삭 엔진 | 위험 표현·길이·키워드 검사 | 자체 모델 결과 검증 또는 최소 fallback |
| 5 | OpenAI 기반 첨삭 | 품질 보장용 최종 fallback | 자체 모델이 반복 실패하거나 발표 시연 안정성이 필요한 경우 |

### 11.4 주요 task

```text
E_SELF_INTRO_CORRECTION
E_INTERVIEW_ANSWER_CORRECTION
E_RESUME_EXPRESSION_IMPROVEMENT
E_PORTFOLIO_DESCRIPTION_IMPROVEMENT
E_USAGE_PLAN_RECOMMENDATION
```

| task | 입력 | 출력 |
| --- | --- | --- |
| `E_SELF_INTRO_CORRECTION` | 자기소개서 원문, 문항, 지원 직무, 공고 분석 | 개선 문장, 변경 이유, 위험 표현, 키워드 보강 |
| `E_INTERVIEW_ANSWER_CORRECTION` | 면접 질문, 답변 원문, D 평가 결과, 공고 요구 역량 | 답변 개선안, STAR 구조 보강, 핵심 근거 보강 방향 |
| `E_RESUME_EXPRESSION_IMPROVEMENT` | 이력서 bullet, 프로젝트 설명, A 키워드 | 간결한 표현, 성과 중심 표현, 정량 지표 보강 제안 |
| `E_PORTFOLIO_DESCRIPTION_IMPROVEMENT` | 포트폴리오 설명, 프로젝트 맥락, 희망 직무 | 직무 연관성 강화 문장, 기술 선택 이유 정리 |
| `E_USAGE_PLAN_RECOMMENDATION` | 사용량, 크레딧, 이용 패턴 | 요금제 또는 크레딧 사용 추천. 단 1차는 규칙 기반 권장 |

`E_USAGE_PLAN_RECOMMENDATION`은 결제 강요나 과장 추천 위험이 있으므로, 1차 구현은 규칙 기반으로 두고 LLM은 설명 문구 초안에만 제한적으로 사용한다.

### 11.5 입력 컨텍스트

E 모델은 원문만 받지 않고 다음 정보를 함께 받는다.

```text
A 프로필:
- 희망 직무
- 기술스택
- 경력/프로젝트 요약
- 자기소개서 기본 키워드
- 사용자 확정 강점

B 지원 건:
- 기업명
- 직무명
- 공고 필수/우대 조건
- 주요 업무
- 기술스택 카드
- 면접 포인트

C 분석:
- 적합도 설명
- 부족 역량
- 학습 로드맵
- 지원 판단 근거

D 면접:
- 질문 원문
- 답변 원문
- 평가 점수
- 반복 약점
- 리포트 요약

E 자체:
- 첨삭 유형
- 원문 길이
- 사용자가 원하는 톤
- 남은 크레딧
- 이전 첨삭 이력 요약
```

모든 입력은 사용자가 볼 수 있거나 서비스 내부에서 정당하게 생성한 구조화 데이터여야 한다. E 모델이 A/B/C/D 원본을 직접 수정하지 않는다.

### 11.6 출력 JSON 예시

```json
{
  "status": "SUCCESS",
  "task_type": "E_SELF_INTRO_CORRECTION",
  "corrected_text": "저는 Spring Boot 기반 프로젝트에서 인증 API와 게시글 CRUD API를 구현하며, 요구사항을 실제 동작하는 서비스 흐름으로 연결한 경험을 쌓았습니다. 이번 백엔드 개발자 공고에서 요구하는 Java, Spring Boot, MySQL 기반 API 구현 역량과 맞닿아 있어 지원했습니다.",
  "summary": "지원 동기를 직무 요구 역량과 프로젝트 경험에 연결해 구체화했습니다.",
  "changes": [
    {
      "type": "SPECIFICITY",
      "before": "React 프로젝트에서 API 연결을 담당했습니다.",
      "after": "Spring Boot 기반 프로젝트에서 인증 API와 게시글 CRUD API를 구현했습니다.",
      "reason": "역할과 구현 범위를 구체화했습니다.",
      "evidence_source": "USER_PROFILE_PROJECT"
    }
  ],
  "risk_flags": [
    {
      "type": "MISSING_METRIC",
      "severity": "LOW",
      "message": "성과 수치가 없어 설득력이 약할 수 있습니다.",
      "suggestion": "처리 시간, 오류 감소, 사용자 수 같은 실제 수치가 있으면 추가하세요."
    }
  ],
  "preserved_meaning": true,
  "added_facts": [],
  "recommended_keywords": ["Spring Boot", "API 구현", "MySQL", "요구사항 분석"],
  "confidence": 0.86
}
```

### 11.7 서버 검증 및 후처리

E 모델의 결과는 반드시 서버 검증을 거친다.

| 검증 항목 | 기준 |
| --- | --- |
| JSON 구조 | `status`, `task_type`, `corrected_text`, `changes`, `risk_flags`, `confidence` 필수 |
| 원문 보존 | 원문 의미가 바뀌면 폐기 또는 재생성 |
| 허위 사실 | 원문과 구조화 컨텍스트에 없는 회사명, 기술명, 수치, 경력, 자격증 추가 금지 |
| 위험 표현 | 과장, 허위 경력, 단정적 합격 표현, 부적절한 말투 탐지 |
| 길이 | 문항별 제한 글자 수 또는 권장 길이 초과 시 축약 제안 |
| 변경 이유 | 개선안마다 변경 이유와 근거 source 필요 |
| keyword | B 공고와 연결된 핵심 키워드가 과도하거나 부자연스럽게 반복되지 않아야 함 |
| confidence | 기준 미만이면 fallback 또는 사용자에게 낮은 신뢰도 표시 |

### 11.8 저장 정책

첨삭 결과는 원본 데이터를 덮어쓰지 않는다. 모든 결과는 별도 첨삭 이력으로 저장한다. 사용자가 선택적으로 반영할 수 있도록 원문, 개선안, 변경 이유, 위험 표시를 함께 제공한다.

저장 항목:

```text
- 사용자 ID
- 관련 지원 건 ID(nullable)
- 첨삭 유형
- 원문
- 개선안
- 변경 이유 JSON
- 위험 표현 여부
- 검증 통과 여부
- 사용한 모델명/provider
- prompt version
- fallback 여부
- token/credit 사용량
- 생성 시각
- 사용자가 반영했는지 여부
```

현재 목표 DB명은 `correction_request`이며, 실제 스키마 확정 전까지는 `TEAM_WORK_DISTRIBUTION.md`의 E 주요 DB 책임을 따른다.

### 11.9 크레딧과 사용량 연계

E는 전체 AI 사용량 조회와 결제·크레딧 흐름을 담당한다. 첨삭 모델 호출 시 다음 원칙을 따른다.

- AI 요청 성공 여부, 토큰 사용량, 모델명, provider, fallback 여부를 `ai_usage_log`에 기록한다.
- 자체 모델 호출도 사용량 로그에 남긴다. 토큰 산정이 불가능하면 추정 토큰 또는 문자 수 기반 단위를 별도 표시한다.
- 실패한 AI 요청은 기본 미차감으로 처리한다.
- 자체 모델 검증 실패 후 OpenAI fallback이 성공한 경우 최종 사용자에게 제공된 결과 기준으로 차감한다.
- 요금제 추천은 사용자의 이익을 우선하고 결제 강요처럼 보이는 표현을 금지한다.

### 11.10 E fallback 흐름

```text
1. E 자체 모델 재시도 1회
2. 경량 E 모델 또는 팀 공통 Qwen2.5-3B E adapter
3. 규칙 기반 첨삭 엔진
4. OpenAI 기반 첨삭
5. 실패 상태 저장 + 사용자 재시도 안내
```

fallback 결과도 E JSON 스키마를 통과해야 저장한다.

### 11.11 E 산출물

E 담당자는 다음 산출물을 보유한다.

```text
- e-correction.train.jsonl
- e-correction.eval.jsonl
- E JSON 스키마
- E 모델 학습 노트북 또는 스크립트
- E LoRA adapter 또는 merge GGUF 모델
- E 모델 카드
- base 모델 출력과 E 모델 출력 비교 결과
- 원문 보존/허위 사실 검증 테스트
- Spring Boot E AI 연동 코드
- 첨삭 이력 저장과 ai_usage_log 기록 검증 결과
- /correction 화면 또는 관리자 첨삭 로그 화면 시연 결과
```

---

## 12. F 파트 — 커뮤니티/고객센터/공지/알림 + AI 후기 요약·추천·문의 답변

### 12.1 담당 범위

F 파트는 커뮤니티 게시글, 댓글, 신고, 고객문의, 공지, 알림을 담당한다. F 파트 자체 모델은 커뮤니티 안전성 확보와 사용자 안내를 위한 검열, 자동 태깅, 신고분류, 면접질문추출, RAG 안내 챗봇 기능에 사용된다.

### 12.2 모델 방향

F 파트는 로컬 Gemma 4를 우선 사용한다. 게시글이 작성되면 비동기 이벤트로 로컬 Gemma 4를 호출하여 검열, 자동 태깅, 신고분류, 면접질문추출을 수행한다. 하나의 파이프라인이 `task_type`만 바꾸어 여러 작업에 재사용된다.

현재 `community/moderation`에 Ollama structured output 흐름이 구현되어 있으므로, F는 팀 공통 `ai/common` 승격의 출발점이다.

### 12.3 주요 task

```text
F_POST_MODERATION
F_AUTO_TAGGING
F_REPORT_CLASSIFICATION
F_INTERVIEW_QUESTION_EXTRACTION
F_SUPPORT_DRAFT_GENERATION
F_COMMUNITY_RAG_CHATBOT
```

| task | 입력 | 출력 |
| --- | --- | --- |
| `F_POST_MODERATION` | 게시글 제목, 본문, 댓글 | 욕설, 스팸, 광고, 악성 글 여부 |
| `F_AUTO_TAGGING` | 게시글 내용 | 직무, 기업, 면접, 자소서, 후기 태그 |
| `F_REPORT_CLASSIFICATION` | 신고 사유, 원문 | 신고 유형, 조치 필요성, confidence |
| `F_INTERVIEW_QUESTION_EXTRACTION` | 면접 후기 게시글 | 실제 면접 질문, 질문 유형, 직무 키워드 |
| `F_SUPPORT_DRAFT_GENERATION` | 고객문의 내용 | 답변 초안, 담당자 확인 필요 여부 |
| `F_COMMUNITY_RAG_CHATBOT` | 사용자 질문, 커뮤니티/공지 검색 결과 | RAG 기반 안내 답변 |

### 12.4 D 파트 연계

F 파트에서 추출한 실제 면접 질문은 D 파트의 가상면접 RAG 지식베이스로 공급한다. 이를 통해 커뮤니티 면접 후기가 단순 게시글로 끝나지 않고, 가상면접 질문 생성 품질을 높이는 데이터로 재사용된다.

### 12.5 RAG 안내 챗봇

```text
사용자 질문
→ BGE-M3 임베딩 검색
→ 관련 공지/FAQ/커뮤니티 문서 검색
→ Gemma 4가 근거 기반 답변 생성
→ 링크와 함께 안내
```

### 12.6 fallback

검열 모델 실패 시 규칙 기반 필터와 관리자 검토 큐로 fallback한다. 고객문의 답변 초안 생성 실패 시 상담원 직접 답변 흐름으로 전환한다.

---

## 13. 공통 산출물 기준

각 담당자는 최소한 다음 산출물을 제출한다.

```text
1. 담당 도메인 task 정의서
2. 학습 데이터 JSONL
3. 평가 데이터 JSONL
4. 프롬프트 템플릿
5. JSON 스키마
6. LoRA adapter 또는 merge GGUF 모델
7. 모델 카드
8. base 모델 출력과 담당 모델 출력 비교 결과
9. Spring Boot 또는 wrapper API 연결 방식
10. fallback 정책
11. 테스트 입력/출력 예시 5개 이상
12. 관리자 또는 사용자 화면 시연 캡처
```

모델 카드는 다음 내용을 포함한다.

```text
- 모델명
- 담당자
- 담당 도메인
- base model
- 학습 방식
- 학습 데이터 출처
- 주요 task
- 입력 형식
- 출력 형식
- 검증 방식
- fallback 방식
- 알려진 한계
- 라이선스 주의사항
- 실제 서비스 연결 경로
- 마지막 평가 일자
```

---

## 14. 통합 시연 기준

통합 시연은 모든 담당 모델을 동시에 상주시켜야 한다는 뜻이 아니다. 각 담당 모델은 자기 도메인의 모델 산출물과 실행 증거를 보유하고, 통합 시연에서는 핵심 모델을 선별하여 live 호출한다.

시연 기준:

```text
- C 모델은 대시보드 요약 또는 공고-스펙 적합도 분석을 live 호출한다.
- F 모델은 커뮤니티 검열 또는 자동 태깅을 live 호출한다.
- E 모델은 자기소개서 또는 면접 답변 첨삭을 live 호출한다.
- A/B/D 모델은 health check, 테스트 결과, 저장된 분석 결과, 영상 증빙 중 하나 이상으로 보여줄 수 있다.
- 모델 실패 시 fallback이 동작하는지 확인한다.
- 관리자 화면 또는 API에서 담당 모델 상태를 확인할 수 있게 한다.
```

각 담당 발표 자료에는 다음 표가 들어간다.

| 항목 | 내용 |
| --- | --- |
| before | 외부 API 또는 base 모델 결과 |
| after | 담당 LoRA 모델 결과 |
| 개선점 | 도메인 용어, JSON 안정성, 비용, 지연 시간, 근거 품질 |
| 한계 | 실패 유형, 낮은 confidence 사례, fallback 조건 |
| 서비스 연결 | 실제 API/화면/DB 저장 위치 |

---

## 15. 작업 체크리스트

### 15.1 공통

- [ ] 팀 결정표와 이 운영안의 모델 예외 사항 정합성 확인
- [ ] `ai/common` 공통 client 승격 여부 팀장 승인
- [ ] 담당별 `ml/<domain>` 폴더 생성
- [ ] 공통 JSONL 샘플과 모델 카드 템플릿 작성
- [ ] `/health`, `/generate` wrapper 인터페이스 결정
- [ ] 관리자 AI 상태 화면 또는 상태 API 범위 결정

### 15.2 E 우선 작업

- [ ] `ml/correction-llm` 폴더와 README 생성
- [ ] E task별 JSON 스키마 작성
- [ ] 자기소개서/면접 답변 before-after 합성 데이터 생성
- [ ] 원문 보존 검증 케이스 작성
- [ ] Qwen3-8B와 Qwen3-4B 중 실제 시연 가능한 모델 확정
- [ ] E 자체 모델과 OpenAI 결과 비교표 작성
- [ ] `correction_request` 저장 정책과 `ai_usage_log` 기록 방식 확정
- [ ] `/correction` 화면에서 첨삭 이력과 위험 표시를 확인할 수 있는 흐름 설계

---

## 16. 정리

이 운영안의 핵심은 “각 담당자가 자기 도메인 모델을 하나씩 실제로 운용하되, 서비스 품질은 서버 검증과 fallback으로 지킨다”는 것이다. 자체 모델은 CareerTuner의 포트폴리오 완성도를 높이는 증거이며, 외부 API는 데모 안정성을 지키는 안전망이다.

특히 E 파트는 사용자가 실제 지원 문서에 반영할 문장을 다루므로, 모델 품질·원문 보존·허위 사실 방지·첨삭 이력 저장·크레딧 차감이 모두 중요하다. Qwen3-8B 계열 모델은 E 첨삭 품질을 높이기 위한 주력 후보이고, Qwen3-4B 또는 팀 공통 Qwen2.5-3B는 저사양 환경과 공통 파이프라인 검증을 위한 fallback 후보로 운용한다.
