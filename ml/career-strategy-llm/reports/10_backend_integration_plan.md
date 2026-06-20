# C 백엔드 자체모델 연동 설계 (C_FIT_EXPLAIN, 구현 완료)

> 2026-06-21 · C 적합도 분석에 자체모델(`careertuner-c-career-strategy-3b`)을 1차로 연결.
> 범위: **`C_FIT_EXPLAIN`** 만(전략/로드맵 RAG/경향/7B 비교는 다음 단계). 코드는 컴파일+단위테스트 통과.
> ✅ **2026-06-21 라이브 검증 완료** — PR #95 dev 머지, 실 API 호출(model=careertuner-c-career-strategy-3b·fitScore=45·자체모델 설명)·Ollama 중단 시 mock 폴백 확인. 결과: `reports/12_live_e2e_result.md`.

## 1. 핵심 설계 — 뉴로-심볼릭 조립

`MockFitAnalysisAiService` 가 **이미 규칙엔진**이다(점수·matched·missing·지원판단·조건매트릭스·gap·로드맵·자격증을 결정론 계산). 자체모델은 점수를 만들지 않고 **설명 텍스트만** 생성하도록 학습됐다. 그래서 OSS 경로는:

```
규칙엔진(Mock)으로 골격 계산  →  골격값을 입력에 넣어 자체모델 호출(설명만)  →  병합
  · 점수/판단/매칭/부족/조건/자격증 = 규칙엔진(서버 권위)
  · 설명(strategy)·strategyActions·gap 사유 = 자체모델
```

점수/판단은 서버 값만 쓰므로, 모델이 금지키(fitScore/score/applyDecision/decision)를 내도 **결과에 반영되지 않는다(화이트리스트로 구조적 제거)**.

## 2. 연동 대상 / 새로 추가한 클래스

| 구분 | 클래스 | 변경 |
| --- | --- | --- |
| 대상(기존) | `fitanalysis.ai.FitAnalysisAiService`(인터페이스) | 그대로 |
| 대상(기존) | `fitanalysis.ai.OpenAiFitAnalysisAiService` | **@Primary 제거**(폴백 체인의 OpenAI 단계로 강등) |
| 대상(기존) | `fitanalysis.ai.MockFitAnalysisAiService` | 그대로(규칙엔진 골격으로 재사용) |
| 대상(기존) | `fitanalysis.ai.prompt.FitAnalysisPromptCatalog` | `FIT_EXPLAIN_SYSTEM_PROMPT` + `fitExplainUserPrompt(...)` 추가(학습 프롬프트와 일치) |
| **신규** | `analysis.ai.provider.CareerAnalysisAiProviderProperties` | provider 선택 + OSS 설정 바인딩 |
| **신규** | `analysis.ai.provider.CareerAnalysisOssClient` | Ollama OpenAI 호환 `/chat/completions` 호출(D `OssLlmGateway` 미러) |
| **신규** | `fitanalysis.ai.OssFitAnalysisAiService` | 뉴로-심볼릭 조립(Mock 골격 + 모델 설명) |
| **신규**(@Primary) | `fitanalysis.ai.FallbackFitAnalysisAiService` | **OSS→OpenAI→Mock** 디스패처 |
| 테스트 | `OssFitAnalysisAiServiceTest`, `FallbackFitAnalysisAiServiceTest` | 신규 |
| 설정 | `backend/.../application.yaml` | `careertuner.analysis.ai.*` 추가(**추적됨** — 팀이 dev 기본값을 커밋하는 관례. base-url 등 실값은 env 주입, 커밋엔 빈/기본값만) |

D 담당 `interview/service/*` 는 **참고만 하고 수정하지 않음.**

## 3. 설정 키 (`careertuner.analysis.ai.*`)

```yaml
careertuner:
  analysis:
    ai:
      provider: ${CAREERTUNER_ANALYSIS_AI_PROVIDER:openai}   # openai(기본) | oss
      oss:
        base-url: ${CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL:}    # 예: http://<4090>:11434/v1 (비면 OSS 비활성)
        model:    ${CAREERTUNER_ANALYSIS_AI_OSS_MODEL:careertuner-c-career-strategy-3b}
        api-key:  ${CAREERTUNER_ANALYSIS_AI_OSS_API_KEY:}
        max-tokens:  ${CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS:1280}
        temperature: ${CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE:0.2}
```

기본 `provider=openai` + `base-url` 비움 → **기존 동작과 100% 동일**(OSS 비활성). 자체모델은 두 값을 채워야 켜진다.

## 4. Ollama 요청/응답 예시

요청(`POST {base-url}/chat/completions`):
```json
{ "model": "careertuner-c-career-strategy-3b",
  "messages": [
    {"role":"system","content":"<FIT_EXPLAIN_SYSTEM_PROMPT>"},
    {"role":"user","content":"# 적합도 분석 입력 ... 규칙엔진 사전계산(fitScore/applyDecision/matched/missing) ..."}],
  "temperature": 0.2, "max_tokens": 1280, "response_format": {"type":"json_object"} }
```
응답에서 `choices[0].message.content` 를 꺼내 잡설 제거(`extractJsonSpan`) 후 파싱. 기대 출력:
```json
{ "fitSummary":"...", "strengths":["..."], "risks":["..."],
  "strategyActions":["..."], "learningTaskReasons":[{"skill":"...","why":"..."}] }
```

## 5. ★max token 주의 (4090 검증)

자체모델 설명 출력은 길어서 **출력 토큰이 1024 미만이면 JSON 이 잘려 parse 실패**한다(4090: max-new 512 truncation → 1024 통과). 그래서 `max-tokens` 기본값 **1280**(최소 1024, 권장 1280~1536). Ollama 는 `max_tokens` 를 `num_predict` 로 매핑한다.

## 6. 폴백 흐름 (화면 안 깨짐)

```
provider=oss && oss.available()  →  OSS 자체모델 시도
   └ 실패(HTTP/타임아웃/JSON깨짐/fitSummary 공백)  →  OpenAI 단계
OpenAI(키 있으면 호출, 없거나 실패  →  내부 Mock 폴백)  →  항상 결과 반환
```
OSS 실패는 `BusinessException` 으로 던져 디스패처가 잡고 OpenAI/Mock 으로 전환. 규칙엔진 골격은 항상 계산되므로 최악에도 Mock 수준 결과가 화면에 남는다.

## 7. 금지키 처리

모델 출력에서 **화이트리스트(fitSummary/strategyActions/learningTaskReasons)만 읽는다.** fitScore/score/applyDecision/decision 이 있어도 읽지 않아 결과에 반영되지 않음(구조적 제거). 테스트 `ignoresForbiddenScoreKeysFromModel` 가 "모델이 fitScore 999/APPLY 를 내도 결과 점수=규칙엔진 45·판단=HOLD" 를 검증.

## 8. 테스트 계획 / 결과 (통과)

| 테스트 | 검증 | 결과 |
| --- | --- | --- |
| `OssFitAnalysisAiServiceTest.mergesRuleEngineSkeletonWithModelExplanation` | 정상 JSON → FitAnalysisResult 병합(설명=모델, 점수=규칙엔진) | ✅ |
| `...ignoresForbiddenScoreKeysFromModel` | 금지키 무시(점수/판단 규칙엔진 유지) | ✅ |
| `...throwsWhenModelSummaryBlank` | fitSummary 공백 → 예외(→폴백) | ✅ |
| `...propagatesWhenModelClientFails` | 모델 호출 실패 → 예외(→폴백) | ✅ |
| `FallbackFitAnalysisAiServiceTest.usesOssWhenProviderOssAndAvailable` | provider=oss+available → OSS 사용 | ✅ |
| `...fallsBackToOpenAiWhenOssFails` | OSS 실패 → OpenAI 폴백 | ✅ |
| `...usesOpenAiWhenProviderOpenai` / `skipsOssWhenBaseUrlMissing` | base-url/provider 미설정 → OpenAI, OSS 미호출 | ✅ |
| `...ossDefaultsHonorMaxTokenAndModel` | max-tokens ≥1024, 모델명·temperature 기본값 | ✅ |

`./gradlew -p backend compileTestJava test --tests com.careertuner.fitanalysis.ai.*` → **BUILD SUCCESSFUL, 18/18 통과**.
(참고: `CareerTunerApplicationTests`(@SpringBootTest)는 F 담당 `ai_moderation_setting.sanction_threshold` 컬럼이 로컬 DB에 없어 실패 — 본 변경과 무관한 사전 DB 스키마 드리프트.)

## 9. ★원격 호출 경로 — 미확정 (다음 결정 사항)

자체모델은 공유 4090 Ollama 에 있고, 백엔드가 거기에 접근하는 경로가 미정이다. `base-url` 을 하드코딩하지 않고 env 로 둔 이유. 택1 필요:
- (A) 공유 4090 Tailscale 신규 설치 → `http://<tailscale-ip>:11434/v1`
- (B) 같은 LAN 내부 IP → `http://<lan-ip>:11434/v1`
- (C) SSH 포트포워딩 → `http://localhost:11434/v1`
- (D) 데모를 공유 4090 로컬에서 직접 구동
경로 확정 후 `CAREERTUNER_ANALYSIS_AI_PROVIDER=oss` + `CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL=...` 만 주입하면 실연동된다(코드 변경 불필요).

## 10. 다음 단계
- 원격 경로 확정 → 실 4090 Ollama 라이브 호출 + 화면(StrategyPanel) 자체모델 설명 시연
- ai_usage_log 에 OSS 호출 기록(model id, fallback 여부)
- C_STRATEGY / C_LEARNING_ROADMAP(RAG) / C_TREND_SUMMARY 확장, 7B 비교
