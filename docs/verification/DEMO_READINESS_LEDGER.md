# 전 영역 시연 준비도 검증 원장

이 문서는 A~F와 웹·모바일·데스크톱의 검증 기준점을 남겨 같은 검증을 반복하지 않기 위한 정본이다.
변경 파일과 재검증 항목의 연결은 `demo-readiness-checks.json`이 담당하고,
`scripts/verification/select-demo-regression-scope.mjs`가 이후 PR에서 다시 실행해야 할 검사 범위만 선택한다.
selector의 성공은 경로 매핑이 완전하다는 뜻이지, 선택된 빌드·테스트·실기 검증을 실행했다는 증거가 아니다.

## 기준점

| 항목 | 값 |
| --- | --- |
| 이전 전 영역 기준 | PR #379, merge `a86d089a39adad67e362d2e61c1396dd8435afd9` |
| 전 영역 구현 기준 | PR #391, merge `dba92c3150676b21f1b0c3efff8244c3a5fd2260` |
| 문서 최신화 시작 시점 `dev` | PR #417, merge `8ab96cc52b06d9144ed09662bd62a9f6001c6e3c` (2026-07-14 확인) |
| 문서 정보 구조·검증 자동화 기준 | PR #422, merge `fcf7fee911ec8d5f3f414569bbe4b859bff848b7` |
| 운영 호스트 신뢰·DB 회전 기준 | PR #424 merge `80eab747f911b937cfce5cf0139523f2d015d4cc`, PR #427 merge `f17d0b40b392538ef648f6c6b3548aab8e4d11a6`, PR #428 merge `3bbab67c455fc0a1b0ad5221625842d169924c21` |
| 마지막 전 영역 구현·검증 기준 | PR #391, merge `dba92c3150676b21f1b0c3efff8244c3a5fd2260` |
| 마지막 기능·CI 표적 검증 기준 | PR #448, merge `167f5feffa6f80b55b47333565a7d357821c1e5f`; Frontend CI run `29315421962`, Documentation CI run `29315421955`, selector run `29315421967`, Service Pipeline CI run `29315421960` |
| 마지막 운영 배포 게이트 기준 | PR #448, merge `167f5feffa6f80b55b47333565a7d357821c1e5f`; Service Pipeline CI run `29315624234`, backend/DB deploy run `29315624101`, web deploy run `29315624240`, Android live release run `29316191781` |
| 마지막 원장 검증일 | 2026-07-14 |
| 변경 기준 | PR #390 merge `7364c4e51e137453e89044e19d0a684c66021174` 이후 PR #391 전체 보강 + PR #392·#395 배포 경계 + PR #422 문서/계약 정리 + PR #424·#427·#428 운영 SSH/DB 회전 + PR #448 프로필·AutoPrep·커뮤니티 표적 재검증 |
| 종합 판정 | PR #395 스냅샷의 코드·mock·AWS 핵심 시연 경로는 `PASS`, PR #422·#424·#427·#428·#448 변경 범위는 아래 증거로 `PASS_TARGETED`. PR #448의 프로필 동시 편집, AutoPrep 실행·첨부 생명주기와 멱등성, 커뮤니티 개인정보·soft delete 경계는 필수 CI를 통과했고 신규 DB patch, 백엔드·웹, mock 데모와 최신 Android live APK까지 배포했다. 데스크톱 네이티브 소스는 바뀌지 않아 기존 Release 패키지 실기 증거를 승계한다. 실 OAuth 로그인, 유료 AI 실출력, SMS는 2026-07-14 프로젝트 책임자의 수동 live 완주 확인을 `PASS_MANUAL`로 별도 기록한다. Apple 개발자 활성화·테스트와 Team ID 기반 운영 Universal Link 전환은 분리하며, 후자는 현재 시연 범위 밖의 `DEFERRED` 항목이다. |

상태는 `PASS`, `PASS_TARGETED`, `PASS_MANUAL`, `PENDING_LIVE`, `BLOCKED_EXTERNAL`, `DEFERRED`만 사용한다.
`PASS_TARGETED`는 이전 전체 검증 이후 바뀐 파일에 필요한 항목만 다시 확인했다는 뜻이다.
`PASS_MANUAL`은 운영 계정·단말·유료 공급자가 필요한 live 시나리오를 프로젝트 책임자가 직접 완주했지만
계정 식별자, credential, 원문 응답 같은 민감 증거는 저장소에 남기지 않았다는 뜻이다. CI 통과를 의미하지 않는다.
`DEFERRED`는 현재 시연 완료 조건과 분리해 명시적으로 미룬 후속 운영 항목이며 결함이나 시연 차단을 뜻하지 않는다.

## 재검증 규칙

1. PR마다 strict selector를 실행해 재검증 대상을 결정한다.
2. 선택된 체크에 연결된 빌드·자동 테스트·필요한 실기 검증을 별도로 실행하고, 해당 행의 PR·SHA·증거를 갱신한다.
3. selector가 찾지 못한 파일이 하나라도 있으면 CI를 실패시켜 원장 누락을 허용하지 않는다.
4. DB 패치는 이미 적용된 파일의 수정이 아니라 새 파일 추가만 허용하며 checksum 원장으로 재실행을 막는다.
5. 라이브 배포와 자동 증거가 있으면 `PASS`, 운영 계정·단말로 수동 완주했으면 `PASS_MANUAL`로 올리고 검증 주체·날짜·범위를 기록한다.
6. `PASS_MANUAL` 항목은 자격증명, callback, provider, 모델 또는 제한 정책이 바뀐 경우에만 다시 검증한다.

## 실행 증거

| 증거 ID | 결과 | 실행 내용 |
| --- | --- | --- |
| `BE-FULL-01` | `PASS_TARGETED` | `gradlew test` 1회: 1,692건 중 유일한 실패가 H2 관리자 적합도 픽스처 16건. 운영 `users.status/deleted_at`과 픽스처 불일치를 수정한 뒤 두 실패 클래스 16/16 통과. 그 외 실패 0 |
| `FE-ALL-01` | `PASS` | typecheck, 관리자 접근, native OAuth/config/deep link/mobile, 모델 재시도, A~F demo, 임시 파일, 세션 경계, mock/AWS build 등 18개 스크립트 전부 통과 |
| `DB-FRESH-01` | `PASS` | MySQL 8.4 빈 DB에 canonical schema와 seed, 2026-07-12 패치 7개 적용. 동일 패치 재실행 후 checksum 기준 무변경, 사용자 5명·`deleted_at` 컬럼 41개·첨부 정책 4행 확인 |
| `DB-DEPLOY-01` | `PASS` | DB 패치 배포 Python 계약 4건, AASA nginx 계약 4건 통과. `actionlint`, Docker `shellcheck`, `git diff --check` 통과 |
| `SCOPE-01` | `PASS` | selector 단위테스트 5건 통과. strict 작업 트리 선택: 변경 216개, 체크 22개, 미매핑 0 |
| `SCOPE-PR417-01` | `PASS_TARGETED` | PR #395→#417 strict 선택: 변경 76개, 체크 22개, 미매핑 0. selector 단위테스트 7건 통과(2026-07-14). 이 증거는 범위 매핑 결과이며 선택된 22개 검사의 재실행 증거가 아님 |
| `DOCS-IA-20260714-01` | `PASS_TARGETED` | PR #422: 중복 `docs/ops`·`docs/areas`·`docs/ai`·`docs/mockups`를 정본 구조로 통합. 메인 링크 검사 3,337 tracked files/754 refs, 서브모듈 포함 전체 검사 923 refs/4 gitlinks, strict selector 22 gates, `actionlint` 통과. PR 필수 체크 전부 통과 후 merge |
| `DB-PATCH-LIVE-20260714-01` | `PASS_TARGETED` | backend deploy run `29297318730`에서 `20260714_c_ncs_code_contract.sql`을 실제 적용했고 최종 `readiness OK (app + DB up)` 확인. NCS 정규화·입력·dry-run·전체 교체 거부·TLS·적재 계약 14건 통과 |
| `SSH-TRUST-20260714-01` | `PASS_TARGETED` | PR #424: 공개 CA TLS 원점의 일회성 challenge로 EC2 SSH 호스트 키를 독립 검증(run `29298827926`), 실제 협상 키를 첨삭 사전 점검(run `29299004093`)으로 확인. backend/web/DB 회전은 지문 누락 시 원격 연결 전 실패하도록 고정 |
| `DB-ROTATION-20260714-01` | `PASS_TARGETED` | PR #427·#428: `.env`를 실행하지 않는 고정 DB key parser, 응답 유실·불명확 상태의 old/new 인증 판별, 복구 파일 보존, 실제 readiness `status/db` 계약을 반영. run `29299583768`에서 readiness 계약 오판 뒤 이전 credential 인증·원복과 서비스/DB `UP`을 확인했고, 최종 run `29300591177`에서 신규 credential·이전 credential 거부·backend/DB readiness를 모두 확인 |
| `OAUTH-CONTRACT-20260714-01` | `PASS_TARGETED` | deploy run `29299432668`에서 legacy Google redirect 구분자를 값 노출 없이 정규화, run `29300154303` backend 배포 성공. 공개 AWS에서 Google/Kakao/Naver 모두 활성이고 각 시작 endpoint가 302, 공식 provider host, `https://careertuner.example.com/api/auth/oauth/{provider}/callback`, 비-placeholder client ID 계약을 충족. 실제 사용자 동의·callback 세션 발급은 포함하지 않음 |
| `OAUTH-LIVE-MANUAL-20260714-01` | `PASS_MANUAL` | 2026-07-14 프로젝트 책임자 확인: Google·Kakao·Naver 테스트 계정으로 공급자 동의 화면→AWS callback→CareerTuner 세션 발급을 각각 완주. 계정 식별자와 토큰은 저장소에 기록하지 않음 |
| `PAID-AI-LIVE-MANUAL-20260714-01` | `PASS_MANUAL` | 2026-07-14 프로젝트 책임자 확인: 운영 환경에서 Claude Haiku와 OpenAI GPT의 비-mock 실출력 및 실제 provider 귀속을 확인. credential과 응답 원문은 저장소에 기록하지 않음 |
| `SMS-LIVE-MANUAL-20260714-01` | `PASS_MANUAL` | 2026-07-14 프로젝트 책임자 확인: 운영 SMS를 실제 단말에서 수신하고 코드 인증과 재요청·제한 시나리오를 완주. 전화번호·인증 코드는 저장소에 기록하지 않음 |
| `PHONE-SUPPORT-UI-20260714-01` | `PASS_TARGETED` | PR #434, merge `92a57cd82bfecd15c4db0f21e087e36e5d5dfa39`: 기존 OTP·Firebase 인증 로직을 공유 계정 카드에 통합하고 고객센터 CTA 다크 테마·완료 버튼 정렬을 보정. Frontend CI run `29302612611`의 typecheck/build, selector run `29302612637`의 변경 4개·재검증 항목 6개·미매핑 0, Documentation CI run `29302612630` 통과. 이는 코드·CI 표적 검증이며 새 인라인 화면의 SMS 발송·인증 완주나 Android 패키지 실기 재실행을 뜻하지 않는다. SMS 공급자·실단말 완주 증거는 `SMS-LIVE-MANUAL-20260714-01`을 유지한다 |
| `CI-SCOPE-20260714-02` | `PASS_TARGETED` | PR #435, merge `e6370e2d86cd184c8861267efd7698899e76dc56`: backend의 정확한 README 2개만 서비스 CI·배포에서 제외하고, 혼합 변경·향후 runtime Markdown·운영 Compose·DB patch 적용기 변경은 계속 검증·배포 대상으로 유지. production Compose 병합 검증과 CI 전용 env-file 경계, DB patch 동기화 트리거를 계약 테스트로 확인. PR Service Pipeline CI run `29303354111`과 dev Service Pipeline CI run `29303462775`의 모든 작업 통과, deploy run `29303462773`에서 DB 패치 10개가 모두 기존 적용 상태임을 확인한 뒤 worker `Healthy`·backend 기동·readiness app/DB `UP` 확인. Documentation CI run `29303462782`와 외부 readiness HTTP 200도 통과 |
| `PROFILE-CONCURRENCY-20260714-01` | `PASS_TARGETED` | PR #448, merge `167f5feffa6f80b55b47333565a7d357821c1e5f`: 전체·섹션 프로필 저장과 문서 가져오기에 base/current/local 3-way merge, 버전 충돌 표시, 저장·가져오기 epoch, dirty 이탈 방지와 원본 파일 정리를 적용. Frontend CI run `29315421962`의 프로필 저장·섹션 계약·병합 회귀와 Service Pipeline CI run `29315421960`의 프로필 mapper/service·import 전체 회귀를 통과 |
| `AUTOPREP-LIFECYCLE-20260714-01` | `PASS_TARGETED` | PR #448: 사용자 범위 run 취소와 선행 취소 tombstone, SSE 정상 종료와 EOF·네트워크·abort·watchdog 구분, 인증을 유지한 정확한 동의 독립 복구, 첨부 재시도·교체·세션 정리, 빈 면접 세션 정리, 공고 파일 `(user_id, file_id)` 멱등 생성 계약을 보강. 신규 DB patch와 mapper 계약 및 backend 전체 테스트는 Service Pipeline CI run `29315421960`, 프런트 첨부·실행 생명주기는 Frontend CI run `29315421962`에서 통과했고 DB patch 라이브 적용은 `DB-PATCH-PR448-20260714-01`로 확인 |
| `COMMUNITY-PRIVACY-20260714-01` | `PASS_TARGETED` | PR #448: 차단·탈퇴 사용자의 글을 limit·pagination 전에 제외하고 작성자 공개 범위를 일관되게 상속하며 관계 데이터의 soft delete·재활성화와 mine/paging 집계를 보강. backend 개인정보·관계·삭제 사용자 회귀는 Service Pipeline CI run `29315421960`, 커뮤니티 demo·프런트 회귀는 Frontend CI run `29315421962`에서 통과 |
| `CI-PR448-20260714-01` | `PASS_TARGETED` | PR #448 필수 체크 통과 후 merge: Frontend CI run `29315421962`, Documentation CI run `29315421955`, strict selector run `29315421967`(변경 104개·재검증 15개·미매핑 0), Service Pipeline CI run `29315421960`의 backend 전체 테스트 통과. 변경 감지상 대상이 아닌 worker 이미지와 NCS 적재 작업의 정상 skip은 실패가 아니며, 이 행은 라이브 배포나 DB 적용 완료 증거가 아님 |
| `DB-PATCH-PR448-20260714-01` | `PASS_TARGETED` | dev backend deploy run `29315624101`에서 `20260714_auto_prep_case_dedupe.sql`을 `Applying`→`Applied`로 실제 반영하고 `readiness OK (app + DB up)` 확인. 외부 `/api/health/ready`도 HTTP 200, `status=UP`, `db=UP` |
| `DEPLOY-PR448-20260714-01` | `PASS_TARGETED` | merge `167f5fef`의 dev push에서 Service Pipeline `29315624234`, Frontend CI `29315624132`, Documentation CI `29315624109`, backend/DB deploy `29315624101`, web deploy `29315624240`, sanitized demo deploy `29315624108` 모두 성공. 운영 root HTTP 200, AASA/Asset Links 공개 계약과 nginx 원점 검증 통과 |
| `ANDROID-PR448-20260714-01` | `PASS_TARGETED` | 공유 프런트 변경분을 dev merge `167f5fef`에서 다시 번들한 live release run `29316191781` 성공. 서명·Asset Links·보안 manifest 검증을 통과한 release `live-pr448`, APK SHA-256 `7fc3c2bb0abcfd886b17079c1502702742769eed3a861934bad537146d3c7a9d` 게시. 신규 에뮬레이터 실기는 반복하지 않고 기존 `ANDROID-LIVE-01` 실기 증거를 승계 |
| `WEB-UI-01` | `PASS_TARGETED` | mock 실브라우저: 390px 커뮤니티 `scrollWidth <= innerWidth`, 고객센터 검색·FAQ·비로그인 문의 복귀·AI 상담, 첨삭 4종·모델·삭제, USER `/admin/policies` 403, 관리자 프로필 버전·면접·첨삭·정책 화면 확인 |
| `ANDROID-01` | `PASS` | Capacitor release sync, `lintDebug testDebugUnitTest assembleDebug` 성공(Gradle 512 task), API 35 에뮬레이터 APK 설치·기동·더보기·커뮤니티·하단탭·390px·라이트/다크 실기 확인 |
| `DESKTOP-01` | `PASS` | Qt 6.11.1 Release 빌드, CTest 1/1, windeployqt, ZIP·NSIS 설치형·포터블 생성. 패키지 실행 후 로컬 DB 계정 로그인·세션·웹 인계 8개·설정·라이트/다크 실기 확인 |
| `DATA-LIFECYCLE-01` | `PASS` | 사용자 콘텐츠·관계 데이터의 물리 삭제를 soft delete/reactivation으로 전환하고 활성 필터·카운트 보정·계정 삭제 연쇄를 계약 테스트와 MySQL로 확인 |
| `CI-PR391-01` | `PASS` | PR #391 selector·frontend·backend(1,692 tests)·worker Docker 필수 체크 통과 후 `dev` merge |
| `DEPLOY-LIVE-01` | `PASS` | backend/DB run `29189589610`과 PR #395 run `29190817227`: 관리형 DB 패치 준비·적용, backend/worker image 배포, EC2 readiness 성공 |
| `WEB-LIVE-01` | `PASS` | PR #392 run `29190198851`, PR #395 run `29190817238`: TLS server root `/var/www/careertuner`, AASA origin 사전검증, 웹 교체, 공개 Asset Links/AASA 본문·JSON MIME 검증. 외부 재확인에서 `/`, health, readiness 모두 200·DB `UP` |
| `ANDROID-LIVE-01` | `PASS` | run `29190211049`, release `live-pr392`: release 서명·패키지 보안 XML 검증, APK SHA-256 `b4d4986fea45489843ceeefad1473c1ed57651fcdceb3dd00fefcdf4205fd2e8`. API 35 설치·cold launch·도메인 `verified`·`/auth/callback` MainActivity 전달 확인 |
| `SITES-BACKUP-01` | `PASS` | PR #395 Sites source `8ce73dc1acd7b0a1c1b7fdde9fe7b80e1450df0d`, version 5를 owner-only 백업 `https://sites.example.com`에 게시. root 200, `/__backup/health` `UP`·upstream 200 확인 |
| `SITES-BACKUP-20260714-02` | `PASS_TARGETED` | 현재 공개 주소 `https://sites.example.com`의 root 200, `/__backup/health` 200·`UP`, upstream 200을 2026-07-14 재확인. 이전 namespace 주소는 위 역사 증거로만 유지 |
| `OUTAGE-FALLBACK-01` | `PASS_TARGETED` | PR #395: DB 연결 계열만 503, 제약·일반 오류 500 유지. Sites worker가 `/api/health/ready`로 DB 장애를 판별하며 정상·503·비정상 3경로 테스트, backend 전체 CI, AWS/Sites 재배포 통과 |
| `AWS-CONFIG-20260714-01` | `PASS_TARGETED` | 공개 AWS `/api/health/ready`에서 서비스·DB `UP`. Google·Kakao·Naver 모두 활성이고 공식 provider host·운영 callback·비-placeholder client ID 계약을 확인. 공급자 계정 동의·callback 로그인과 유료 AI 실출력은 이 확인에 포함하지 않음 |
| `BILLING-BOUNDARY-20260714-01` | `PASS_TARGETED` | PG 승인 없이 유료 권한을 부여하던 legacy 구독·크레딧 API 제거. Spring MVC 404 계약, Toss ready/confirm/cancel 보존, 승인 후 구독·크레딧 반영, FREE 취소, 프런트 경로 부재를 표적 테스트로 확인 |
| `NCS-CONTRACT-20260714-01` | `PASS_TARGETED` | NCS 코드 정규화·입력 검증·dry-run 무접속·축소 입력 및 미확인 전체 교체 거부·TLS 모드·자격증 적재 계약 14건 통과. 중복 복합키를 canonical `ncs_code`로 이관하는 재실행 가능 DB 패치와 MySQL fixture를 추가했으며 실제 MySQL 결과는 CI 증거에서 별도 확인 |

## A~F 및 플랫폼 체크리스트

| 체크 | 상태 | 이번에 확인·보강한 경계 | 증거 |
| --- | --- | --- | --- |
| A-AUTH-ACCOUNT | PASS_TARGETED | 익명·USER 관리자 fail-closed, 동의·세션 경계, 계정 삭제 시 프로필·파일·관계 정리, 운영 OAuth·SMS 수동 live 완주. PR #448에서 인증은 유지하면서 정확한 AutoPrep 복구만 동의 의존성에서 분리 | BE-FULL-01, FE-ALL-01, WEB-UI-01, OAUTH-LIVE-MANUAL-20260714-01, SMS-LIVE-MANUAL-20260714-01, AUTOPREP-LIFECYCLE-20260714-01 |
| A-PROFILE | PASS_TARGETED | 프로필 버전 스냅샷, AI 분석 provenance, 관리자 버전 이력, 삭제 계정 파일 정리. 전체·섹션 동시 편집 3-way merge와 저장·가져오기 경쟁 응답 방어 | BE-FULL-01, DB-FRESH-01, WEB-UI-01, PROFILE-CONCURRENCY-20260714-01 |
| A-SETTINGS-ADMIN | PASS_TARGETED | 관리자 메뉴 그룹·exact CRUD 권한, 권한 없는 탭/동작 비노출, 실제 사용자 표시 | FE-ALL-01, WEB-UI-01 |
| B-APPLICATION | PASS_TARGETED | 지원 건 overflow, mock/실 모델 계약, soft delete, provenance. 같은 공고 파일 재시도의 지원 건 멱등 생성과 사용자별 실행 취소 경계 | BE-FULL-01, FE-ALL-01, AUTOPREP-LIFECYCLE-20260714-01 |
| B-COMPANY-ANALYSIS | PASS_TARGETED | 삭제 사용자·지원 건의 분석 제외, 관리자 조회 경계 | BE-FULL-01, DATA-LIFECYCLE-01 |
| B-JOB-ANALYSIS | PASS_TARGETED | 공고·파일 소유권, 파이프라인 정리, 삭제 데이터 비노출. 첨부 재시도·교체·취소 시 pending 파일 회수와 중복 case 방지 | BE-FULL-01, FE-ALL-01, AUTOPREP-LIFECYCLE-20260714-01 |
| C-DASHBOARD | PASS_TARGETED | 할 일 soft delete, 관리자 활성 집계, mock 대시보드 | BE-FULL-01, FE-ALL-01 |
| C-FIT-ANALYSIS | PASS_TARGETED | 프로필 버전→적합도, 관리자 active filter, H2 픽스처 정합. AutoPrep 적합도 handler까지 취소 전파와 terminal SSE 경계 확인 | BE-FULL-01, DB-FRESH-01, AUTOPREP-LIFECYCLE-20260714-01 |
| C-CAREER-PLANNER | PASS_TARGETED | 장기 분석·플래너 active filter와 soft delete, 데스크톱 웹 인계 | BE-FULL-01, DESKTOP-01 |
| D-INTERVIEW | PASS_TARGETED | A/B/C snapshot context·provenance, 사용자 선택 모델 재시도, terminal SSE error. 취소·실패가 질문 생성 전에 끝나면 빈 면접 세션을 정리하고 질문 생성 완료 세션은 유지 | BE-FULL-01, FE-ALL-01, AUTOPREP-LIFECYCLE-20260714-01 |
| D-MEDIA-HANDOFF | PASS_TARGETED | 답변·미디어 소유권, 임시 파일 정리, 실제 Browser STT | BE-FULL-01, FE-ALL-01, ANDROID-01 |
| D-REPORT | PASS_TARGETED | 리포트·복습 lifecycle과 데스크톱 패키지 | BE-FULL-01, DESKTOP-01 |
| E-CORRECTION | PASS_TARGETED | 4종 첨삭, C 적합도·D 답변 원문 연결, provenance, soft delete, 사용자 모델 선택. AutoPrep 첨삭 handler의 사용자 취소 전파 확인 | BE-FULL-01, FE-ALL-01, WEB-UI-01, AUTOPREP-LIFECYCLE-20260714-01 |
| E-BILLING-CREDITS | PASS_TARGETED | AI 실행 전 과금 preview·멱등키, 실패 시 정산 경계, Toss 승인 후에만 유료 권한 부여, FREE 취소, 데스크톱 결제 인계 | BE-FULL-01, FE-ALL-01, DESKTOP-01, BILLING-BOUNDARY-20260714-01 |
| F-COMMUNITY | PASS_TARGETED | 글·태그·반응·스크랩 soft delete/reactivation, 카운트 보정, 공개 activity 보안 경계, 390px. 차단·탈퇴 작성자를 pagination 전에 제외하고 관계 재활성화·작성자 공개 범위 상속 보강 | BE-FULL-01, WEB-UI-01, ANDROID-01, COMMUNITY-PRIVACY-20260714-01 |
| F-NOTIFICATION-COLLAB | PASS_TARGETED | 알림·구독·친구·대화 권한 데이터 active filter와 계정 삭제 정리 | BE-FULL-01, DATA-LIFECYCLE-01, DESKTOP-01 |
| F-SUPPORT-CONTENT | PASS_TARGETED | FAQ 검색, 비로그인 문의, 공유 AI 상담, 실제 STT, 모호 질문 원문 재실행. 온보딩 프로필 병합·저장의 stale 응답 방어와 계정 범위 유지 | FE-ALL-01, WEB-UI-01, PROFILE-CONCURRENCY-20260714-01 |
| DATA-LIFECYCLE | PASS_TARGETED | 기존 checksum 적용기, orphan 방지 검증, canonical NCS 코드 계약을 유지. PR #448에서 pending 첨부 정리, 프로필 import 원본 정리, 커뮤니티 관계 soft delete와 AutoPrep 멱등 patch·mapper 계약을 재검증하고 신규 patch를 운영 DB에 적용 | DB-FRESH-01, DB-DEPLOY-01, DATA-LIFECYCLE-01, DEPLOY-LIVE-01, NCS-CONTRACT-20260714-01, DB-PATCH-LIVE-20260714-01, PROFILE-CONCURRENCY-20260714-01, AUTOPREP-LIFECYCLE-20260714-01, COMMUNITY-PRIVACY-20260714-01, DB-PATCH-PR448-20260714-01 |
| PLATFORM-WEB | PASS_TARGETED | mock/AWS 빌드, 영향 경로 실브라우저, 반응형·다크·권한 경계, AWS live·현재 Sites 주소·DB outage 전환. PR #448 공유 프런트의 typecheck/build와 표적 회귀 후 운영 웹·mock 데모를 재배포하고 root 200·AASA/Asset Links를 확인 | FE-ALL-01, WEB-UI-01, WEB-LIVE-01, SITES-BACKUP-20260714-02, OUTAGE-FALLBACK-01, OAUTH-CONTRACT-20260714-01, OAUTH-LIVE-MANUAL-20260714-01, DB-ROTATION-20260714-01, CI-PR448-20260714-01, DEPLOY-PR448-20260714-01 |
| PLATFORM-MOBILE | PASS_TARGETED | 기존 Android debug 실기·verified App Link 증거를 유지하고 PR #448 공유 프런트를 최신 live 서명 APK로 다시 번들해 서명·보안 manifest·Asset Links를 검증. 에뮬레이터 실기는 기존 증거를 승계 | FE-ALL-01, ANDROID-01, ANDROID-LIVE-01, CI-PR448-20260714-01, ANDROID-PR448-20260714-01 |
| PLATFORM-DESKTOP | PASS | 실제 Release 패키징·실행·로그인·테마·웹 인계 | DESKTOP-01 |
| RELEASE-GATE | PASS_TARGETED | PR #391·#392·#395의 전 영역 실기 기준과 운영 OAuth·유료 AI·SMS 수동 live 증거를 유지한다. PR #448의 프로필 동시 편집, AutoPrep 생명주기·멱등성, 커뮤니티 개인정보·soft delete는 필수 CI를 통과했고 운영 DB patch·백엔드·웹·mock 데모·최신 Android live APK 배포까지 완료했다. 데스크톱 네이티브 소스는 변경되지 않아 기존 Release 패키지 실기 증거를 승계 | CI-PR391-01, DEPLOY-LIVE-01, WEB-LIVE-01, ANDROID-LIVE-01, SITES-BACKUP-20260714-02, OUTAGE-FALLBACK-01, OAUTH-LIVE-MANUAL-20260714-01, PAID-AI-LIVE-MANUAL-20260714-01, SMS-LIVE-MANUAL-20260714-01, PROFILE-CONCURRENCY-20260714-01, AUTOPREP-LIFECYCLE-20260714-01, COMMUNITY-PRIVACY-20260714-01, CI-PR448-20260714-01, DB-PATCH-PR448-20260714-01, DEPLOY-PR448-20260714-01, ANDROID-PR448-20260714-01 |

## 실환경 검증과 추후 운영 항목

| 항목 | 상태 | 현재 판정 | 재검증 또는 후속 조건 |
| --- | --- | --- | --- |
| Google/Kakao/Naver OAuth 운영 로그인 | PASS_MANUAL | 공급자별 동의→AWS callback→CareerTuner 세션 발급 완료. `OAUTH-CONTRACT-20260714-01`의 자동 계약과 `OAUTH-LIVE-MANUAL-20260714-01`의 수동 live 증거를 함께 사용 | client credential, redirect URI, callback 또는 세션 계약 변경 시 |
| 실 유료 AI runtime | PASS_MANUAL | Claude Haiku·OpenAI GPT 비-mock 실출력과 provider 귀속 확인. 장애 독립 mock/규칙 폴백도 유지 | API key, 모델, provider chain 또는 trace 계약 변경 시 |
| SMS 운영 인증 | PASS_MANUAL | 실제 단말 수신·코드 인증·재요청/제한 시나리오 완료 | SMS provider, credential, 인증 또는 제한 정책 변경 시 |
| Apple 개발자·iOS Universal Link | DEFERRED | Apple 개발자 활성화 후 테스트 중. Team ID 미발급 상태의 AASA deny-all은 의도된 안전 기본값이며 현재 웹·Android·데스크톱 시연을 막지 않음 | 추후 Team ID 발급 시 `IOS_APP_LINK_TEAM_IDS` 등록→웹 재배포→AASA `details`와 iOS Universal Link 확인 |

mock은 운영 API 장애 때만 사용하는 독립 백업이며, 정상 운영 중에는 AWS 실서버가 우선이다.
