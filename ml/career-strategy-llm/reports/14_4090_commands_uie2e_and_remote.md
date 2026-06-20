# 4090 통합 명령 시트 — UI E2E 캡처 + 원격(Tailscale) 셋업

> 공유 RTX 4090(Windows, TeamViewer)에서 그대로 복붙해 실행하는 명령 모음.
> **PART A** = 4090 로컬에서 프런트 화면에 자체모델 설명이 뜨는 것을 **캡처**(전부 localhost, 네트워크 불필요).
> **PART B** = 시연 장소(4090 ≠ 데모 위치)에서 노트북이 4090 Ollama 를 호출하도록 **C 전용 새 Tailscale tailnet** 셋업.
> 개념/배경은 `reports/12`(라이브 E2E)·`reports/13`(Tailscale 런북). 이 문서는 **실행 명령**만.

## 0. 절대 금지 (이번 작업 범위 밖)
```text
7B 학습 · 추가 데이터 생성 · GGUF 재변환 · Ollama 모델 재생성
DB 스키마 임의 변경 · D/F 담당 코드 수정 · 백엔드 구조 대규모 수정
(PART A 한정) Tailscale/방화벽/포트 개방 — PART A 는 전부 localhost
```

## 0-1. 현재 4090 상태 (이미 확인됨)
```text
dev @ 4406fa7 (이후 dev 는 dd0ac02 까지 진행됨 — 필요시 git pull)
Ollama: careertuner-c-career-strategy-3b:latest · /v1/models 정상
provider=oss · base-url=http://localhost:11434/v1 · bootRun/POST 39/자체모델/ mock 폴백 모두 성공
```

---

# PART A — 4090 로컬 UI E2E 캡처 (네트워크 불필요)

## A-1. 상태 확인
```bat
git rev-parse --abbrev-ref HEAD
git rev-parse --short HEAD
git status
ollama list
```
PowerShell 에서 모델 응답 확인:
```powershell
Invoke-RestMethod http://localhost:11434/v1/models
```

## A-2. 백엔드 실행 (PowerShell, env → bootRun)
```powershell
$env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
$env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://localhost:11434/v1"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
$env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
cd backend
.\gradlew.bat bootRun
```
확인: `Tomcat started on port 8080` · `Started CareerTunerApplication`. (이미 떠 있으면 중복 실행 금지)

## A-3. 프런트 실행 (다른 터미널)
```bat
cd frontend
npm install
npm run dev
```
- ★ `npm run dev:mock` **금지**(목데이터라 자체모델이 안 뜸). 반드시 `npm run dev`(백엔드 프록시).
- 브라우저: `http://localhost:5173` (포트가 다르면 Vite 출력의 Local URL 사용).

## A-4. UI에서 적합도 분석 확인 + 캡처
```text
1. 로그인(또는 데모 계정) 진입
2. 지원 건 목록 → application_case 39 상세(ApplicationDetailPage) 진입
3. 적합도 분석 실행/표시 (FitAnalysisPanel)
4. strategy(전략)·strategyActions(다음 액션)·gapRecommendations(부족역량 사유)가
   자체모델 문구로 표시되는지 확인 → 화면 캡처 저장
```
**합격 기준**
```text
□ 화면 안 깨짐
□ fitScore = 규칙엔진 값(서버 권위)으로 유지
□ applyDecision = 규칙엔진 값으로 유지
□ 설명문 = careertuner-c-career-strategy-3b 자체모델 출력 반영
□ 중국어/일본어 토큰 없음
□ 설명 JSON 에 fitScore/score/applyDecision/decision 같은 금지키 미혼입
```

## A-5. 로그 확인
백엔드 로그:
```text
provider=oss · model=careertuner-c-career-strategy-3b · status=SUCCESS
CareerAnalysisOssClient / "OSS 자체모델" 호출 로그
ai_usage_log 기록
```
GPU 로드 확인:
```bat
ollama ps
```
자체모델 호출 직후 `careertuner-c-career-strategy-3b` 가 로드돼 있어야 함.

## A-6. (선택) 폴백 화면 — API 폴백은 이미 검증됨
시간 있으면: Ollama 잠깐 중단 → 적합도 재실행 → 화면이 안 깨지고 mock/OpenAI 로 폴백되는지 캡처.
**테스트 후 Ollama 반드시 정상 복구.**

## A-7. 증거 저장 (커밋 안 함)
```text
ml/career-strategy-llm/reports/run_logs/
  backend_bootrun_live.stdout.log
  live_fit_analysis_response.json
  ui_fit_analysis_capture.png (또는 캡처 경로)
  ollama_ps.txt · ai_usage_log_check.txt
```
원본 캡처/로그에 민감정보(로컬 경로/계정)가 있으면 **Git 커밋 금지**(문서엔 경로·요약만).

---

# PART B — 원격(Tailscale) 셋업: C 전용 새 tailnet

> 시연 장소 ≠ 4090. 노트북 백엔드가 4090 Ollama 를 호출하려면 둘을 **같은 tailnet** 에 넣는다.
> ★ **C 코드/스키마 변경 0** — 노트북의 `OSS_BASE_URL` env 만 4090 의 Tailscale IP 로 바뀐다.
> ⚠️ `application.yaml` 의 `localhost` 는 **F tailnet** → 재사용 안 함. **C 새 계정/tailnet** 으로 한다.
> ⚠️ 한 머신 = 한 tailnet 활성. 4090 이 F tailnet 에 있으면 C 계정 전환 시 F 가 끊긴다(팀 조율 필요).

## B-1. 4090 (1회)
```text
1) Tailscale 설치: https://tailscale.com/download  (Windows)
2) tailscale up   → 브라우저 로그인 → ★C 새 계정/tailnet 으로 로그인
```
Ollama 를 Tailscale 인터페이스에서도 듣게(시스템 환경변수):
```powershell
[Environment]::SetEnvironmentVariable("OLLAMA_HOST","0.0.0.0:11434","Machine")
# 설정 후 Ollama 재시작(트레이 종료 후 재실행 또는 서비스 재시작). 기본 127.0.0.1 이면 외부에서 못 닿음.
```
4090 의 Tailscale IP 확인 + 모델 확인:
```powershell
tailscale ip -4          # 100.x.y.z (기기별 고정) — 이 값을 노트북에 알려준다
ollama list              # careertuner-c-career-strategy-3b 있어야 함
```
> 더 사설로: `OLLAMA_HOST=<4090-Tailscale-IP>:11434` 로 두면 Tailscale 에만 바인딩(LAN 차단).

## B-2. 시연 노트북
```text
1) Tailscale 설치 → tailscale up → 4090 과 같은 C 계정/tailnet 으로 로그인
```
연결 확인:
```powershell
tailscale status                                          # 4090 기기가 보여야 함
Invoke-RestMethod http://<4090-Tailscale-IP>:11434/v1/models   # 모델 보이면 성공
```
백엔드 env (4090 IP 만 바뀜) → bootRun:
```powershell
$env:CAREERTUNER_ANALYSIS_AI_PROVIDER="oss"
$env:CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL="http://<4090-Tailscale-IP>:11434/v1"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MODEL="careertuner-c-career-strategy-3b"
$env:CAREERTUNER_ANALYSIS_AI_OSS_MAX_TOKENS="1280"
$env:CAREERTUNER_ANALYSIS_AI_OSS_TEMPERATURE="0.2"
cd backend; .\gradlew.bat bootRun
```
프런트:
```bat
cd frontend
npm install
npm run dev
```
→ `http://localhost:5173` → A-4 와 동일하게 적합도 분석 → 자체모델 설명 확인.
> 노트북은 인터넷(공유 DB team1_db @ AWS) + Tailscale(4090 Ollama) 둘 다 필요. DB 는 패치 완료라 부팅 OK.

## B-3. 보안 (Ollama 무인증)
```text
0.0.0.0 바인딩 = 4090 의 모든 인터페이스(LAN 포함)에 11434 개방.
공개 인터넷엔 안 열림(포트포워딩 안 함) — Tailscale ACL(같은 tailnet 만)이 접근 경계.
더 좁히려면: OLLAMA_HOST 를 Tailscale IP 로 바인딩 / ACL 로 노트북만 11434 허용 / 4090 방화벽에서 11434 를 Tailscale 인터페이스로만 허용.
```

## B-4. 시연 전 리허설 체크리스트
```text
□ 4090·노트북 둘 다 tailscale status 에 서로 보임
□ 노트북에서 http://<4090-ts-ip>:11434/v1/models 에 모델 보임
□ 노트북 백엔드 bootRun 성공
□ 적합도 분석 → model=careertuner-c-career-strategy-3b, strategy=자체모델 설명
□ Ollama 잠깐 끔 → mock 폴백, 화면 안 깨짐
□ ★백업 캡처 확보(라이브 막혀도 발표 증거) — PART A 캡처가 이 백업이 된다
```

---

## 보고 형식 (작업 후)
```text
[PART A 로컬 캡처]
1. 브랜치/커밋   2. ollama list/ps   3. 백엔드 실행 결과   4. 프런트 실행 결과·URL
5. 테스트 계정/지원건(application_case 39)   6. 적합도 실행 성공 여부
7. 화면 fitScore/applyDecision 요약   8. 자체모델 설명 표시 여부
9. 백엔드 로그 model/status   10. ai_usage_log 기록 여부   11. 금지키/CJK 여부
12. (했다면) 폴백 화면 결과   13. 저장한 로그/캡처 경로

[PART B 원격 — 실제 셋업했다면]
14. 4090 Tailscale IP   15. 노트북에서 /v1/models 도달 여부
16. 원격 bootRun + UI 자체모델 표시 여부   17. 남은 이슈(ACL/F tailnet 충돌 등)
```
