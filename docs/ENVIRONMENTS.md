# 환경 프로파일 (local / tailscale / aws / domain / sites)

백엔드·프런트·앱이 바라보는 호스트(DB, Ollama, API, 프런트 주소)를 **이름 하나로 전환**하는 체계.
정식 호스트 정의(단일 진실 소스)는 [`config/environments.json`](../config/environments.json)이며,
값 변경 시 `backend/src/main/resources/application-<이름>.yaml` 과 `frontend/.env.<이름>` 도 함께 갱신한다.

| 프로파일 | 용도 | DB | Ollama | API 주소 |
| --- | --- | --- | --- | --- |
| `local` | 전부 내 PC | `localhost:3306/team1` | `localhost:11434` | `http://localhost:8080` |
| `tailscale` | **팀 표준 개발** — 팀 DB + 공유 4090 | `localhost/team1_db` | `localhost:11434` | `https://careertuner-dev.example.invalid/api` |
| `aws` | AWS 통합 배포 | `localhost/team1_db` | `localhost:11434` | `https://careertuner.kro.kr/api` |
| `domain` | 도메인 운영(미확정) | `localhost/team1_db` | `localhost:11434` | `https://api.careertuner.kr` (예시) |
| `sites` | Codex Sites 보조 프런트 | AWS 프로파일 재사용 | AWS 프로파일 재사용 | 같은 출처 `/api` → Worker → `https://careertuner.kro.kr/api` |

프로파일은 **호스트 기본값 묶음**일 뿐이다 — 기존 환경변수 override(`DB_HOST`, `AI_OLLAMA_BASE_URL` 등)는
프로파일 기본값보다 항상 우선하며, 프로파일 없이 실행하면 기존 기본값 그대로 동작한다.

## 컴포넌트별 전환 방법

| 컴포넌트 | 전환 방법 | 예 |
| --- | --- | --- |
| 백엔드 (Spring) | `SPRING_PROFILES_ACTIVE=<이름>` 또는 `--spring.profiles.active=<이름>` | `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`<br>`.\gradlew.bat bootRun --args='--spring.profiles.active=tailscale'` |
| 웹 (Vite dev) | `npm run dev`(=localhost) / `dev:tailscale` / `dev:aws` | `npm run dev:tailscale` |
| 웹 (빌드) | `npm run build`(상대경로 /api) / `build:tailscale` / `build:aws` / `build:domain` | `npm run build:domain` |
| 웹 백업 (Sites) | `npm run build:sites`로 SPA와 고정 AWS API 프록시 Worker 생성 | `npm run build:sites` |
| 앱 (Capacitor) | `npm run app:tailscale` (tailscale 빌드 + `cap sync android`) 후 APK 빌드. 설치 후에는 **설정 → 계정 설정 → 서버 주소** 프리셋으로 재빌드 없이 전환 | `npm run app:tailscale && npm run mobile:apk` |
| 데스크톱 | 설정 화면 프리셋으로 서버 주소 전환(재빌드 불필요) | — |

- Vite 모드 파일: `frontend/.env.localhost` / `.env.tailscale` / `.env.aws` / `.env.domain`
  (각각 `VITE_API_BASE_URL` = 빌드에 박히는 API 절대 주소, `VITE_PROXY_TARGET` = dev 서버 `/api` 프록시 대상).
- 프런트 코드의 API 베이스는 `frontend/src/app/lib/apiBase.ts` 의 `apiBase()` 단일 소스를 쓴다.
  우선순위: **런타임 오버라이드(앱/dev 전용) → `VITE_API_BASE_URL` → 상대경로 `/api`**.
  런타임 오버라이드는 설정 화면 "서버 주소" 카드가 기록하며, APK 재빌드 없이 환경을 바꾼다.
- `domain` 의 예시 주소는 정식 도메인이 확정되면 `config/environments.json` → 프로파일 yaml → `.env.*` 순으로 교체한다.
- `sites`는 백엔드 프로파일이 아니라 프런트 배포 모드다. AWS API/DB 장애까지 대신하지 않으며,
  현재 소셜 로그인·이메일 링크와 결제 결과의 반환 주소는 AWS 운영 설정을 계속 사용한다.
  별도 Sites URL을 수동으로 사용하는 fallback이며 기본 접근 범위는 소유자 전용이다.

## 4090(공유 Ollama) 꺼짐 시 자동 폴백

백엔드의 `ai.ollama.*` 계열(커뮤니티 검열, 지원 챗봇, 관리자 초안, 커뮤니티 에이전트)은
공유 4090(`http://localhost:11434`)을 기본으로 쓰지만, **부팅 시 4090 이 응답하지 않으면
자동으로 폴백 후보(기본 `http://localhost:11434`, 로컬 Ollama)로 전환**된다.

- 동작: 부팅 시 `GET {base}/api/tags`(타임아웃 1.5초)로 후보를 순서대로 프로브해, 설정된 주소가 죽어 있고
  살아있는 폴백이 있으면 `ai.ollama.base-url`·`langchain4j.ollama.chat-model.base-url` 프로퍼티를 교체한다.
  (`backend/.../ai/common/ollama/OllamaEndpointFallbackConfig`)
- 전 후보가 죽어 있으면 아무것도 바꾸지 않는다 — 기존과 동일하게 각 도메인의 실패/폴백 경로를 탄다(보수적).
- 런타임 재조회가 필요한 호출부는 `OllamaEndpointResolver` 빈을 주입받아 `resolve()`(60초 캐시) /
  `reportFailure()`(실패 보고 → 다음 후보 재시도)를 사용할 수 있다.
- 관련 env: `AI_OLLAMA_FALLBACK_BASE_URLS`(콤마 구분 폴백 후보, 기본 `http://localhost:11434`),
  `AI_OLLAMA_FALLBACK_ENABLED`(기본 `true` — `false` 로 끄면 기존 동작 그대로).
- 주의: 부팅 후 4090 이 꺼지는 경우는 자동 전환되지 않는다(각 클라이언트가 생성 시 주소를 고정) —
  백엔드를 재시작하면 다시 프로브한다. B 분석(`B_ANALYSIS_OLLAMA_*`)·E 첨삭 등 별도 base-url 을 쓰는
  도메인은 각자의 폴백 체계를 따른다.
