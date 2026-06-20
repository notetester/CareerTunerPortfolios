# C 자체모델 라이브 E2E 결과 (Phase 1)

> **발표용 요약**
> C 자체모델 Phase 1은 합성 mixed 데이터 416건으로 Qwen2.5-3B를 QLoRA 학습하고, Q4_K_M GGUF로 Ollama에
> 등록한 뒤, Spring 백엔드의 OSS provider를 통해 실제 적합도 분석 API에 연결했다. 점수와 지원판단은 서버
> 규칙엔진이 유지하고, 자체모델은 설명 JSON만 생성한다. Ollama 장애 시 OpenAI/Mock 폴백으로 화면이
> 깨지지 않음을 확인했다.

## 1. 라이브 API 검증 (✅ 완료, 로그 검증)

| 항목 | 값 |
| --- | --- |
| 브랜치/커밋 | `dev @ 4406fa7` (PR #95 merge 완료) |
| Ollama 모델 | `careertuner-c-career-strategy-3b:latest` (`/v1/models` 확인) |
| provider / base-url | `oss` / `http://localhost:11434/v1` (max-tokens 1280, temperature 0.2) |
| 호출 | `POST /api/fit-analyses/application-cases/39` → `success=true`, `fitAnalysisId=25` |
| 응답 | `model=careertuner-c-career-strategy-3b`, `status=SUCCESS`, `fitScore=45` |
| **자체모델 설명** | `strategy` = "Java·MySQL을 보유하고 있지만 필수 스킬인 Spring과 REST API가 없어…" (모델 생성). gap 사유(Spring/Docker)도 모델 enrich |
| 뉴로-심볼릭 | fitScore/applyDecision(COMPLEMENT)/matched/missing/conditionMatrix = **규칙엔진**. 설명/strategyActions/gap reason = **자체모델** |
| 금지키 / CJK | top-level fitScore/applyDecision은 서버 규칙엔진 값. **모델 설명 필드에 금지키 누출 0, 중국어/일본어 누출 0** |
| ai_usage_log | OSS 호출 `careertuner-c-career-strategy-3b` model id 기록 |
| **폴백** | Ollama 중단 → 재호출 `fitAnalysisId=26`, `model=mock`, `status=SUCCESS`, **동일 fitScore=45**. 로그 `C 적합도 OSS 자체모델 실패 → OpenAI/Mock 폴백` |
| 부팅 | `Tomcat started on port 8080` + `Started CareerTunerApplication in 2.999s` (공유 DB 패치 후 정상) |

> 검증 방식: 직접 화면 대신 POST API 경로로 검증했고, 화면이 소비할 응답 envelope/데이터 계약은 정상.
> OSS 응답과 mock 폴백 응답의 차이(설명문·gap 사유가 모델 vs 규칙)가 자체모델이 실제로 동작한다는 증거.

## 2. 프런트 UI 시연 절차 (★4090에서 실행)

자체모델은 4090 Ollama에만 있으므로 **백엔드+프런트+브라우저를 모두 4090에서** 띄워야 화면에 자체모델 설명이 뜬다(전부 localhost, 원격 경로 불필요). 노트북은 모델이 없어 OSS가 mock으로 폴백된다.

**백엔드(PowerShell):**
```powershell
$env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
$env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://localhost:11434/v1"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
$env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
cd backend
.\gradlew.bat bootRun
```
**프런트(다른 터미널):**
```bash
cd frontend
npm install          # 이미 설치돼 있으면 생략
npm run dev          # ★ 실모드(백엔드 프록시). npm run dev:mock(목데이터)은 쓰지 말 것
```
- Vite dev(:5173)가 `/api/*`를 백엔드(:8080)로 프록시한다. 브라우저는 `http://localhost:5173`.
- `npm run dev:mock`은 백엔드를 안 거치고 목 데이터를 쓰므로 **자체모델이 안 뜬다**. 반드시 `npm run dev`.

## 3. UI에서 확인할 흐름
```text
1. 로그인(또는 데모 계정) 진입
2. 지원 건 목록 → application_case_id=39(또는 job_analysis가 있는 지원 건) 상세 진입(ApplicationDetailPage)
3. 적합도 분석 실행/표시 (FitAnalysisPanel)
4. 결과 화면에서 strategy(전략 설명)/strategyActions(다음 액션)/gapRecommendations(부족역량 사유)가
   자체모델 문구로 표시되는지 확인
5. 백엔드 로그에서 provider=oss / model=careertuner-c-career-strategy-3b 확인
6. ai_usage_log에 자체모델 model id 기록 확인
```
확인 기준: 화면 안 깨짐 / fitScore·applyDecision=규칙엔진 값 / 설명문=자체모델 / 중국어·일본어 누출 없음 /
모델 설명 JSON에 fitScore·score·applyDecision·decision 미혼입 / Ollama 끄면 mock·OpenAI 폴백으로 화면 유지.

## 4. 증거 저장 위치
4090 로컬(커밋 안 함 — 크거나 로컬 경로/민감정보):
```text
<4090>\...\ml\career-strategy-llm\reports\run_logs\
  backend_bootrun_live.stdout.log          (Started CareerTunerApplication, 폴백 로그)
  live_fit_analysis_response.json          (OSS 응답: model=careertuner-c-career-strategy-3b, fitScore=45)
  live_fit_analysis_fallback_response.json (폴백 응답: model=mock)
  (프런트 화면 캡처)                          (UI E2E 캡처 — 4090에서 생성)
```
이 문서에는 요약·파일 위치만 기록한다(원본 미커밋).

## 5. 4090 Codex UI E2E 지시문 (붙여넣기)
```text
이미 적합도 분석 라이브 API(자체모델)는 검증됨. 이번엔 프런트 화면에서 자체모델 설명이 뜨는 것을 캡처한다.
금지: 7B/추가학습/RAG/Tailscale/방화벽·포트개방/DB 스키마 임의변경/D·F 코드 수정.
순서:
 1) 백엔드: 위 env(provider=oss, MAX_TOKENS 1280) 후 cd backend; .\gradlew.bat bootRun (8080 기동 확인)
 2) 프런트: cd frontend; npm install; npm run dev  (★dev:mock 금지) → http://localhost:5173
 3) 로그인 → 지원 건(application_case 39) 상세 → 적합도 분석 표시
 4) 화면에서 strategy/strategyActions/gap 사유가 자체모델 문구인지 확인, 화면 캡처 저장
 5) 백엔드 로그 provider=oss·model=careertuner-c-career-strategy-3b, ai_usage_log 기록 확인
 6) (선택) Ollama 잠깐 끄고 재실행 → mock 폴백으로도 화면 안 깨지는지 캡처
보고: 백엔드/프런트 실행 결과, 적합도 화면 캡처 경로, 자체모델 설명 표시 여부, 폴백 화면 여부, 남은 이슈.
```

## 6. 남은 이슈
- 실제 프런트 화면 캡처는 4090에서 생성(노트북엔 모델 없음 → 자체모델 화면 불가).
- 공유 DB 패치 2건 `적용 이력` 갱신 + F/팀 공지 미완.
- 비-4090 환경 데모 시 원격 호출 경로(Tailscale/LAN) 결정 필요(4090-local 데모면 불필요).
