# 환경 프로파일과 클라이언트 연결

> 최종 대조 기준: PR #431 merge `82ada254` (2026-07-14)

이 문서는 local·Tailscale·AWS·domain·Sites 환경의 **역할과 전환 방법**을 설명한다. 주소나 장비 IP를 문서 본문에 복제하지 않고 다음 설정을 기준으로 확인한다.

| 대상 | 런타임 기준 |
| --- | --- |
| 공통 환경 목록 | [`config/environments.json`](../config/environments.json) |
| Spring 기본/환경변수 계약 | [`backend/src/main/resources/application.yaml`](../backend/src/main/resources/application.yaml) |
| Spring 프로파일 override | `backend/src/main/resources/application-<profile>.yaml` |
| Vite 빌드·dev 모드 | `frontend/.env.<mode>` |
| 웹/네이티브 API 선택 | [`frontend/src/app/lib/apiBase.ts`](../frontend/src/app/lib/apiBase.ts), [`frontend/src/features/settings/lib/serverAddress.ts`](../frontend/src/features/settings/lib/serverAddress.ts) |
| desktop API 선택 | [`desktop/core/SettingsStore.h`](../desktop/core/SettingsStore.h) |
| 운영 강제값·전체 env 주입 | [`docker-compose.prod.yml`](../docker-compose.prod.yml), EC2 비공개 환경 파일 |

`config/environments.json`은 사람이 환경 구성을 한눈에 보는 공통 인덱스이며 애플리케이션이 직접 읽지는 않는다. **실제 런타임에서는 환경변수/운영 Compose → 활성 Spring 프로파일 또는 Vite mode → 비민감 공통 기본값** 순으로 적용된다. 원격 DB 주소·계정·비밀번호에는 커밋 기본값이 없으며 `DB_SSL_MODE=REQUIRED`가 기본이다. 인덱스와 런타임 파일이 다르면 런타임 설정이 현재 동작의 기준이며, 변경 PR에서 두 쪽을 함께 맞춰야 한다.

## 프로파일 역할

| 이름 | 현재 용도 | 주의점 |
| --- | --- | --- |
| `local` | 백엔드·DB·Ollama를 개발 PC에서 실행 | 웹은 상대 `/api`와 Vite proxy 사용. 네이티브 기기의 `localhost`는 개발 PC가 아님 |
| `tailscale` | 팀 DB, 공유 개발 AI, HTTPS 개발 진입점을 조합한 팀 개발 환경 | 내부 호스트는 `config/environments.json`과 각 프로파일에서 확인. tailnet 접속과 장비 가용성 필요 |
| `aws` | 현재 운영 웹/API 통합 origin | 운영 Compose가 `aws`를 강제하고 EC2 환경 파일이 DB·AI·공급자 값을 최종 결정 |
| `domain` | 향후 별도 도메인 구성을 위한 플레이스홀더 | 현재 운영 프로파일이 아님. 예시 도메인을 운영 주소로 간주하지 않음 |
| `sites` | Sites용 프런트 빌드 모드 | Spring 프로파일이나 별도 백엔드가 아님. Worker가 운영 API를 우선 호출하고 장애 시에만 mock 체험 제공 |

2026-07-14 공개 AWS 상태 확인에서 `/api/health/ready`는 서비스·DB 모두 `UP`,
`/api/auth/oauth/providers`는 Google·Kakao·Naver를 모두 활성으로 반환했다. 같은 날 프로젝트 책임자가
세 공급자의 동의 화면→AWS callback→CareerTuner 세션 발급, Claude Haiku·OpenAI GPT 비-mock 출력과
provider 귀속, 실제 단말 SMS 수신·코드 인증·재요청/제한 시나리오를 수동으로 완주했다. 민감한 계정·토큰·
응답 원문·전화번호는 저장소에 기록하지 않으며 자세한 범위와 재검증 조건은
[`verification/DEMO_READINESS_LEDGER.md`](verification/DEMO_READINESS_LEDGER.md)를 따른다.

Apple 개발자 기능은 활성화해 테스트 중이다. Team ID 발급과 `IOS_APP_LINK_TEAM_IDS` 기반 운영 Universal Link
전환은 추후 범위이며, 현재 AASA deny-all은 안전 기본값이다. 이 후속 항목은 웹·Android·데스크톱 시연의
차단 조건이 아니다.

## 컴포넌트별 전환

| 컴포넌트 | 명령/설정 | 동작 |
| --- | --- | --- |
| 백엔드 | `SPRING_PROFILES_ACTIVE=<profile>` 또는 `--spring.profiles.active=<profile>` | `local`, `tailscale`, `aws`, `domain` 프로파일 사용 |
| 로컬 웹 | `npm run dev` | 상대 `/api`를 로컬 백엔드로 proxy |
| Tailscale/AWS dev | `npm run dev:tailscale`, `npm run dev:aws` | 해당 `.env.*`의 proxy/API 설정 사용 |
| 웹 빌드 | `npm run build`, `build:tailscale`, `build:aws`, `build:domain` | 웹/네이티브 용도에 맞는 API base를 번들에 포함 |
| Android live | `npm run build:aws` 후 `npm run native:sync -- android` | 운영 API 절대 주소 사용. release workflow의 live 모드가 이 경로를 검증 |
| Android 개발 | `npm run app:tailscale` | Tailscale 빌드 후 Android sync |
| Android/iOS mock | `npm run mobile:sync`, `npm run ios:sync` | 백엔드 없는 정적 mock 번들을 네이티브 프로젝트에 sync |
| Sites | `npm run dev:sites`, `npm run build:sites` | 같은 origin `/api`를 Worker가 운영 API로 proxy |
| Windows desktop | 앱 설정의 운영/로컬/Tailscale/custom 프리셋 | 재빌드 없이 API origin 변경. 변경 시 토큰 삭제 후 재로그인 |

백엔드 실행 예시:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
SPRING_PROFILES_ACTIVE=tailscale ./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

## 웹과 네이티브의 API base

프런트의 단일 진입점은 `apiBase()`다.

1. 네이티브 앱 또는 dev 웹에서 허용된 런타임 override
2. 빌드 시 `VITE_API_BASE_URL`
3. 값이 없으면 상대경로 `/api`

운영 웹은 Nginx와 API가 같은 origin이므로 기본 빌드의 상대 `/api`를 사용한다. Android live 번들은 WebView origin이 운영 도메인이 아니므로 `build:aws`의 절대 API 주소가 필요하다.

서버 주소 설정은 다음 보안 경계를 지킨다.

- production 웹에서는 런타임 override를 허용하지 않는다.
- custom 주소는 HTTPS만 허용하고, HTTP는 loopback 또는 명시적 개발 허용 설정에서만 쓴다.
- 저장 주소가 안전하지 않거나 서버가 바뀌면 기존 access/refresh token을 삭제한다.
- 네이티브 기기에서 개발 PC를 사용할 때는 `localhost` 대신 기기가 접근 가능한 안전한 개발 주소가 필요하다.

## desktop 연결

desktop은 Capacitor/WebView가 아니라 Qt/C++ 클라이언트다. 기본값은 운영 API이며 설정 화면에서 운영·로컬·Tailscale·custom origin으로 바꿀 수 있다.

- custom origin은 path/query/fragment/user-info 없는 HTTPS 주소만 허용한다.
- HTTP는 loopback만 허용한다.
- origin 변경 시 보관 토큰을 삭제한다.
- 웹 열기 주소는 `CAREERTUNER_WEB_APP_URL`로 별도 지정할 수 있고, 로컬 API의 기본 웹 주소는 개발 웹 포트로 보정한다.

desktop preset의 실제 주소는 `SettingsStore.h`가 단일 소스다. 내부 개발 호스트를 이 문서에 중복 기재하지 않는다.

## Sites와 장애 demo

Sites는 별도 진입점인 보조 프런트다.

- `VITE_USE_MOCK=false`, `VITE_ENABLE_OUTAGE_FALLBACK=true`로 빌드한다.
- 브라우저는 같은 origin의 `/api`를 호출하고 Worker가 고정된 운영 origin으로 전달한다.
- 실제 요청이 네트워크 오류 또는 502/503/504를 받은 뒤, Worker의 `/__backup/health`가 운영 `/api/health/ready` 실패까지 확인해야 `outage-demo`로 전환한다.
- 첫 쓰기 요청의 결과가 불명확하면 자동 성공으로 바꾸지 않는다. 장애 demo에 들어간 뒤 사용자가 다시 시도한 변경만 비영속 mock으로 처리한다.
- 정상 복구를 확인하면 페이지를 다시 불러 실제 데이터로 돌아간다.
- 결제·구독·환불·크레딧·리워드 등 금융성 mutation은 정상 연결 중에도 Worker와 백엔드 정책으로 차단한다.
- 소셜 OAuth는 운영 API가 정상일 때만 사용하고 outage demo에서는 차단한다.

Sites는 운영 DNS 자동 전환, 백엔드/DB 복제 또는 영속 장애 저장소가 아니다. 배포 주소와 공개 범위는 Sites 배포 설정에서 관리하며 환경 인덱스의 URL은 현재 게시 위치를 기록하는 값일 뿐이다.

## Ollama endpoint와 폴백

내부 장비 주소는 환경별 설정과 비공개 운영 환경 파일에서 관리한다. 프로파일의 의미는 다음과 같다.

- `local`: 로컬 Ollama를 기본/폴백 후보로 사용한다.
- `tailscale`·`domain`: 설정된 공유 endpoint를 우선하고 로컬 후보를 둘 수 있다.
- `aws`: `application-aws.yaml`의 안전한 기본값보다 EC2의 `AI_OLLAMA_BASE_URL`과 관련 도메인별 변수가 우선한다. `config/environments.json`의 장비 표기는 구성 인덱스이지 운영 가용성 보장이 아니다.

공통 `ai.ollama.*` 경로는 부팅 시 `GET {base}/api/tags`로 설정 endpoint와 `AI_OLLAMA_FALLBACK_BASE_URLS` 후보를 짧게 확인한다.

- 설정 endpoint가 살아 있으면 그대로 사용한다.
- 설정 endpoint가 죽고 후보가 살아 있으면 빈 생성 전에 `ai.ollama.base-url`과, 같은 주소를 공유한 경우 LangChain4j base URL을 교체한다.
- 모든 후보가 죽으면 설정값을 유지해 각 도메인의 기존 실패/공급자 폴백 경로를 탄다.
- 이 동작은 `AI_OLLAMA_FALLBACK_ENABLED=false`로 끌 수 있다.
- 부팅 후 endpoint가 바뀌어도 생성 시 주소를 고정한 클라이언트는 자동 전환되지 않는다. 현재 요청 시점 resolver 재시도는 이를 명시적으로 사용하는 호출부에만 적용된다.

B 분석, C 전략, D 면접 평가, E 첨삭 등 전용 base URL을 쓰는 경로는 공통 `ai.ollama.*`와 별개다. 해당 모듈 설정과 공급자 폴백을 확인한다.

## 변경 체크리스트

환경 주소나 프로파일을 바꿀 때 다음을 한 PR에서 함께 확인한다.

- [ ] `config/environments.json`의 공통 인덱스
- [ ] 관련 `application-<profile>.yaml`
- [ ] 관련 `frontend/.env.<mode>`
- [ ] 모바일/desktop preset source
- [ ] 운영 Compose와 workflow의 강제값
- [ ] OAuth·이메일·결제 반환 주소와 공급자 콘솔 등록값
- [ ] 기존 서버 토큰을 새 서버로 보내지 않는지
- [ ] [`frontend/scripts/test-mobile-platform.mjs`](../frontend/scripts/test-mobile-platform.mjs)
- [ ] [`frontend/scripts/test-sites-worker-outage.mjs`](../frontend/scripts/test-sites-worker-outage.mjs)
