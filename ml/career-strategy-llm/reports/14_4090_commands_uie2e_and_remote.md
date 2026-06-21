# 4090 통합 명령 시트 — UI E2E 캡처 + 원격(Tailscale) 셋업

> 공유 RTX 4090(Windows, TeamViewer)에서 그대로 복붙해 실행하는 명령 모음.
> **PART A** = 4090 로컬에서 프런트 화면에 자체모델 설명이 뜨는 것을 **캡처**(전부 localhost, 네트워크 불필요).
> **PART B** = 시연 장소(4090 ≠ 데모 위치)에서 노트북이 4090 Ollama 를 호출하도록 **원격** 셋업.
> 개념/배경은 `reports/12`(라이브 E2E)·`reports/13`(Tailscale 런북). 이 문서는 **실행 명령 + 검증 정정본**(2026-06-21 repo 대조).

## 0. 절대 금지 (이번 작업 범위 밖)
```text
7B 학습 · 추가 데이터 생성 · GGUF 재변환 · Ollama 모델 재생성
DB 스키마 임의 변경 · D/F 담당 코드 수정 · 백엔드 구조 대규모 수정
(PART A 한정) Tailscale/방화벽/포트 개방 — PART A 는 전부 localhost
```

## 0-1. 현재 4090 상태 (이미 확인됨)
```text
dev 기준(이후 dev는 d8ebb64까지 진행 — 필요시 git pull)
Ollama: careertuner-c-career-strategy-3b:latest · /v1/models 정상
provider=oss · base-url=http://localhost:11434/v1 · bootRun/POST 39/자체모델/mock 폴백 모두 성공
```

---

# PART A — 4090 로컬 UI E2E 캡처 (네트워크 불필요)

## A-1. 상태 확인 + 모델 스모크 (UI 가기 전에 모델 경로 단독 검증)
```powershell
git rev-parse --abbrev-ref HEAD; git rev-parse --short HEAD
ollama list                                   # careertuner-c-career-strategy-3b 있어야 함
Invoke-RestMethod http://localhost:11434/v1/models
# ★ 추론 1회 스모크 — UI 전에 모델이 실제로 답하는지 확인
$body = @{ model="careertuner-c-career-strategy-3b"; messages=@(@{role="user"; content="한 문장으로 자기소개"}); max_tokens=64 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:11434/v1/chat/completions -ContentType "application/json" -Body $body
ollama ps                                      # 호출 직후 모델이 GPU 로드됐는지
```

## A-2. 백엔드 실행 (PowerShell, env → bootRun)
```powershell
$env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
$env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://localhost:11434/v1"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
$env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
cd backend; .\gradlew.bat bootRun
```
- ★ `gradlew.bat` 은 **`backend/` 안에만** 있다. 루트에서 `.\gradlew.bat -p backend bootRun` 는 **동작 안 함**. 위처럼 `cd backend` 후 실행(또는 루트에서 `.\backend\gradlew.bat bootRun`).
- 확인: `Tomcat started on port 8080` · `Started CareerTunerApplication`. (이미 떠 있으면 중복 실행 금지)
- Java 21 필요. DB(localhost:3306) 도달 안 되면 부팅이 지연된다.

## A-3. 프런트 실행 (다른 터미널)
```bat
cd frontend
npm install
npm run dev
```
- ★ `npm run dev:mock` **금지**. `dev:mock`(`vite --mode mock`)은 `.env.mock`의 `VITE_USE_MOCK=true`로 **백엔드 프록시를 우회하고 메모리 목데이터만** 반환 → 자체모델이 안 뜬다. 반드시 `npm run dev`(vite, `/api/*`를 :8080로 프록시).
- 브라우저: `http://localhost:5173`.

## A-4. UI에서 적합도 분석 확인 + 캡처
```text
1. 로그인 — ★ 실모드(npm run dev)에서는 demo@careertuner.dev 자동로그인이 안 된다(그건 mock 전용).
   백엔드 시드 실계정으로 로그인(backend/README 시드 계정 참고).
2. 지원 건 상세 → 적합도 탭. 라우트: /applications/{id}/fit (ApplicationDetailPage 의 '적합도' 탭).
   ★ application_case 39 는 그 계정 소유여야 접근된다. 39가 본인 소유가 아니거나 없으면,
   목록에 보이는 실존 지원 건 아무거나 선택(빈 화면/404 를 폴백 실패로 오인하지 말 것).
3. 적합도 분석 실행/표시 → FitAnalysisPanel + StrategyPanel.
4. 자체모델 문구 확인(아래 기준) + 화면 캡처 저장.
```
**★ 검증 기준 (정정 — 무엇이 자체모델이고 무엇이 규칙엔진인지)**
```text
[자체모델이 만드는 것]
- strategy(=fitSummary)          → StrategyPanel 의 전략 설명문
- strategyActions                → 화면에 직접 안 뜨고 StrategyPanel 의 자소서/면접 제안 유도 입력으로 쓰임
- gapRecommendations 의 reason   → ★전체가 아니라 reason 필드만, 그것도 모델 learningTaskReasons 와
                                    skill 이 매칭될 때만 교체(매칭 없으면 규칙엔진 reason 유지)
- FitAnalysisPanel 에 model 이름(careertuner-c-career-strategy-3b)+생성시각 뱃지 표시

[규칙엔진(서버 권위) — 자체모델이 못 바꿈]
- fitScore · applyDecision · matchedSkills · missingSkills · conditionMatrix
- gapRecommendations 의 skill/category/priority

[공통 합격 기준]
□ 화면 안 깨짐  □ 중국어/일본어 토큰 없음
□ 설명 JSON 에 fitScore/score/applyDecision/decision 같은 금지키 미혼입
```

## A-5. 로그 확인
```text
백엔드: provider=oss · model=careertuner-c-career-strategy-3b · status=SUCCESS · ai_usage_log 기록(model id, 토큰은 0)
폴백 시 로그: "C 적합도 OSS 자체모델 실패 → OpenAI/Mock 폴백: ..." (WARN) ← 이게 보이면 자체모델 실패→폴백
```

## A-6. (선택) 폴백 화면 — API 폴백은 이미 검증됨
Ollama 잠깐 중단 → 적합도 재실행 → 화면 안 깨지고 mock/OpenAI 폴백 캡처. **테스트 후 Ollama 정상 복구.**

## A-7. 증거 저장 (★커밋되지 않는 경로로)
```powershell
# reports/ 하위는 git 추적 대상 → 로그를 reports/run_logs/ 에 두면 실수 커밋 위험.
# out/ 는 이미 gitignore 됨 → 여기에 저장.
New-Item -ItemType Directory -Force ml\career-strategy-llm\out\run_logs | Out-Null
```
저장: bootRun 로그 / 프런트 로그 / live 응답 JSON / 화면 캡처 / `ollama ps` / ai_usage_log 확인. 민감정보(로컬 경로·계정) 커밋 금지.

---

# PART B — 원격 셋업 (시연 장소 ≠ 4090)

> ✅ **4090 점검 완료(2026-06-21):** 4090(`chanssick`)은 **Tailscale 미설치**. `application.yaml:163` 의 `localhost`(model `gemma4`, 팀 챗봇/태깅)는 **F 의 다른 장치** 주소이지 4090 이 아니다 → 그 IP 로는 C 모델을 못 부른다(거긴 C 모델 없음). 따라서 **재사용 경로는 없고, 4090 을 새로 가입시켜 새 100.x 를 발급받는 것이 유일한 경로.** 4090 은 미설치라 F tailnet 전환이 아니므로 **F 장치(localhost)와 충돌 없음.**
>
> 권장 구조: **F 기존 장치**(F tailnet·localhost 유지, C는 미사용) / **4090**(C 또는 팀 tailnet 신규 가입 → 새 100.x, endpoint `http://<4090-new-ip>:11434/v1`) / **노트북**(같은 tailnet 가입, `OSS_BASE_URL=http://<4090-new-ip>:11434/v1`).

## B-1. 4090 Tailscale 신규 설치 + Ollama 노출
```text
4090 은 공유 PC → Tailscale 설치 자체는 팀·관리자 동의 후(공통 인프라 변경). F 전환 충돌은 없음(미설치 신규).
```
1. Tailscale 설치 → `tailscale up` → **C 또는 팀 tailnet** 에 새로 가입(브라우저 로그인). `tailscale ip -4` 로 **새 100.x** 기록.
2. Ollama 를 외부(tailnet) 인터페이스에 노출 — ★PART A(localhost 캡처)를 먼저 끝낸 뒤 바꾸는 걸 권장(아래 주의):
   ```powershell
   # 기존 Ollama 트레이를 완전 종료(Quit)한 뒤, 같은 세션에서:
   $env:OLLAMA_HOST="0.0.0.0:11434"; ollama serve
   netstat -ano | findstr 11434        # 0.0.0.0:11434 LISTENING 확인
   ```
   - 영구로 두려면 `[Environment]::SetEnvironmentVariable("OLLAMA_HOST","0.0.0.0:11434","Machine")` — 단 **관리자 권한 PowerShell** 필요, 설정 후 **Ollama 트레이 완전 종료→재시작**해야 적용(실행 중 프로세스엔 반영 안 됨). 시연 종료 후 **원복**.
   - 더 좁게(권장): `0.0.0.0` 대신 `<4090-new-ip>:11434` 로 바인드 → LAN 노출 없이 tailnet 만 허용. ★주의: 이렇게 하면 **localhost(127.0.0.1) 접근이 끊겨** PART A 로컬 캡처가 안 된다. 로컬 캡처는 PART A(기본 OLLAMA_HOST)에서 먼저 끝내고, 원격 전환 시 `0.0.0.0`(localhost+tailnet 둘 다) 또는 tailnet IP 바인드를 택한다.
3. `ollama list` 로 `careertuner-c-career-strategy-3b` 확인.

## B-2. 시연 노트북
1. Tailscale 설치 → 4090 과 **같은 C 또는 팀 tailnet** 합류.
2. 도달 확인:
   ```powershell
   tailscale status                                          # 4090 보임
   Invoke-RestMethod http://<4090-new-ip>:11434/v1/models    # 모델 보이면 성공
   ```
3. 백엔드 — ★`base-url` 만 바꾸면 안 되고 **OSS env 풀세트**를 그 세션에 다시 설정(provider=oss 없으면 OSS 경로 진입 안 함):
   ```powershell
   $env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://<4090-new-ip>:11434/v1"   # B-1에서 발급된 4090 새 tailnet IP
   $env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
   $env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
   cd backend; .\gradlew.bat bootRun
   ```
4. 프런트: `cd frontend; npm install; npm run dev` → `http://localhost:5173` → A-4 와 동일 확인.
> 노트북은 인터넷(공유 DB team1_db @ AWS) + Tailscale(4090 Ollama) 둘 다 필요. DB 는 패치 완료라 부팅 OK.

## B-3. 보안 (Ollama 무인증)
```text
OLLAMA_HOST=0.0.0.0 은 4090 의 모든 인터페이스(공유 오피스 LAN/Wi-Fi 포함)에 11434 개방.
OSS api-key 도 빈값 → 같은 LAN 누구나 무인증 호출 가능. 공개 인터넷엔 닫힘(포트포워딩 안 함).
4090 은 이번에 처음 노출하는 것이므로(기존 공용 노출 아님) 범위를 좁게:
권장: 0.0.0.0 대신 <4090-new-ip>(tailnet IP) 바인드 / Tailscale ACL 로 노트북만 허용 / 시연 종료 후 OLLAMA_HOST 원복.
```

## B-4. 시연 전 리허설 체크리스트
```text
□ 4090 Tailscale 신규 설치 + C/팀 tailnet 가입 → 새 100.x 발급(tailscale ip -4)
□ 4090·노트북 둘 다 tailscale status 에 서로 보임
□ 노트북에서 http://<4090-new-ip>:11434/v1/models 에 모델 보임
□ 노트북 백엔드: PROVIDER=oss 포함 OSS env 풀세트 + bootRun 성공
□ 적합도 분석 → model=careertuner-c-career-strategy-3b, strategy=자체모델 설명
□ Ollama 잠깐 끔 → mock 폴백, 화면 안 깨짐
□ ★백업 캡처 확보(PART A 로컬 캡처) — 라이브 막혀도 발표 증거
□ 시연 종료 후 OLLAMA_HOST 원복(노출 범위 원상복구)
```

---

## 보고 형식 (작업 후)
```text
[PART A 로컬 캡처]
1.브랜치/커밋 2.ollama list/ps + 스모크 결과 3.백엔드 실행 4.프런트 실행·URL
5.로그인 계정/지원건 ID 6.적합도 실행 성공 여부 7.fitScore/applyDecision(규칙엔진) 요약
8.자체모델 표시 필드(strategy/gap reason) 확인 9.백엔드 model/status 10.ai_usage_log
11.금지키/CJK 여부 12.(했다면)폴백 화면 13.저장 경로(out/run_logs)

[PART B 원격]
14.4090 Tailscale 신규 설치·가입 tailnet(C/팀) 15.4090 새 tailnet IP 16.OLLAMA_HOST 노출 방식
17.노트북 /v1/models 도달 18.원격 bootRun+UI 자체모델 표시 19.남은 이슈(승인/원복/ACL)
```
