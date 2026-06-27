# CareerTuner 보안 정책

CareerTuner는 채용공고, 사용자 프로필, 경력/자격증, 자기소개서와 업로드 문서, 면접 음성/영상, AI 분석 로그, 결제/크레딧 데이터를 함께 다루는 AI 취업 전략 플랫폼입니다. 보안 관리는 "누가 어떤 지원 건과 관리자 데이터를 볼 수 있는가", "업로드 파일과 AI 입력/출력이 어디로 흐르는가", "배포와 데모 산출물에 비밀값이 섞이지 않는가"를 중심으로 합니다.

이 문서는 저장소 운영자가 아직 확정하지 않은 장기 지원 버전, 고정 응답 SLA, 보상 프로그램을 임의로 만들지 않습니다. 브랜치와 PR 운영은 [AGENTS.md](AGENTS.md)와 [docs/BRANCHES.md](docs/BRANCHES.md)를 따르고, 보안 수정은 현재 기본 개발선과 실제 데모/배포/릴리즈에 연결된 활성 코드에 우선 적용합니다.

## 비공개 신고

취약점의 세부 내용은 공개 이슈, 공개 PR, 공개 토론에 바로 올리지 마세요.

가능하면 GitHub의 Private vulnerability reporting 또는 Security Advisory 기능으로 신고합니다. 그 경로를 사용할 수 없다면 저장소 소유자나 프로젝트 관리자에게 직접 전달합니다.

신고에는 가능한 범위에서 다음을 포함해 주세요.

- 영향받는 기능, API, 화면, 워크플로, 파일 경로
- 재현 절차와 필요한 권한 수준
- 예상 영향: 개인정보 노출, 인증 우회, 권한 상승, 결제/크레딧 조작, 파일 접근, 원격 코드 실행, 서비스 중단 등
- 요청/응답 예시, 로그, 스크린샷, 테스트 계정 조건
- 이미 확인한 완화 방법이나 수정 제안

실제 사용자 개인정보, 원본 이력서/공고 파일, 면접 음성/영상, 실제 토큰/비밀번호/개인키는 신고 본문에 그대로 붙이지 마세요. 필요한 경우 마스킹한 샘플이나 최소 재현 데이터로 설명합니다.

## 우선 보호 대상

아래 영역은 CareerTuner의 실제 구현과 운영 문서를 기준으로 우선 보호합니다.

| 영역 | 보안상 중요한 이유 |
| --- | --- |
| 인증/세션 | Spring Security stateless JWT, refresh token 회전/폐기, OAuth callback/state, BCrypt 비밀번호 해시 |
| 사용자 지원 건 | `application_case` 중심으로 프로필, 공고 revision, 기업/직무/적합도 분석, 면접, 첨삭이 연결됨 |
| 업로드 파일 | 공고 PDF/이미지, 첨부 파일, 면접 미디어가 로컬 `.uploads/**` 경로와 DB `file_asset` 메타데이터로 관리됨 |
| 관리자 API | `/api/admin/**`는 URL 레벨에서 ADMIN/SUPER_ADMIN 권한이 필요하며 운영 메모, 회원, 결제, AI 사용량, 프롬프트를 다룸 |
| AI/ML 처리 | OpenAI, Anthropic, Ollama/4090, RAG/Qdrant, 파인튜닝 JSONL, 프롬프트 템플릿, AI 사용량/크레딧 로그가 포함됨 |
| 결제/크레딧 | 구독, 사용권, 크레딧 차감, 결제 원장, 관리자 결제 조회가 서비스 권한과 비용에 직접 연결됨 |
| 배포/릴리즈 | GitHub Pages mock 데모, Android APK 릴리즈, iOS 시뮬레이터 빌드, 4090 trigger workflow, demo repo push token을 사용함 |
| 구성/비밀값 | `application.yaml`의 개발용 기본값, `.env`, GitHub Actions secrets, VAPID/FCM/APNs, SMTP, DB/JWT/OAuth/OpenAI 키 |

## 특히 보고해야 할 사례

- 다른 사용자의 지원 건, 프로필, 업로드 파일, 면접 결과, 결제/크레딧 내역에 접근할 수 있음
- 일반 사용자가 관리자 API나 SUPER_ADMIN 기능에 접근할 수 있음
- JWT, refresh token, OAuth state, 비밀번호 재설정/이메일 인증 토큰을 재사용하거나 우회할 수 있음
- 파일 업로드/다운로드에서 경로 탈출, 임의 파일 읽기/쓰기, 예상 밖 content type 실행, 과도한 파일 크기 처리가 가능함
- MyBatis 매퍼나 검색/정렬 파라미터를 통해 SQL injection 또는 권한 없는 데이터 조회가 가능함
- OpenAI/Anthropic/Ollama/4090/Qdrant 등 외부·내부 AI provider로 민감 데이터가 불필요하게 전송되거나 로그에 남음
- 프롬프트 주입으로 관리자 기능, 파일, 토큰, 타 사용자 데이터에 접근하거나 시스템 정책을 우회할 수 있음
- 결제 승인, 구독 상태, 크레딧/사용권 차감, AI 사용량 원장을 조작할 수 있음
- GitHub Actions secret, demo 배포 token, SSH private key, API key, DB/JWT/OAuth/VAPID private key가 repo, 로그, 공개 demo 산출물에 노출됨
- 공개 데모(`CareerTunerDemo`)나 mock APK에 실제 DB 주소, 사용자 데이터, 운영 키, 내부 API endpoint가 섞임

## 개발 기본값과 배포 경계

이 저장소는 팀 개발 편의를 위해 일부 개발용 기본값을 `backend/src/main/resources/application.yaml`에 둡니다. 이 값들은 운영 비밀값으로 간주하지 않으며, 실제 배포에서는 동일 이름의 환경변수 또는 GitHub Actions secrets로 반드시 교체합니다.

비밀값이 노출되면 코드에서 지우는 것만으로 끝내지 않습니다. 해당 키와 토큰을 폐기하거나 회전하고, 영향받은 배포 환경과 로그, 공개 데모 산출물까지 확인합니다.

`frontend` mock 데모와 Android debug APK는 백엔드 없이 동작하는 시연 산출물입니다. 데모 빌드는 실제 사용자 데이터, 운영 DB 주소, 운영 API 키, 내부 secret을 포함하지 않아야 합니다. `.github/workflows/deploy-demo.yml`의 dist secret scan이 실패하면 배포를 중단하고 원인을 먼저 제거합니다.

## 범위 밖 또는 낮은 우선순위

아래 항목은 민감 정보 노출, 권한 문제, 무단 데이터 변경, 비용 발생, 서비스 장애와 직접 연결되지 않는 한 보안 취약점으로 처리하지 않을 수 있습니다.

- 단순 오타, 스타일, 반응형 레이아웃 문제
- 공개 가능한 mock 데이터의 표시 오류
- 민감 정보가 없는 개발용 콘솔 경고
- 실제 데모/배포와 연결되지 않은 폐기 실험 코드
- 악용 가능성이 확인되지 않은 일반 의존성 최신화 요청

## 의존성 업데이트

의존성 업데이트는 [.github/dependabot.yml](.github/dependabot.yml)에서 영역별로 관리합니다.

- `frontend`: React/Vite/TypeScript/Capacitor npm 의존성
- `backend`: Spring Boot, MyBatis, Spring Security, JWT, OpenAPI, PDF/mail/push 관련 Gradle 의존성
- `ml`/`docs/ai-training`: Python 모델 학습, OCR, 평가/릴리즈 검증 requirements
- `docker`: compose, backend image, job-posting worker image
- `github-actions`: 데모 배포, 모바일 릴리즈, 4090 trigger, CI workflow actions

실제 악용 가능한 취약점, 공개 CVE, secret 노출, 인증/권한 우회와 연결된 업데이트는 일반 버전 업데이트보다 우선 검토합니다. ML requirements는 재현성과 GPU/4090 실행 환경에 영향을 줄 수 있으므로 프런트/백엔드 런타임 업데이트와 분리해서 봅니다.

## 수정과 공개

취약점이 확인되면 필요한 범위에서 비공개 브랜치, 제한된 PR, 또는 Security Advisory로 수정 내용을 관리합니다. 패치가 준비되기 전까지 재현 코드와 영향 범위 세부 사항은 공개하지 않습니다.

수정 확인은 가능한 한 영향받은 계층의 테스트와 함께 진행합니다. 예를 들어 백엔드는 `./gradlew test`, 프런트는 `npm run typecheck`, job-posting worker는 해당 Python tests/릴리즈 readiness 스크립트, 배포 산출물은 workflow secret scan과 artifact 확인을 우선 사용합니다.
