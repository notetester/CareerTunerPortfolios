# 웹 · 모바일 · 데스크톱

CareerTuner는 웹을 단순히 앱으로 감싼 세 복사본이 아닙니다. 웹/PWA와 Capacitor 앱은 React 화면을 공유하고, C++/Qt 데스크톱 앱은 같은 REST·JWT·지원 건 계약을 네이티브 UI로 소비합니다. 기능의 입출력과 데이터 소유권은 공유하되, 인증 복귀·미디어·배포 방식은 플랫폼별로 분리합니다.

## 구현 범위

| 플랫폼 | 현재 구현 | 배포 단위 |
| --- | --- | --- |
| 웹/PWA | 사용자 SPA, 관리자 SPA, 서비스워커, Web Push, 반응형·다크 모드 | Vite 정적 번들 + AWS API |
| Android/iOS | Capacitor 8 셸, 카메라·푸시·딥링크·native OAuth handoff | APK/AAB, Xcode 프로젝트 |
| Windows 데스크톱 | C++17 + Qt 6.11 + QML 로그인/MFA, 지원 건, AutoPrep, 면접, 플래너, 커뮤니티, 협업 | Qt runtime 포함 ZIP |

## 공통 계약

- 모든 클라이언트는 `/api/**`와 `ApiResponse<T>` envelope를 사용합니다.
- 액세스 토큰은 `Authorization: Bearer`로 전송하고 401 시 플랫폼별 복구 정책을 적용합니다.
- 지원 건 ID가 분석·면접·첨삭·플래너 인계의 공통 문맥입니다.
- 모델 선택은 `AUTO`, 자체 모델, Claude, OpenAI 등 같은 요청 계약을 사용합니다.
- 재시도 기본값은 이전 선택이지만 사용자가 다른 모델을 다시 고를 수 있습니다.
- 변경 API는 action key/멱등키를 사용해 네트워크 재시도에서 중복 작업·중복 과금을 막습니다.

## 웹과 PWA

`frontend/vite.config.ts`는 React 19, Vite 8, TypeScript 7, Tailwind v4와 `vite-plugin-pwa`를 묶습니다. 정적 JS/CSS/HTML/아이콘만 precache하고 `/api` 응답은 runtime cache에 넣지 않습니다. 프로필·면접·결제처럼 계정에 묶인 데이터가 로그아웃 뒤 브라우저 캐시에 남지 않게 하기 위한 선택입니다.

SPA fallback은 앱 경로에만 적용합니다. `/api`, 장애 worker 경로, 공개 지식맵 `/Obsidian/`은 React `index.html`로 가로채지 않습니다. 서비스워커 교체도 controller identity가 실제로 바뀔 때 한 번만 새로고침해 무한 reload를 막습니다.

## Capacitor 모바일 보안 경계

release 앱의 기본 WebView origin은 HTTPS이며 cleartext와 mixed content는 닫혀 있습니다. 외부 `server.url`이 release 구성에 남으면 빌드 정책 검사가 실패합니다. HTTP live reload는 debug 플래그와 로컬·사설망 주소가 함께 확인된 경우에만 열립니다.

소셜 로그인은 시스템 브라우저에서 시작하고 PKCE verifier와 일회성 handoff code로 앱에 복귀합니다.

- Android: exact callback path를 선언한 verified App Link와 `assetlinks.json`
- iOS: Associated Domains와 AASA
- 공통: 허용된 HTTPS authorization endpoint, handoff 중복 교환 방지, 만료·취소·로그인 전환 세대 검사

따라서 웹 callback URL과 앱 callback URL은 provider 콘솔에 각각 등록하며, custom scheme만 신뢰해 토큰을 전달하지 않습니다.

## Qt 데스크톱 앱

`desktop/`은 초기 관제탑 골격을 넘어 실제 사용자 흐름을 구현합니다.

- `ApiClient`: envelope 파싱, bearer token, 401 복구, multipart·다운로드
- `AuthService`: 로그인, MFA/백업 코드, 자동 로그인, 세션 세대 격리
- `JobModel`·`AutoPrepRunner`: 지원 건과 POST-SSE 자동 준비 실행
- `InterviewSession`: 질문 모델 선택, single-flight 생성, 답변·꼬리질문·모범답안, 음성/영상 업로드, 리포트 내보내기
- `PlannerClient`: 일정·메모·리마인더와 always-on-top overlay
- `CommunityClient`·`CollaborationClient`: 게시글, 댓글, 반응, 파일 협업
- `SettingsStore`: 토큰을 QML에 노출하지 않고 C++에서 보관

일반 GET SSE는 `Last-Event-ID`로 이어받지만, AutoPrep의 POST-SSE는 자동 재연결하면 작업을 중복 실행할 수 있어 재실행하지 않습니다. 네트워크 방식이 같아 보여도 멱등성에 따라 복구 전략을 분리한 사례입니다.

카메라·마이크가 없는 PC에서는 모바일/웹 인계 경로를 제공하고, 제출되지 않은 로컬 미디어와 서버 업로드를 정리합니다. 면접 리포트는 Markdown 등으로 내보낼 수 있습니다.

## 테스트와 릴리스

- 프런트: typecheck, mock build, admin/A~F readiness, OAuth·deep-link·Capacitor 정책 테스트
- Android: Node 22 + JDK 21, release 서명, verified link 산출물 검사
- iOS: Associated Domains/AASA 적용 후 시뮬레이터 컴파일
- 데스크톱: CMake/CTest, Qt runtime 배포, ZIP smoke test

mock 웹·APK는 서버 없이 시연하기 위한 별도 산출물입니다. 운영 연결이 정상인데 mock을 우선하지 않으며, 장애 독립 데모로 전환할 때도 관리자 persona는 공개 static demo에서만 허용합니다.

관련 문서: [인증](./auth.md), [배포·장애 대응](./release-readiness.md), [데이터 생명주기](./data-lifecycle.md)
