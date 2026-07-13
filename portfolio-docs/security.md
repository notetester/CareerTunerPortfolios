# 보안과 공개 이력 정화

CareerTuner는 프로필·경력·지원 건·업로드 문서·면접 미디어·AI 결과·결제 원장을 함께 다룹니다. 따라서 보안은 화면을 숨기는 수준이 아니라 **요청 주체, 데이터 소유권, 관리자 행위 권한, 외부 전송 경계, 삭제 수명주기**를 서버에서 집행해야 합니다. 이 공개 저장소에는 여기에 하나의 경계가 더 있습니다. 비공개 팀 저장소의 코드를 포트폴리오로 공개하면서도, 과거 커밋에 들어간 자격증명과 비공개 인프라 정보는 이력 전체에서 제거해야 합니다.

## 런타임 보안 경계

### JWT와 계정 상태

`SecurityConfig`는 세션을 만들지 않는 Spring Security 체인입니다. `JwtAuthenticationFilter`가 `Authorization: Bearer` access token을 검증해 `AuthUser(id, email, role)`를 복원하고, 공개 화이트리스트 이외의 `/api/**` 요청은 인증을 요구합니다. access·refresh·OAuth state token은 용도를 구분하며, refresh token은 DB에서 회전·폐기됩니다. 비밀번호는 BCrypt로 해시합니다.

관리자 계정에는 역할만 보지 않는 추가 경계가 있습니다. `AdminAccountStateFilter`가 현재 DB 상태를 확인하므로, 발급 뒤 비활성·차단·삭제된 관리자의 오래된 access token도 계속 사용할 수 없습니다.

### 관리자 역할과 세부 CRUD 권한

관리자 인가는 네 겹으로 적용됩니다.

1. `SecurityConfig`: `/api/admin/**`는 `ADMIN` 또는 `SUPER_ADMIN`만 진입할 수 있습니다.
2. `AdminPermissionInterceptor`: 컨트롤러의 `@RequireAdminPermission`을 읽어 직접 부여와 그룹 경유 권한의 합집합을 검사합니다. 클래스의 READ와 메서드의 CREATE/UPDATE/DELETE가 함께 선언되면 두 조건을 모두 통과해야 합니다.
3. `AdminAccess`: 서비스가 관리자 또는 최고 관리자 역할을 다시 확인합니다.
4. 프런트 `AdminRouteBoundary`·메뉴·mock API: 같은 권한 카탈로그로 route, lazy import, 버튼, API 동작을 제한합니다.

일반 `ADMIN`용 API에 세부 권한 선언이 빠지면 허용을 추정하지 않고 403으로 닫습니다. 역할만으로 충분한 좁은 엔드포인트만 `@AdminRoleOnly`를 명시합니다. `SUPER_ADMIN`은 전체 정규 권한을 통과하고 권한 그룹·개별 권한을 관리하지만, 마지막 활성 최고 관리자를 비활성화하거나 일반 관리자가 자기 권한을 올리는 동작에는 별도 guard가 있습니다.

이 구조 때문에 `/admin/policies`를 주소창에 직접 입력해도 익명 사용자는 로그인으로 이동하고, 일반 회원과 `POLICY_READ`가 없는 관리자는 화면을 마운트하기 전에 차단됩니다. 프런트 검사는 UX 경계이며 최종 판정은 항상 백엔드가 합니다.

### 데이터 소유권과 삭제 수명주기

지원 건은 다른 분석·면접·첨삭의 부모입니다. 서비스와 MyBatis 쿼리는 인증 사용자 id와 지원 건 id를 함께 조건으로 사용해 다른 회원의 자원을 id만 추측해 읽지 못하게 합니다. 파일 다운로드도 `file_asset.owner_user_id`를 검사합니다.

업로드 파일은 UUID 기반 저장 이름, 크기·MIME·확장자 검증, 정규화된 경로의 base-directory 소속 확인을 적용합니다. 응답 파일명에서는 따옴표와 개행을 제거합니다. 공고 파일 참조는 지원 건 id와 저장 경로를 함께 확인합니다.

제품 데이터는 원칙적으로 `deleted_at`을 남기는 소프트 삭제입니다. 관계 데이터는 부모 삭제 상태를 조회 조건에 반영하고, 사용자 삭제·복구·보관 전환은 연결된 데이터가 고아가 되지 않도록 패치와 회귀 테스트로 관리합니다. 외부 저장 파일처럼 DB 행만으로 수명주기를 끝낼 수 없는 자원은 별도 정리 정책 대상입니다.

### OAuth·전화 인증·동의

웹 OAuth는 서명되고 짧게 만료되는 state로 callback CSRF를 막습니다. Capacitor 앱은 브라우저 인증 뒤 PKCE 기반 handoff code를 앱 deep link로 받고, 서버에서 일회성으로 교환합니다. provider secret은 앱 번들에 넣지 않습니다.

전화번호 인증은 Firebase Phone Auth가 발급한 client proof를 서버가 검증하는 경로를 우선합니다. 실제 SMS/Firebase 자격증명과 provider 콘솔 등록이 없는 환경에서는 성공으로 가장하지 않습니다. `AI_DATA` 동의는 AI 처리 직전에 서버가 확인하고, 철회 뒤의 새 요청을 차단합니다.

### 외부 호출과 오류 응답

OpenAI·Anthropic·자체 모델·Ollama·Qdrant·OCR worker의 목적지 URL은 사용자 입력으로 만들지 않고 서버 설정으로 고정합니다. 사용자 입력은 요청 본문 데이터일 뿐 host를 바꾸지 못합니다. 설정되지 않은 선택 provider는 비활성 또는 검증된 폴백으로 내려갑니다.

예상하지 못한 예외는 `GlobalExceptionHandler`가 일반화된 `ApiResponse` 오류로 바꾸고, 스택·SQL·내부 원인 문자열을 클라이언트에 보내지 않습니다.

## 공개 저장소 이력 정화

공개본은 현재 파일만 복사한 스냅샷이 아닙니다. 비공개 원본의 브랜치·태그와 커밋 그래프를 보존한 뒤, 공개하면 안 되는 값만 이력 재작성으로 치환합니다. 원본과 공개본의 커밋 SHA가 다른 것은 이 과정의 정상적인 결과입니다.

### 보존 범위

- 모든 로컬/원격 head와 tag를 정화 대상으로 포함합니다.
- GitHub `refs/pull/*/head`에만 남아 기존 branch·tag에서 도달할 수 없는 커밋은 공개용 archive branch로 보존합니다.
- `git filter-repo`는 `--prune-empty never --prune-degenerate never`로 실행해, 내용 치환으로 비어 보이는 커밋도 임의로 없애지 않습니다.
- 원본의 각 커밋이 정확히 하나의 공개 커밋으로 대응하는지, root·merge parent 수·tag target을 기계적으로 비교합니다.
- Windows의 대소문자 비구분 ref 충돌을 피하기 위해 이력 변환은 Linux 파일시스템에서 실행합니다.

### 제거·정규화 범위

비공개로 유지하는 exact-value 목록과 패턴 탐지기를 함께 사용해 다음 범주를 blob·commit message·tag message에서 제거합니다.

- DB 사용자명·비밀번호·원격 host와 URL
- OAuth·메일·결제·LLM·푸시 자격증명과 개인키
- 내부 AWS/Tailscale/LAN 주소와 운영 endpoint
- 의도하지 않은 개인 이메일·이름·로컬 절대 경로

허용된 여섯 팀원의 실명과 실제 이메일은 포트폴리오 기여 증거이므로 제거하지 않습니다. 과거 별칭은 author와 committer 양쪽에서 정규화하고, `Co-authored-by` 같은 commit trailer도 같은 허용 목록으로 맞춥니다. GitHub와 Dependabot 같은 서비스 계정은 서비스 identity로 보존합니다.

정화 규칙과 원래 값 목록은 공개 저장소에 넣지 않습니다. 변환 뒤에는 원본 object가 unreachable 상태로 남지 않도록 새 bare mirror를 다시 만들고, 그 깨끗한 mirror를 게시 후보로 사용합니다.

### 공개 tip의 저장소 경계

비공개 storyboard·장문 AI 산출물·raw artifact·내부 Obsidian vault는 Git submodule URL이나 gitlink로 공개 tip에 남기지 않습니다. 공개본에는 다음만 포함합니다.

- 실행 가능한 mock 데모
- 사람이 읽을 수 있도록 다시 작성한 `/docs/` 기능 설명
- credential·개인정보를 제거한 `/Obsidian/` 공개 지식 그래프
- 원본 근거의 종류와 검증 시점을 설명하는 작은 manifest

즉, 참고 저장소를 그대로 복제해 공개하는 것이 아니라 공개 가능한 근거만 별도로 투영합니다.

## 배포 게이트

Pages workflow는 다음 순서로 사이트를 조립합니다.

1. Node 22에서 `npm ci`, typecheck, 핵심 mock·권한·플랫폼 회귀 테스트
2. `VITE_DEMO_MODE=true`, `VITE_USE_MOCK=true`로 최신 SPA 빌드
3. dead link를 허용하지 않는 VitePress `/docs/` 빌드
4. 공개 지식 그래프를 `/Obsidian/`에 복사
5. 조립된 `_site`에 비밀값·개인키·내부 주소 패턴이 없을 때만 Pages artifact 업로드

전체 Git 이력 검사는 공개 릴리스 생성 단계에서 `gitleaks`와 프로젝트 전용 verifier로 수행합니다. Pages의 산출물 검사는 브라우저에 실제로 전달되는 파일에 대한 마지막 방어선입니다.

## 데모와 운영의 차이

공개 Pages는 브라우저 안에서 mock 데이터로 동작하는 장애 독립 시연판입니다. 운영 AWS가 정상일 때 우선 연결되는 failover endpoint가 아니며, 실제 OAuth·SMS·결제·유료 AI provider의 운영 성공을 증명하지 않습니다. 외부 자격증명이 필요한 항목은 코드/계약 테스트와 provider 콘솔·실환경 검증 상태를 별도로 기록합니다.

보안 문제 제보 방법과 우선 보호 대상은 저장소 루트의 [SECURITY.md](https://github.com/notetester/CareerTunerPortfolio/blob/dev/SECURITY.md)를 참고하세요.
