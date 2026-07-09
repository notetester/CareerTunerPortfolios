# 실데이터 구동 런북 — mock 데모 → 실제 백엔드

> 지금 APK·웹 데모는 전부 **mock**(가짜 데이터, 아무 이메일로 로그인되는 "김데모")으로 돈다.
> 이 문서는 그걸 **실제 백엔드 + 실제 AI**로 구동하는 절차다. 팀 전체가 읽는 기준 문서.
>
> 관련 문서: APK 빌드 자체는 [frontend/MOBILE_BUILD.md](../frontend/MOBILE_BUILD.md)·[docs/RELEASE.md](RELEASE.md), 오케스트레이터는 [docs/AI_ORCHESTRATOR.md](AI_ORCHESTRATOR.md).

## 0. 가장 먼저 — 키는 백엔드에, APK는 화면일 뿐

가장 많이 헷갈리는 지점부터 정리한다.

```
[APK = 화면 껍데기]  ──HTTPS──>  [백엔드 = AI 키 보유]  ──>  [Claude / OpenAI / 자체LLM]
   VITE_API_BASE_URL이               ANTHROPIC_API_KEY 등             실제 AI 응답
   가리키는 주소                     여기에만 존재
```

- **API 키를 APK·프론트·git에 절대 넣지 않는다.** APK는 디컴파일로 까볼 수 있어서 키를 넣으면 그날 유출된다.
- 키는 **백엔드 환경변수**로만 주입한다. APK는 "키를 든 백엔드"의 주소만 가리키면 된다.
- 그래서 "실데이터 구동"의 본질은 **백엔드를 폰이 닿는 곳에 띄우고 + 키를 주입하고 + 앱이 그 주소를 보게 빌드**하는 것이다. APK 자체를 고치는 일이 아니다.

## 1. 출발점 — C(팀장)가 이미 깔아둔 것

바닥부터 만드는 게 아니다. 대부분 되어 있다.

| 항목 | 상태 | 위치 |
| --- | --- | --- |
| Capacitor 패키징 + PWA + 네이티브 셸 | ✅ | `frontend/capacitor.config.json` |
| APK 자동 빌드 CI (`demo-apk-N` 태그 push) | ✅ (mock) | `.github/workflows/android-release.yml` |
| 웹 데모 자동 배포(GitHub Pages) | ✅ (mock) | `.github/workflows/deploy-demo.yml` |
| **CORS** — `capacitor://localhost` 등 허용, Bearer 토큰 OK | ✅ | `common/config/SecurityConfig.java` |
| 서버 `0.0.0.0:8080` 외부 접근 가능 | ✅ | `application.yaml` |
| **DB 이미 원격 AWS**(`localhost`) | ✅ | 백엔드를 어디서 띄워도 같은 DB |
| Docker / docker-compose (백엔드+Qdrant) | ✅ | `backend/Dockerfile`, `docker-compose.yml` |
| AI 키 환경변수 분리 | ✅ | `OPENAI_API_KEY` 등 미주입 상태 |

**우리가 추가로 할 일은 3가지뿐:** ① 실데이터로 빌드(아래 스위치) ② 백엔드를 도달 가능하게 띄우기 ③ 키 주입.

## 2. mock ↔ 실데이터 스위치 2개

프론트 빌드에서 이 두 환경변수가 전부다. (`frontend/src/app/lib/api.ts`)

| 변수 | mock 모드 | 실데이터 모드 |
| --- | --- | --- |
| `VITE_USE_MOCK` | `true` | **`false`** (또는 미설정) |
| `VITE_API_BASE_URL` | (무시) | 웹: 미설정(`/api` 프록시) · **APK: 백엔드 절대 URL 필수** |

- `VITE_USE_MOCK=true`면 네트워크 없이 가짜 응답. C의 데모/APK가 전부 이 모드(`--mode mock`).
- 실데이터는 `VITE_USE_MOCK`을 끄고, **APK일 때만** `VITE_API_BASE_URL`에 백엔드 주소를 박는다. (웹은 Vite 프록시라 안 박아도 됨)

> ⚠️ APK에 들어가도 되는 값(공개): `VITE_API_BASE_URL`, `VITE_USE_MOCK`, `VITE_VAPID_PUBLIC_KEY`, `VITE_TOSS_CLIENT_KEY`(클라이언트 키). **백엔드에만 둘 값(비밀):** AI 키, 토스 시크릿, DB 비번.

---

## 3. [1단계] 웹에서 실데이터 검증 — 제일 먼저 ★

**APK부터 만들지 마라.** mock은 모든 화면을 채우지만 실제 `team1_db`는 일부만 차 있다(회사분석 ~35%, 면접평가 59건 수준). 실데이터로 켜면 빈 화면·에러가 나는 곳이 있고, 그걸 **폰에서 디버깅하면 지옥**이다. 브라우저에서 먼저 잡는다.

### 3-1. 백엔드를 키와 함께 띄운다

키 주입 방식 택1. (우리 팀은 `run-local.bat`이 이미 키 주입 + `bootRun`이라 그걸 쓰면 됨)

```bash
# 방법 A) Docker (권장 — Qdrant까지 함께)
OPENAI_API_KEY=sk-... ANTHROPIC_API_KEY=sk-ant-... docker compose up -d

# 방법 B) Gradle 직접
cd backend
OPENAI_API_KEY=sk-... ANTHROPIC_API_KEY=sk-ant-... ./gradlew bootRun
```

- `OPENAI_API_KEY`는 **사실상 필수**(최종 폴백). `ANTHROPIC_API_KEY`는 권장(1차 폴백 Haiku, 빠르고 쌈).
- DB는 기본값이 원격(`localhost`)이라 `DB_HOST` 안 줘도 붙는다.

### 3-2. 프론트를 mock 끄고 띄운다

```bash
cd frontend
npm run dev      # /api → Vite 프록시 → localhost:8080. VITE_USE_MOCK 미설정이라 실데이터.
```

> `npm run dev:mock`(mock)과 헷갈리지 말 것. 그냥 `npm run dev`가 실데이터다.

### 3-3. 점검 체크리스트

- [ ] 진짜 계정으로 로그인되나 (mock의 "김데모" 아님 — `admin@careertuner.dev` / `Career1234!` 등 실계정)
- [ ] AI 기능이 실제로 호출되나 (면접 질문 생성, 적합도 분석 등 — 응답이 mock 고정문구가 아닌지)
- [ ] **빈 화면 목록 작성** — mock엔 있는데 실 DB엔 없어서 비는 화면. 이게 다음 할 일(데이터 시드)의 입력이 된다.
- [ ] 콘솔에 CORS / 401 / 500 에러 없는지

### 3-4. 검증 결과 (2026-06-24, 이 PC 실측)

로컬 백엔드(`run-local.bat`) + 원격 DB(`team1_db`) + 실 AI 키로 기동해 확인:

| 항목 | 결과 |
| --- | --- |
| 실 로그인 | ✅ `jiwon.kim` JWT 발급(256자) — mock "김데모" 아님 |
| 실데이터 | ✅ 지원건 **16건** 조회됨 |
| AI 실호출 | ✅ autoprep 두뇌가 "네이버 백엔드 신입…"을 LLM 파싱 → `company=네이버`, `jobTitle=백엔드 개발자`, `nextAsk=MODE`까지 설계대로 |

→ **백엔드 + 원격DB + 실 AI 모두 실동작 확인. mock 아님 확정.** 4090 없이 로컬로 1단계 통과(이 PC에서 DB 접속되므로). 도메인별 빈 화면 갭은 브라우저 전수 확인으로 이어 점검.

---

**이 단계가 끝나야 APK가 의미 있다.** 웹에서 실데이터가 도는 걸 확인한 뒤 APK로 간다.

---

## 4. [2단계] 4090에 백엔드 상주 + 폰으로 보기 ★ (우리 현실)

AWS 가기 전, **이미 상시 돌아가는 4090**을 백엔드 서버로 쓴다. 이게 cloudflared 터널보다 낫다:

- **자체 LLM(Ollama)이 4090에 있다** → 백엔드를 4090에서 띄우면 자체모델 호출이 `localhost`라 초고속. 폴백 체인의 "자체LLM" 1순위가 제값을 한다.
- **Tailscale 고정 IP**(`localhost`) → 터널처럼 URL이 안 바뀐다. 한 번 빌드하면 끝.
- 추가 비용 0. DB는 원격 AWS 그대로.

### 4-1. 4090에서 백엔드 기동 (TeamViewer)

4090은 SSH 미설정이라 TeamViewer로 접속해 명령을 붙여넣는다.

```bash
# 4090 안에서 — 자체 LLM이 같은 머신이므로 Tailscale 우회를 제거
set OPENAI_API_KEY=sk-...
set ANTHROPIC_API_KEY=sk-ant-...
set AI_OLLAMA_BASE_URL=http://localhost:11434      # ← 핵심: 4090 로컬 Ollama 직결
gradlew bootRun                                     # 또는 docker compose up -d
```

> 시연 중엔 GPU 학습을 멈춰 백엔드와 충돌(메모리/VRAM 경합)을 막는다.

### 4-2. (권장) HTTPS — `tailscale serve`

```bash
tailscale serve --bg 8080
#   → https://<머신>.<tailnet>.ts.net 으로 8080을 HTTPS 노출 (Tailscale 내부)
```

HTTPS면 Capacitor의 cleartext 차단 이슈가 없다. 안 쓰면 평문 `http://localhost:8080` + `allowMixedContent:true` 조합.

### 4-3. APK 빌드 — 로컬 대안 (Android SDK 필요, 평소엔 4-5 사용)

`frontend/.env.local` (git 미추적, OS 무관):

```
VITE_USE_MOCK=false
VITE_API_BASE_URL=https://<머신>.<tailnet>.ts.net/api
#   HTTPS 안 쓰면:  http://localhost:8080/api
```

```bash
cd frontend && npm run build && npx cap sync android && cd android && ./gradlew assembleDebug
#   → android/app/build/outputs/apk/debug/app-debug.apk
```

> 이 PC에 Android SDK가 없으면 로컬 빌드가 안 된다 → 4-5의 CI 경로를 쓴다.

### 4-4. 폰에 넣고 실행 — Flutter 때와 다른 핵심

백엔드가 **Tailscale 사설망 안**(`100.x`)에 있는 게 공개 백엔드와의 결정적 차이다. 순서:

1. **폰을 같은 Tailscale에 넣는다** — 폰에 Tailscale 앱 설치 + 같은 계정 로그인. ← **이게 핵심.** 안 하면 폰이 `localhost`에 못 닿아 앱이 네트워크 에러만 낸다. (공개 백엔드 보던 Flutter 땐 없던 단계)
2. **APK를 폰으로 옮긴다** — USB 복사 / OneDrive·드라이브 업로드 후 폰서 다운 / 카톡 '나에게 보내기' 중 택1.
3. **APK 설치** — 폰 파일매니저에서 `.apk` 탭 → "출처를 알 수 없는 앱" 허용 → 설치.
4. **앱 실행** → 4090 백엔드 실데이터로 동작.

### 4-5. ★ 기본 — CI로 빌드 → 폰으로 바로 받기 (C 방식 채택)

**빌드는 이 경로를 기본으로 한다.** C(팀장)가 이미 쓰던 방식이고, 로컬 Android SDK가 필요 없으며, 폰에서 바로 받는다. C의 CI가 APK를 GitHub Release에 올려준다:

```bash
git tag demo-apk-real-1 && git push origin demo-apk-real-1
#   → 약 3분 뒤 Releases에 APK 첨부 → 폰 브라우저로 그 페이지서 바로 다운로드·설치
```

단 **현재 CI는 mock 빌드**라, 실데이터 빌드 변형(`VITE_API_BASE_URL`/`VITE_USE_MOCK` 주입)을 워크플로에 추가해야 한다 = **C와 합의 필요**(`.github/workflows`는 공통영역). 그 전까진 4-3 로컬 빌드.

> 외부인(Tailscale 없는 폰)에게도 보여줘야 하면: 임시로는 `cloudflared tunnel --url http://localhost:8080`(URL 매번 바뀜), 상시는 5장(클라우드/Tailscale Funnel).

---

## 5. [3단계] 최종 제출/상시 데모 — 클라우드 상주

상시 켜두려면 백엔드를 클라우드에 올린다.

- DB가 이미 `localhost`(AWS EC2)이므로, **가능하면 같은 인스턴스/VPC에 백엔드 `docker compose up`** 하는 게 DB 레이턴시상 이상적.
- 도메인 + HTTPS(nginx 리버스 프록시 또는 ALB) 붙이면 고정 URL이 생긴다. 그 URL을 `VITE_API_BASE_URL`로 박아 APK 빌드하면 재빌드 없이 상시 동작.
- CI(`android-release.yml`)에 실데이터 빌드 변형을 추가하면 `demo-apk-N` 태그 push로 실APK 자동화 가능.

---

## 6. 주의사항 (꼭 읽기)

- **CORS에 백엔드 IP를 넣는 게 아니다(흔한 오해).** 앱이 보내는 요청의 origin은 `capacitor://localhost`(앱 WebView)이지 백엔드 주소가 아니다. 그 origin은 이미 허용돼 있다. 그래서 백엔드를 LAN/터널/클라우드 어디에 두든 **CORS는 대개 안 건드려도 된다.** 실측해서 origin이 다르게 뜨면 그때 `CORS_ALLOWED_ORIGINS` 환경변수로 추가.
- **키 보안** — 절대 git·APK·공개 repo에 넣지 않는다. 백엔드 환경변수만. 비용 부담자는 OpenAI/Anthropic 콘솔에서 **사용량 한도(usage limit)**를 걸어 폭주에 대비한다.
- **CI 워크플로(`.github/workflows/`)는 공통영역 + C(팀장) 소유.** 실데이터 APK 워크플로를 추가하려면 C와 합의. 백엔드 URL이 공개 repo에 노출되지 않게 secrets/variables로 처리(C의 `deploy-demo`엔 시크릿 스캔이 있다).
- **데이터 갭** — mock은 전 화면을 채우지만 실 `team1_db`는 비어 있는 영역이 있다. 시연 전 핵심 화면의 시드 데이터를 채워야 "실데이터인데 빈 화면" 사고를 막는다. (3-3에서 만든 빈 화면 목록이 작업 대상)

---

## 7. 명령 빠른 참조

```bash
# ── [1단계] 웹 실데이터 검증 ──
OPENAI_API_KEY=sk-... ANTHROPIC_API_KEY=sk-ant-... docker compose up -d   # 백엔드
cd frontend && npm run dev                                                # 프론트(실데이터)

# ── [2단계] 4090 백엔드 + 실데이터 APK ──
# (4090 안) set AI_OLLAMA_BASE_URL=http://localhost:11434 + 키 주입 후 gradlew bootRun
# (4090 안) tailscale serve --bg 8080            # HTTPS 노출(권장)
# 폰: Tailscale 앱 설치 + 같은 계정 로그인  ← 안 하면 100.x 백엔드에 못 닿음
# frontend/.env.local 에 VITE_API_BASE_URL=https://<머신>.<tailnet>.ts.net/api , VITE_USE_MOCK=false
cd frontend && npm run build && npx cap sync android && cd android && ./gradlew assembleDebug
# 또는 CI: git tag demo-apk-real-1 && git push  → Releases 에서 폰으로 다운(실데이터 변형은 C 합의)

# ── 모드 구분 ──
npm run dev           # 웹 · 실데이터(프록시)
npm run dev:mock      # 웹 · mock
npm run demo:apk      # APK · mock (C가 만든 원클릭)
# 실데이터 APK = .env.local(API_BASE_URL+USE_MOCK=false) 후 build → cap sync → assembleDebug
```

| 목표 | 백엔드 | 프론트 빌드 | 폰 |
| --- | --- | --- | --- |
| 웹 실데이터 검증 | 로컬 기동 + 키 | `npm run dev` | — |
| 시연 APK | **4090 + Tailscale** | `.env.local`(ts.net URL, mock=false) → build → cap sync | **Tailscale 로그인** |
| 외부 공개 | 클라우드 / Funnel | 고정 도메인 URL로 동일 | 불필요 |
