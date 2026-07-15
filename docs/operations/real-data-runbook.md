# 실데이터 구동 런북

> 현재 동작 기준: 2026-07-14 `dev` 런타임. 운영 웹과 API는 `https://careertuner.example.com`, 공개 GitHub Pages 데모만 명시적 mock이다.

CareerTuner의 프런트는 동일한 화면 코드를 live와 mock 두 모드로 실행한다. 실데이터 확인의 핵심은 “앱에 AI 키를 넣는 것”이 아니라 프런트가 올바른 백엔드 주소를 사용하고, 백엔드 환경에 DB·OAuth·AI provider 자격증명이 주입됐는지 확인하는 것이다.

```text
웹·모바일·데스크톱 ── HTTPS /api ──> Spring Boot ──> MySQL
                                           ├──────> OpenAI / Anthropic
                                           ├──────> 공유 4090 Ollama
                                           └──────> OCR·공고 추출 worker
```

- 브라우저나 APK에 들어가도 되는 값: API 주소, VAPID public key, Toss client key처럼 공개를 전제로 한 값.
- 백엔드에만 둘 값: DB 비밀번호, JWT secret, OAuth client secret, AI provider key, 결제 secret, private key.
- 주소의 단일 출처는 [`config/environments.json`](../../config/environments.json), 상세 전환 규칙은 [환경 프로파일](../ENVIRONMENTS.md)이다.

## 모드 구분

| 목적 | 프런트 명령·산출물 | 데이터 |
| --- | --- | --- |
| 로컬 풀스택 | `npm run dev` + 로컬 백엔드 | 실제 로컬 DB/API |
| AWS 백엔드 점검 | `npm run dev:aws` | 배포 API·DB |
| 운영 웹 | `npm run build` 산출물을 EC2 nginx에 배포 | 배포 API·DB |
| live Android | `npm run build:aws` 또는 `live-*` 릴리즈 | 배포 API·DB |
| 공개 데모 | `npm run dev:mock` / `build:mock` | 메모리 mock |
| Sites 백업 | `npm run build:sites` | AWS 우선, 전체 장애 확인 후 비영속 mock 체험 |

`VITE_USE_MOCK=true`일 때만 mock registry를 사용한다. live 요청 실패를 평상시에 mock 성공으로 위장하지 않는다. Sites도 실제 API 요청과 전체 health가 함께 실패한 뒤에만 outage-demo로 전환한다.

## 가장 빠른 운영 확인

```bash
curl -fsS https://careertuner.example.com/api/health
curl -fsS https://careertuner.example.com/.well-known/assetlinks.json
```

그다음 브라우저에서 실제 테스트 계정으로 로그인해 다음 경계를 확인한다.

1. 새로고침 후에도 JWT/refresh 흐름이 유지되는지
2. 지원 건 목록과 공고·기업·적합도 분석이 같은 application case를 가리키는지
3. 파일 업로드 후 추출 상태가 `PENDING/RUNNING`에서 성공 또는 명시적 실패로 끝나는지
4. AI 응답의 model/provider 표시와 관리자 사용량 로그가 실제 호출과 일치하는지
5. 로그아웃·전체 로그아웃 뒤 보호 API가 401이고 관리자 API가 권한 없는 사용자에게 403인지

운영 데이터는 삭제·결제·환불 같은 비가역 동작을 임의로 실행하지 않는다. 쓰기 검증은 전용 테스트 계정과 테스트 지원 건을 사용하고, 삭제는 프로젝트 정책대로 soft delete 여부를 확인한다.

## 로컬 풀스택

### 백엔드

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

로컬 MySQL과 Ollama가 없더라도 서버는 부팅될 수 있지만, 해당 기능이 실제로 성공한다는 뜻은 아니다. `/api/health`와 기능별 실패 응답을 구분한다. 외부 provider를 검증할 때만 현재 PowerShell 세션에 필요한 환경변수를 주입하고 값을 출력하지 않는다.

### 프런트

```bash
cd frontend
npm ci
npm run dev
```

`npm run dev`는 `/api`를 로컬 백엔드로 프록시한다. 배포 백엔드만 확인하려면 `npm run dev:aws`, mock만 확인하려면 `npm run dev:mock`을 사용한다.

## 팀 개발 환경과 공유 4090

팀 DB와 공유 Ollama를 함께 사용할 때는 `tailscale` 프로파일을 쓴다.

```powershell
cd backend
.\gradlew.bat bootRun --args='--spring.profiles.active=tailscale'
```

```bash
cd frontend
npm run dev:tailscale
```

- 공유 4090 주소는 Tailscale 내부이므로 실행 PC가 tailnet에 연결돼 있어야 한다.
- 4090이 꺼져 있으면 백엔드가 설정된 fallback endpoint와 provider 체인을 사용한다. provider마다 체인이 다르므로 단순히 서버 부팅 성공만 보고 자체 모델 성공으로 기록하지 않는다.
- Ollama tag, adapter, 모델 표시와 실제 응답 provider가 일치하는지는 관리자 AI 사용량·실패 로그와 각 모델 card를 함께 확인한다.

## AWS 배포

`dev` 반영 후 배포는 self-hosted trusted runner에서 순차 실행된다.

- `.github/workflows/deploy-backend.yml`: DB patch checksum 검증·적용, backend/worker 배포, health 확인과 rollback 경계 처리.
- `.github/workflows/deploy-web.yml`: 프런트 빌드, secret scan, App/Universal Link 메타데이터 생성, nginx 정적 루트 원자 교체.
- `.github/workflows/deploy-demo.yml`: 운영과 별개인 공개 mock 빌드 배포.

신규 DB는 `schema.sql`을 사용하지만 기존 공유·운영 DB에는 `backend/src/main/resources/db/patches/`의 새 patch만 적용한다. 이미 배포된 patch를 수정하면 checksum 불일치로 배포가 중단되므로 후속 patch를 추가한다. 상세 인프라는 [루트 배포 가이드](../../DEPLOY.md), 데모·앱 산출물은 [릴리즈 가이드](../RELEASE.md)를 따른다.

배포 직후에는 최소한 다음을 확인한다.

```bash
curl -fsS https://careertuner.example.com/api/health
curl -fsS https://careertuner.example.com/.well-known/apple-app-site-association
```

- backend와 worker container 상태 및 최근 migration 원장
- nginx가 새 index와 hashed asset을 함께 제공하는지
- 실제 로그인, `/auth/me`, refresh, logout
- 공고 파일 1건의 업로드→추출→분석 완료
- 선택한 AI 기능 1건의 provider/model/fallback 기록

## Android live 빌드

기본 live 번들은 `frontend/.env.aws`의 `https://careertuner.example.com/api`를 사용한다.

```bash
git tag live-2026-07-14
git push origin live-2026-07-14
```

릴리즈 워크플로가 시연용 release 서명 APK를 만든다. 설치 뒤에는 설정 → 계정 설정 → 서버 주소에서 허용된 프리셋을 선택할 수 있어 진단 목적의 주소 전환에 재빌드가 필요 없다. 서명·App Link·권한·SDK 요구사항은 [모바일 빌드](../../frontend/MOBILE_BUILD.md)와 [릴리즈 가이드](../RELEASE.md)를 따른다.

실기기 확인 항목:

- cold start와 로그인 복원
- OAuth/browser 복귀와 verified App Link
- 카메라·마이크·사진 권한의 요청 시점과 거부 후 안내
- 네트워크 단절/복구와 서버 주소 변경
- 파일 선택·촬영 업로드 후 pending 파일 정리
- 로그아웃 시 계정별 로컬 상태와 임시 첨부 정리

## Sites 보조 프런트

Sites는 운영 DNS를 자동 전환하거나 backend/DB를 복제하지 않는다. AWS 프런트 장애에 대비한 별도 진입점이며, AWS API가 살아 있으면 실제 데이터를 그대로 사용한다. API와 health가 모두 실패한 경우에만 저장되지 않는 mock 체험으로 전환한다.

- outage-demo의 변경은 운영 DB에 저장되지 않는다.
- 첫 쓰기 요청은 처리 여부가 불확실하므로 실패로 표시하고, 사용자가 데모 상태에서 다시 시도한 변경만 메모리에 반영한다.
- 결제·구독·환불 mutation은 Sites에서 항상 403으로 차단한다.
- 복구 확인 뒤 페이지를 다시 불러 실제 데이터로 돌아간다.

## 기록 원칙

검증 결과에는 날짜만 쓰지 말고 대상 커밋/PR, 환경 프로파일, 호출한 명령, provider/model, 성공·실패 근거를 남긴다. 이미 검증한 기능은 [시연 준비도 원장](../verification/DEMO_READINESS_LEDGER.md)에 기록하고, 이후 관련 PR이 들어왔을 때 해당 행만 재검증한다.
