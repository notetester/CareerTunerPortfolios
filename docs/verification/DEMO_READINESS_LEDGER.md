# 전 영역 시연 준비도 검증 원장

이 문서는 A~F와 웹·모바일·데스크톱의 검증 기준점을 남겨 같은 검증을 반복하지 않기 위한 정본이다.
변경 파일과 재검증 항목의 연결은 `demo-readiness-checks.json`이 담당하고,
`scripts/verification/select-demo-regression-scope.mjs`가 이후 PR에서 바뀐 항목만 선택한다.

## 기준점

| 항목 | 값 |
| --- | --- |
| 이전 전 영역 기준 | PR #379, merge `a86d089a39adad67e362d2e61c1396dd8435afd9` |
| 이번 후보 기준 | PR #391, 검증된 코드 기준 `58aae8f6b223daa4a9dd4eaa4260f6f856627019` (이 원장 갱신 커밋 제외) |
| 검증일 | 2026-07-12 |
| 변경 기준 | PR #390 merge `7364c4e51e137453e89044e19d0a684c66021174` 이후 PR #391 diff |
| 종합 판정 | 코드·mock 시연 `PASS`; 실 공급자 인증은 아래 `BLOCKED_EXTERNAL` 참조 |

상태는 `PASS`, `PASS_TARGETED`, `PENDING_LIVE`, `BLOCKED_EXTERNAL`만 사용한다.
`PASS_TARGETED`는 이전 전체 검증 이후 바뀐 파일에 필요한 항목만 다시 확인했다는 뜻이다.

## 재검증 규칙

1. PR마다 strict selector를 실행한다.
2. 선택된 체크만 다시 검증하고, 해당 행의 PR·SHA·증거를 갱신한다.
3. selector가 찾지 못한 파일이 하나라도 있으면 CI를 실패시켜 원장 누락을 허용하지 않는다.
4. DB 패치는 이미 적용된 파일의 수정이 아니라 새 파일 추가만 허용하며 checksum 원장으로 재실행을 막는다.
5. 라이브 배포 뒤에만 `PENDING_LIVE`를 `PASS`로 올린다.

## 실행 증거

| 증거 ID | 결과 | 실행 내용 |
| --- | --- | --- |
| `BE-FULL-01` | `PASS_TARGETED` | `gradlew test` 1회: 1,692건 중 유일한 실패가 H2 관리자 적합도 픽스처 16건. 운영 `users.status/deleted_at`과 픽스처 불일치를 수정한 뒤 두 실패 클래스 16/16 통과. 그 외 실패 0 |
| `FE-ALL-01` | `PASS` | typecheck, 관리자 접근, native OAuth/config/deep link/mobile, 모델 재시도, A~F demo, 임시 파일, 세션 경계, mock/AWS build 등 18개 스크립트 전부 통과 |
| `DB-FRESH-01` | `PASS` | MySQL 8.4 빈 DB에 canonical schema와 seed, 2026-07-12 패치 7개 적용. 동일 패치 재실행 후 checksum 기준 무변경, 사용자 5명·`deleted_at` 컬럼 41개·첨부 정책 4행 확인 |
| `DB-DEPLOY-01` | `PASS` | DB 패치 배포 Python 계약 4건, AASA nginx 계약 4건 통과. `actionlint`, Docker `shellcheck`, `git diff --check` 통과 |
| `SCOPE-01` | `PASS` | selector 단위테스트 5건 통과. strict 작업 트리 선택: 변경 216개, 체크 22개, 미매핑 0 |
| `WEB-UI-01` | `PASS_TARGETED` | mock 실브라우저: 390px 커뮤니티 `scrollWidth <= innerWidth`, 고객센터 검색·FAQ·비로그인 문의 복귀·AI 상담, 첨삭 4종·모델·삭제, USER `/admin/policies` 403, 관리자 프로필 버전·면접·첨삭·정책 화면 확인 |
| `ANDROID-01` | `PASS` | Capacitor release sync, `lintDebug testDebugUnitTest assembleDebug` 성공(Gradle 512 task), API 35 에뮬레이터 APK 설치·기동·더보기·커뮤니티·하단탭·390px·라이트/다크 실기 확인 |
| `DESKTOP-01` | `PASS` | Qt 6.11.1 Release 빌드, CTest 1/1, windeployqt, ZIP·NSIS 설치형·포터블 생성. 패키지 실행 후 로컬 DB 계정 로그인·세션·웹 인계 8개·설정·라이트/다크 실기 확인 |
| `DATA-LIFECYCLE-01` | `PASS` | 사용자 콘텐츠·관계 데이터의 물리 삭제를 soft delete/reactivation으로 전환하고 활성 필터·카운트 보정·계정 삭제 연쇄를 계약 테스트와 MySQL로 확인 |

## A~F 및 플랫폼 체크리스트

| 체크 | 상태 | 이번에 확인·보강한 경계 | 증거 |
| --- | --- | --- | --- |
| A-AUTH-ACCOUNT | PASS_TARGETED | 익명·USER 관리자 fail-closed, 동의·세션 경계, 계정 삭제 시 프로필·파일·관계 정리, SMS 공급자 정직성 | BE-FULL-01, FE-ALL-01, WEB-UI-01 |
| A-PROFILE | PASS_TARGETED | 프로필 버전 스냅샷, AI 분석 provenance, 관리자 버전 이력, 삭제 계정 파일 정리 | BE-FULL-01, DB-FRESH-01, WEB-UI-01 |
| A-SETTINGS-ADMIN | PASS_TARGETED | 관리자 메뉴 그룹·exact CRUD 권한, 권한 없는 탭/동작 비노출, 실제 사용자 표시 | FE-ALL-01, WEB-UI-01 |
| B-APPLICATION | PASS_TARGETED | 지원 건 overflow, mock/실 모델 계약, soft delete, provenance | BE-FULL-01, FE-ALL-01 |
| B-COMPANY-ANALYSIS | PASS_TARGETED | 삭제 사용자·지원 건의 분석 제외, 관리자 조회 경계 | BE-FULL-01, DATA-LIFECYCLE-01 |
| B-JOB-ANALYSIS | PASS_TARGETED | 공고·파일 소유권, 파이프라인 정리, 삭제 데이터 비노출 | BE-FULL-01, FE-ALL-01 |
| C-DASHBOARD | PASS_TARGETED | 할 일 soft delete, 관리자 활성 집계, mock 대시보드 | BE-FULL-01, FE-ALL-01 |
| C-FIT-ANALYSIS | PASS_TARGETED | 프로필 버전→적합도, 관리자 active filter, H2 픽스처 정합 | BE-FULL-01, DB-FRESH-01 |
| C-CAREER-PLANNER | PASS_TARGETED | 장기 분석·플래너 active filter와 soft delete, 데스크톱 웹 인계 | BE-FULL-01, DESKTOP-01 |
| D-INTERVIEW | PASS_TARGETED | A/B/C snapshot context·provenance, 사용자 선택 모델 재시도, terminal SSE error | BE-FULL-01, FE-ALL-01 |
| D-MEDIA-HANDOFF | PASS_TARGETED | 답변·미디어 소유권, 임시 파일 정리, 실제 Browser STT | BE-FULL-01, FE-ALL-01, ANDROID-01 |
| D-REPORT | PASS_TARGETED | 리포트·복습 lifecycle과 데스크톱 패키지 | BE-FULL-01, DESKTOP-01 |
| E-CORRECTION | PASS_TARGETED | 4종 첨삭, C 적합도·D 답변 원문 연결, provenance, soft delete, 사용자 모델 선택 | BE-FULL-01, FE-ALL-01, WEB-UI-01 |
| E-BILLING-CREDITS | PASS_TARGETED | AI 실행 전 과금 preview·멱등키, 실패 시 정산 경계, 데스크톱 결제 인계 | BE-FULL-01, FE-ALL-01, DESKTOP-01 |
| F-COMMUNITY | PASS_TARGETED | 글·태그·반응·스크랩 soft delete/reactivation, 카운트 보정, 공개 activity 보안 경계, 390px | BE-FULL-01, WEB-UI-01, ANDROID-01 |
| F-NOTIFICATION-COLLAB | PASS_TARGETED | 알림·구독·친구·대화 권한 데이터 active filter와 계정 삭제 정리 | BE-FULL-01, DATA-LIFECYCLE-01, DESKTOP-01 |
| F-SUPPORT-CONTENT | PASS_TARGETED | FAQ 검색, 비로그인 문의, 공유 AI 상담, 실제 STT, 모호 질문 원문 재실행 | FE-ALL-01, WEB-UI-01 |
| DATA-LIFECYCLE | PASS | 패치 7개, checksum 적용기, 2회 적용 멱등성, orphan 방지 | DB-FRESH-01, DB-DEPLOY-01, DATA-LIFECYCLE-01 |
| PLATFORM-WEB | PASS_TARGETED | mock/AWS 빌드, 영향 경로 실브라우저, 반응형·다크·권한 경계 | FE-ALL-01, WEB-UI-01 |
| PLATFORM-MOBILE | PASS | Android CI self-hosted 전환, 네이티브 계약, 실제 APK·API 35 에뮬레이터 | FE-ALL-01, ANDROID-01 |
| PLATFORM-DESKTOP | PASS | 실제 Release 패키징·실행·로그인·테마·웹 인계 | DESKTOP-01 |
| RELEASE-GATE | PENDING_LIVE | workflow·배포 스크립트 정적검증 통과. PR CI·merge·DB/web/backend 라이브 배포 후 갱신 | DB-DEPLOY-01, SCOPE-01 |

## 외부 구성 차단 항목

| 항목 | 상태 | 시연 영향 | 완료 조건 |
| --- | --- | --- | --- |
| Google/Kakao/Naver OAuth 운영 자격증명 | BLOCKED_EXTERNAL | mock·이메일 로그인 시연에는 영향 없음. 실 소셜 로그인은 비활성 | 각 공급자 콘솔의 client id/secret·반환 주소 등록 후 GitHub secret/variable 주입 |
| Apple Team ID | BLOCKED_EXTERNAL | Android·웹에는 영향 없음. iOS Universal Link AASA는 deny-all로 게시 | `APPLE_TEAM_ID` repository variable 등록 후 웹 재배포 |
| 실 유료 AI·SMS 공급자 키 | BLOCKED_EXTERNAL | 장애 독립 mock 데모는 정상. 실 공급자 출력·발송만 제한 | 데모 당일 사용할 공급자 키와 runtime mode 확정 |

mock은 운영 API 장애 때만 사용하는 독립 백업이며, 정상 운영 중에는 AWS 실서버가 우선이다.
