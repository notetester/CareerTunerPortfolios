# TripTogether → CareerTuner 이식 패리티 매트릭스

> **보관 문서:** 2026-07-05 비교 스냅샷이다. 이후 구현 상태는 런타임 소스와 [시연 준비도 원장](../../verification/DEMO_READINESS_LEDGER.md)을 기준으로 판단한다.

- 기준일: 2026-07-05
- 소스: TripTogether 감사 스펙 8종(admin-grid / admin-permissions / security-block / audit-logs / ops-report-inquiry-ads / member-company-email / 커뮤니티 상호작용 / 채팅·공개범위) + CareerTuner 코드 직접 조사(schema.sql, db/patches, backend 패키지, frontend admin/features)
- 목적: **중복 구현 방지**. CT에 이미 있는 자산은 "있음/부분"으로 명시하고, 갭만 이식 대상으로 삼는다.

## 0. 읽는 법

| 표기 | 의미 |
| --- | --- |
| P1 | 사용자 명시 요구 또는 다른 P1의 선행 기반. 다음 릴리스 사이클 착수 |
| P2 | 가치 확실하나 P1 이후. 독립 모듈이거나 P1 완료 후 얹는 것 |
| P3 | 골격만 예약 또는 인프라 확정 후. 당장 구현 가치 낮음 |
| S/M/L | S=1~2일, M=3~7일, L=1~2주+ (백엔드+프런트+관리자 화면 포함 체감 규모) |
| 있음 | CT에 동등 기능 존재 — 이식 불필요, 재사용 |
| 부분 | 뼈대/데이터만 있고 TT 수준 미달 — 갭만 채움 |
| 없음 | 신규 구현 |

### CT 기존 자산 요약 (중복 구현 금지 목록)

| 자산 | 위치 |
| --- | --- |
| 신고 접수/처리 콘솔 + AI 분류 | `post_report`/`comment_report`(reporter+target UNIQUE), `admin/community/AdminReportController`(HIDDEN/DELETED/DISMISSED/RESTORE), `community/moderation/event/ReportClassifyListener` |
| 문의(티켓) 스레드 + AI 답변 초안 | `support_ticket`/`support_ticket_message`, `admin/ticket/*`(TicketDraftAiClient) |
| AI 검열 파이프라인 + 자동 제재 | `community/moderation`(LLM 게이트웨이 Anthropic/OpenAI/Ollama/Mock, 비동기 이벤트, 재시도 스케줄러), `ai_moderation_setting`(strictness/hide_threshold/sanction_threshold/block_days), `UserSanctionService` |
| 알림 시스템 | `notification`(+sender_relation), `notification_preference`(categories/rules/keywords/quiet hours), FCM/VAPID 푸시, `AdminNotificationFanoutService`(NEW_REPORT/NEW_TICKET role 팬아웃) |
| 개인 차단/공개범위 3층 모델 | `user_block`/`user_ip_block`/`conversation_block`/`user_privacy_policy` + `docs/PERSONAL_BLOCK_POLICY.md`(표면 카탈로그·상속·집행 지점) |
| 로그인 감사 + 상태 이력 + 자동 잠금 | `user_login_history`(LOGIN/LOGOUT/REFRESH, fail_reason, ip/ua), `user_status_history`, `AuthServiceImpl` 5회 실패→10분 잠금+만료 자동 해제 |
| 관리자 권한 **데이터 모델** | `admin_permission_policy`/`admin_permission_group(_item)`/`admin_user_permission`/`admin_user_group`/`admin_permission_audit`/`admin_permission_menu_group`(patches 20260624·20260702) + `SuperAdminController`(/api/admin/super) CRUD |
| 운영 정책 스케줄 | `admin_system_policy`(DORMANT_ACCOUNT/FAILED_LOGIN_LOCK/EMAIL_TOKEN_CLEANUP/AI_USAGE_RETENTION) + `admin/policy/AdminPolicyController`(수정/즉시 실행) |
| 관리자 조치 로그 | `admin/ops/AdminActionLog*` + 프런트 `admin/features/action-logs` |
| 관리자 화면(도메인별) | users(검색+상세 컨텍스트+상태 변경+차단 목록), audit/security, audit/email, community, inquiries, moderation, notices, faq, policies, super-admin 등 39 라우트 |
| 이메일 토큰 + 세션 감사 | `email_verification`(VERIFY/RESET_PW), `refresh_token`(ip/ua) |
| OAuth 소셜 | `user_social`(KAKAO/NAVER/GOOGLE, provider별 1개) |
| 리액션 토글 기본형 | `post_reaction`/`comment_reaction` + `ReactionServiceImpl`(LIKE·BOOKMARK, 토글취소, self-like 방지, 동시성 충돌 흡수, LIKE 알림) |

---

## 1. 관리자 공통 그리드/검색 프레임워크 (TT: admin-grid)

CT 실태: 관리자 목록 화면은 도메인별 개별 구현이며 공통 그리드 계층이 없다. 대표 사례 `admin/features/users/api.ts`는 keyword/status/role/limit(기본 50)만 지원 — **서버 페이징 없음, 컬럼 정렬 없음, 내보내기 없음, 일괄 작업 없음**. 공통 컴포넌트는 `admin/components/AdminShell.tsx` 뿐. 백엔드 `admin/common`은 `AdminAccess`(role 검사)와 `AdminMemoRequest` 뿐이고 `common/web`엔 `ApiResponse`만 있다(PageResult 없음).

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 목록 요청/응답 공통 계약 | SearchVO(@ModelAttribute)+이중 화이트리스트 정규화(VO getter Set→서비스 normalize), X-Section-* 헤더 | 없음 — 도메인별 ad-hoc 파라미터, limit 슬라이스만 | `admin/common/dto/AdminListRequest`(keyword, searchType, 필터 Map, dateFrom/To, sortBy/sortDir, page, size)+`PageResult<T>`(items,total,page,size,totalPages). 응답은 `ApiResponse<PageResult<T>>` — TT의 fragment/api/page 3종·X-Section-* 헤더는 JSON 단일 엔드포인트로 축약 | P1 | M |
| 페이징/정렬 SQL 표준 | mapper XML `<sql id=SearchBase>` 공유 조각 + count/find 분리 + `<choose>` ORDER BY 화이트리스트 + ${sortDir} ASC/DESC 강제 + 타이브레이커 + count 후 page 클램프 재조회 | 없음 | 도메인당 5종 세트 템플릿(SearchBase/find(LIMIT·OFFSET)/count/findForExport(상한)/findByIds). ${} 보간은 sortDir에만 허용을 리뷰 체크리스트로 명문화. keyword OR-LIKE 컬럼은 3~4개 제한 | P1 | M |
| 프런트 제네릭 그리드 | 2계층(제로컨피그 DOM 강화기 + 페이지별 복붙 상태기계), SERVER/CLIENT 듀얼 모드, 모드 localStorage 영속 | 없음 — 페이지별 단순 테이블 | `admin/features/common/`에 `useAdminList` 훅(TanStack Query, queryKey=[domain,filters,sort,page,size,mode])+`<AdminDataGrid>`(columns/fetcher/filterSchema/bulkActions/exportConfig/rowKey). TT의 DOM 캐시+filterKey 무효화는 React Query 캐시가 대체. CLIENT(전체 로드) 모드는 size 상한(5000) 1회 fetch 후 useMemo 로컬 처리 | P1 | L |
| 정렬 UX 규약 | 단일 컬럼, 활성 컬럼만 ▲빨강(ASC)/▼파랑(DESC), 정렬 중일 때만 초기화 버튼 | 없음 | 규약 그대로, 색은 디자인 토큰화. 모드·pageSize localStorage 영속 | P1 | S |
| 내보내기 | scope=all\|search\|selected\|page × csv/excel(POI 수기 매핑, 전량 메모리) + 클라 DOM 스크랩 | 없음 | `GET /api/admin/{x}/export` 규약 유지하되 CSV는 스트리밍(BOM+RFC4180 유틸), Excel은 SXSSFWorkbook + 행 상한(50k). `ExportColumn<T>`(header, extractor) 리스트 하나로 CSV/xlsx 겸용 — TT 대비 1/5 코드. POI 의존성은 build.gradle 추가(공통 영역 → 팀장 승인) | P1 | M |
| 일괄 작업 | form-encoded 반복 파라미터, 고정 슬롯 bulk bar, indeterminate 전체선택, 서버 가드(본인 제외·양수·중복 제거·화이트리스트) | 없음 | `POST /api/admin/{x}/bulk/{action}` JSON `{ids:[],params:{}}` 통일. `<AdminBulkBar>` 고정 슬롯 + 성공 후 invalidateQueries+선택 해제. 서버 가드 3종 그대로 | P1 | M |
| 기존 화면 리마운트 | — | users/audit/community 등 기존 목록이 limit 슬라이스 | 프레임워크 완성 후 users → audit 2종 → community/reports 순으로 교체(신규 화면은 처음부터 프레임워크 사용) | P2 | M |

> 배치: `frontend/src/admin/features/common/{components,hooks,types}` + `backend admin/common/{dto,support}`. **공통 컴포넌트 신설이므로 AGENTS.md 규칙상 팀장 승인 필요.**

---

## 2. 관리자 계정/권한 체계 (TT: admin-permissions)

CT 실태: 권한 **데이터 모델과 SuperAdmin CRUD는 이미 있다**(임명/해제, 개별 권한 grant/revoke, 그룹 배정, 감사 조회, 권한/그룹 정책 CRUD, menu_group). 그러나 **집행이 없다** — `AdminAccess`는 role(ADMIN/SUPER_ADMIN)만 검사하고, 어떤 컨트롤러도 `admin_user_permission`을 실시간 조회하지 않으며, 관리자 알림 팬아웃(`AdminRecipientMapper`)도 role IN(ADMIN,SUPER_ADMIN) 전체 대상이다.

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 권한 카탈로그/그룹/배정 모델 | 정책+그룹+템플릿 3단 번들, soft-toggle, 3-actor 이력 | **있음(부분)** — policy/group/user_permission/user_group/audit 테이블+CRUD(`admin/superadmin`). 템플릿(실효 권한 코드) 계층만 없음 | 템플릿 계층은 도입하지 않는다(TT 스스로 혼란 유발 평가). 현행 정책+그룹 2단 유지 | — | — |
| 실효 권한 계산 | ADMIN_EFFECTIVE_PERMISSION_VW 4-way UNION DISTINCT, permission_source 컬럼 | 없음 — 배정 데이터는 있으나 합산 조회 쿼리 없음 | DB VIEW 대신 MyBatis `<select>` 2-way UNION DISTINCT(직접 권한 ∪ 그룹 경유), `permission_source` 유지("어디서 왔는지" UI). `SuperAdminMapper`에 추가 | P1 | S |
| 권한 집행 | 세션 캐시+URL prefix 인터셉터+컨트롤러 재검사 3중 | 없음 — `AdminAccess.requireAdmin/requireSuperAdmin` role 검사뿐 | `@RequirePermission("REPORT_ADMIN")` 어노테이션 + HandlerInterceptor(HandlerMethod 어노테이션 → 실효 권한 조회, Caffeine 30~60초 캐시 + 변경 시 evict). SUPER_ADMIN role은 전체 통과 단일화. 응답은 ApiResponse 403. **인증/권한 공통 영역 → 팀장 승인** | P1 | M |
| 프런트 메뉴/라우트 가드 | AdminModeInterceptor가 hasXxx 17개 불리언 모델 주입 | 없음 — AdminShell 사이드바는 role 무관 전체 노출 | `GET /api/admin/me`(permissions: string[]) → React context/query 저장 → 라우트 가드+사이드바 조건부 렌더. 서버 검증이 최종 방어선 | P1 | S |
| 신고/문의→권한별 관리자 알림 | role 전체 브로드캐스트+카테고리 opt-out(ADMIN_NOTIFICATION_PREFERENCE) | **부분** — NEW_REPORT/NEW_TICKET role 팬아웃 있음(`AdminNotificationFanoutService`), 권한 필터·opt-out 없음 | 수신자 쿼리를 실효권한 조인으로 교체: 대상=(해당 권한 실효 보유자 ∪ SUPER_ADMIN) − opt-out. opt-out은 기존 `notification_preference.categories_json`에 관리자 카테고리 키 추가로 흡수(신규 테이블 불필요). **사용자 명시 요구** | P1 | S |
| 권한 요청→승인 워크플로 | 미완(컨트롤러 미연결 휴면) | 없음 | 도입 시 status 컬럼(PENDING/APPROVED/REJECTED) 명시형으로 — 당장 불필요 | P3 | M |
| 조직도/급여 Excel 파이프라인 | admin_manager self-FK 트리, 2-phase preview→apply+batch 감사 | 없음 | CT 관리자 규모(6인)에 과설계 — 보류. 2-phase Excel 패턴만 다른 기능(공고 대량 등록 등)에 재활용 후보 | P3 | L |
| 안전 가드 | SUPER_ADMIN 최소 1명, 자기 자신 변경 금지, 보호 role | 부분 — 확인 필요한 수준(SuperAdminService 내) | grant/revoke·role 변경 경로에 3종 가드 명시적 테스트 추가 | P2 | S |

---

## 3. 보안/차단 — 사이트 차원 (TT: security-block)

CT 실태: **개인** 차단(user_block 등)은 완성도가 높으나 **사이트(운영자) 차원** IP/CIDR 차단·로그인 위험 정책·검토 큐·이의제기는 전무. 계정 잠금은 하드코딩 상수(5회/10분)로 동작하고 `admin_system_policy`의 FAILED_LOGIN_LOCK 설정값과 연동되어 있지 않다.

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 계정 로그인 실패 잠금 | LOGIN_RISK_POLICY(관찰창/임계/잠금시간/경고 카운트다운/반복잠금 보호) | **부분** — `AuthServiceImpl` 5회→10분 잠금+`user_status_history` 기록+만료 자동 해제. 정책 테이블 미연동, 경고 카운트다운 없음 | 하드코딩 상수를 `admin_system_policy.FAILED_LOGIN_LOCK.config_json`(maxFailedCount/lockMinutes — 이미 seed됨)에서 로드하도록 연결 + "남은 시도 N회"를 ApiResponse 에러 payload에 실어 프런트 노출 | P2 | S |
| IP 단위 로그인 잠금 | IP 실패수+distinct 계정 임계→IP 잠금 카운터 | 없음 | login_risk_counter 축소판(policy_code, subject_type, subject_key, count, blocked_until). 소스는 user_login_history의 ip_address 집계 | P2 | M |
| 사이트 IP/CIDR 차단 룰 | 3층(history/blocklist/메모리 캐시)+priority 순회+ALLOW 우선 | 없음(개인 IP 차단과 별개) | BLOCK_RULE(target_key unique)+BLOCK_HISTORY+BLOCK_ACCESS_LOG 축소판. SINGLE_IP/CIDR만 시작, RANGE/COUNTRY/ASN은 컬럼 예약. 만료는 조회 시 필터(스케줄러 불필요). OncePerRequestFilter로 /api/** 앞단. SPA라 403+`{blocked,requestId,reason}` envelope → axios 인터셉터가 차단 화면 라우팅 | P2 | L |
| 검토 큐+판단 결과 | SECURITY_RISK_ASSESSMENT+SECURITY_REVIEW_QUEUE 상태기계 | 없음 | 2테이블만(로그인 전용 이원화 금지). approve→APPLIED, reject→IGNORED, hold→PENDING. 결정→집행 단일 @Transactional | P3 | M |
| 이의제기(이메일 인증) | verify→token→submit→publicRequestId→result lookup + rate-limit 정책 | 없음 | 사이트 차단 도입 후에만 의미. 메일 인프라 재사용, rate-limit 기본값 seed 그대로 | P3 | L |
| 외부 판단 Provider 프레임워크 | 요청 템플릿+JSON Pointer 응답 매핑+fail_open+api_key_ref(ENV:) | 없음 — CT는 자체 `ai/common` LLM 게이트웨이 보유 | 이식하지 않음. 검열/판정은 기존 moderation LLM 게이트웨이 확장으로 해결(ai/common 공통 영역 → 팀장 승인 대상) | P3 | — |
| WAF 동기화 큐 | 승인→큐→Provider 체인→SYNCED, Mock 어댑터 | 없음 | 실 WAF 부재 — 보류. 도입 시 status varchar(40) 함정 주의 | P3 | M |

---

## 4. 감사 로그 3종 (TT: audit-logs)

CT 실태: `user_login_history`(LOGIN/LOGOUT/REFRESH) 있음. 관리자 조치는 `admin_action_log`(admin/ops) 있음. **요청 상관관계(request_id/flow_trace_id) 없음, 전역 활동 로그 없음, 보안 이벤트 단계별 감사 없음.** 관리자 화면은 audit/security·audit/email 2종 + 회원 상세 내 로그인 이력 드릴다운 존재.

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 로그인 감사 | USER_LOGIN_HISTORY(auth_flow 세분화, session_id, request_id/flow_trace_id, 전 단계 fail_reason) | **부분** — `user_login_history`에 event_type/fail_reason/ip/ua/request_uri 있음. request_id/flow_trace_id/session_id 없음 | 컬럼 3개 추가(request_id, flow_trace_id, session_id≒refresh_token id)가 최우선 갭 — 3종 로그를 하나의 사건으로 묶는 계약. **schema.sql 공통 영역 → 팀장 승인** | P2 | S |
| 보안 이벤트 이력 | USER_SECURITY_HISTORY(event_type×event_stage REQUEST/ISSUE/VERIFY/COMPLETE, 대상/실행자 이원화, 실패 코드는 로그에만) | 없음 — email_verification 행 자체가 유일한 흔적 | `user_security_history` 신설 + `recordSecurityEvent(대상, 실행자, type, stage, 입력값, 이메일, 성공, fail_reason, ctx)` 시그니처 도입. 계정 존재 비노출 정책(응답 동일, 로그에만 사유) 동반 | P2 | M |
| 일반 활동 로그 | ActivityLogInterceptor(/** 전역, URI 휴리스틱 분류, 민감키 마스킹, best-effort) | 없음 | HandlerInterceptor: preHandle에서 request_id(UUID)→MDC+attribute, afterCompletion에서 URI/핸들러/status/응답시간 @Async INSERT(TT는 동기 — 개선). URI 휴리스틱 대신 `@ActivityLog(domain,code,targetType)` 선언적 어노테이션. 마스킹(token/password/code→***)과 예외 삼킴 계약 유지. 90일 삭제 배치를 처음부터 설계 | P2 | M |
| 교차 드릴다운 | 회원 상세 묶음 컨텍스트, IP 컨텍스트, requestKey 정확일치 필터 | **부분** — `AdminUserController` 상세가 login-history/consents/refresh_tokens/email_verifications/AI usage 묶음 반환. IP 횡단·requestKey 없음 | `GET /api/admin/audit/by-ip` + 목록 API에 requestKey 파라미터 추가. 관리자 UX 가치의 절반이 이 횡단 추적 | P2 | S |
| 보존 정책 | 없음(TT 약점) | 없음 | activity_log만 90일 배치 삭제, login/security는 장기 보존+FK SET NULL 익명화 — admin_system_policy에 정책 행 추가 | P2 | S |

---

## 5. 운영 — 신고/문의/광고/커뮤니티 콘솔/검열 (TT: ops-report-inquiry-ads)

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 신고 접수(글/댓글) | 3중 중복 방어(사전 SELECT→CANCELLED 재활성화→UNIQUE catch), 접수는 무조건 판단 큐 | **있음(부분)** — post_report/comment_report+UNIQUE, ReportDialog UI, AI 자동 분류. CANCELLED 재활성화·본인 취소 없음 | 갭만: status에 CANCELLED 추가+재활성화 UPDATE 경로, DataIntegrityViolation catch→409. 사용자 신고 취소 API | P2 | S |
| 유저 신고(출처 컨텍스트) | target_type=user + source_type/source_id | 없음 — 콘텐츠 신고만 | user_report 또는 report 통합 테이블에 source 컨텍스트 컬럼. 닉네임 클릭 진입점 | P2 | M |
| 신고 처리 콘솔 | resolve 액션 스위치(기각/삭제/작성자 차단/삭제+차단/복원), resolver·resolve_action 기록, 신고자 알림 | **있음(부분)** — HIDDEN/DELETED/DISMISSED/RESTORE + DELETED 종착 가드(`AdminReportServiceImpl`). BLOCK_AUTHOR 액션·신고자 결과 알림·resolver 기록 없음 | BLOCK_AUTHOR/DELETE_AND_BLOCK 액션 추가(기존 UserSanctionService 재사용), 처리 시 신고자에게 notification 발행, admin_id/resolved_at은 이미 있으므로 채우기만. 트랜잭션은 이미 단일 서비스(TT보다 낫다 — 유지) | P1 | S |
| 신고→권한별 관리자 알림 | 미구현 갭(TT도 없음) — CT 신규 설계 | **부분** — NEW_REPORT role 팬아웃 있음 | §2의 실효권한 기반 라우팅으로 교체(중복 기재: 소유는 §2) | P1 | S |
| BLUR 점진 공개 | report_count 캐시+임계 도달 시 파생 표시(상태 전이 없음), 오버레이 클릭 해제, 작성자 알림 1회 | 없음 — CT는 HIDDEN(완전 숨김) 단일 | community_post에 report_count 캐시 컬럼+reconcile 쿼리, 응답 DTO에 blurred/blurReason 서버 계산, React blur 오버레이(로컬 해제). 기존 POST_HIDDEN 알림 옆에 '가림' 알림 추가. clear-blur=카운트 리셋 | P2 | M |
| 게시판 인라인 운영자 모드 | 세션 viewMode 토글, 일반 화면에 체크박스/차단/삭제/BLUR해제 인라인, 일괄 7종 | 없음 — 관리자는 admin 콘솔로만 | **사용자 명시 요구.** React 전역 상태(Zustand+localStorage)로 adminViewMode, 목록 API `includeBlocked=true` 파라미터(권한 검증 서버측), 인라인 액션은 기존 admin API 재사용+권한 가드 조건부 렌더 | P1 | M |
| 문의 상태머신 확장 | PENDING→IN_PROGRESS→COMPLETED + USER_COMPLETED/CANCELLED/DELETE_REQUESTED/공개전환, 상태별 타임스탬프, 답변 이력 | **있음(부분)** — support_ticket RECEIVED 기반 상태+메시지 스레드(스레드 구조라 답변 이력 테이블 불필요), AI 초안 있음 | 갭만: 사용자 종결(USER_COMPLETED)/취소, PENDING에서만 수정·삭제 규칙, 작성 rate-limit(도배 검사). 삭제요청/공개전환 워크플로는 CT 티켓이 비공개 전용이라 불필요 | P2 | S |
| 도배(스팸) rate-limit | 글/댓글/문의 작성 초입 countRecent 검사, 정책 단일행 | 없음 | `ai_moderation_setting`에 창/허용수 3쌍 컬럼 추가(단일행 정책+캐시 무효화 패턴 이미 동일) — 기존 ModerationSettingService에 흡수 | P2 | S |
| 검열 정책 화면 | 민감도 3단+도배 3쌍+BLUR 임계 단일 폼 | **있음(부분)** — strictness/hide_threshold/sanction_threshold/block_days 폼(`admin/features/moderation`) | report_threshold(신고 누적 블러)와 도배 필드만 폼에 추가 | P2 | S |
| 광고 관리(슬롯/기간/집계) | ad_campaign(slot_code, 기간, link_type 3종, view/click 컬럼 집계), IntersectionObserver 노출, 클릭 302 | 없음 | **사용자 명시 요구(3플랫폼: web/desktop/모바일 대응 + 유료 플랜 사용자 노출 제외).** ad_campaign 이식: slot_code는 CT 화면 코드로, link_target_type은 CT 라우트로. `GET /api/ads/slot/{slotCode}`(활성+기간+랜덤 1건 — **users.plan≠FREE면 빈 응답**), impression/click 엔드포인트, `<AdBanner slotCode>` 컴포넌트(50% 가시 1회 발사). 데스크톱/모바일 앱은 동일 API 소비. 일별 로그 테이블은 후속 | P2 | M |

---

## 6. 회원/기업 계정/이메일 액션/OAuth (TT: member-company-email)

CT 실태: 이메일 단일 식별자+소셜 연동 구조로 TT의 멀티 크리덴셜 복잡도가 불필요. **기업(COMPANY) 계정 개념 자체가 없다**(users.role=USER/ADMIN/SUPER_ADMIN, `company`/`admin/company` 패키지는 package-info 플레이스홀더).

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 기업 신청→승인→전환 | BUSINESS_ACCOUNT_APPLICATION(PENDING 1건 제한)→관리자 승인(FOR UPDATE→role UPDATE→role 이력→APPROVED 단일 트랜잭션)→반려 사유 | 없음 | **사용자 명시 요구.** `company_application`(user_id, company_name, business_number, manager_name/phone, description, status, reject_reason, reviewer_id, reviewed_at) + users.role에 'COMPANY' 추가 + `user_role_change_history` 신설(prev/new/reason/changed_by). 규칙 3개 유지: USER만 신청/PENDING 1건/승인 트랜잭션. 마이페이지 신청 카드(PENDING 배지→REJECTED 사유+재신청), 관리자 승인/반려(+일괄)는 §1 그리드 프레임워크 위에 | P1 | M |
| 기업 신뢰등급 | is_verified_member+member_grade 이원 | 없음 | `company_profile`(user_id 1:1, trust_grade BASIC/VERIFIED/PARTNER, business_number 검증 상태) — 신뢰등급이 §7 공고 승인 정책의 입력값 | P1 | S |
| 신뢰등급별 공고 승인 정책 | (TT에는 패키지 리비전 제출 승인 유사 개념) | 없음 | **사용자 명시 요구.** 정책: trust_grade별 공고 등록/수정 시 즉시 게시 vs 관리자 승인 대기(예: BASIC=사전 승인, VERIFIED=사후 심사, PARTNER=자동 게시). company_job_posting.status(DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED)+revision 제출 모델. 승인 정책값은 admin_system_policy config_json | P1 | M |
| 채용공고 게시판(카테고리+사람인식 상세) | — (CT 신규) | 없음 — 기존 `job_posting`은 지원 건 내부의 공고 원문 텍스트라 **별개 도메인**(재사용 금지, 혼동 주의) | **사용자 명시 요구.** `company_job_posting` 신설: 직무 카테고리(대/중/소), 고용형태, 경력/신입, 학력, 급여(협의/범위), 근무지역, 마감일/상시, 우대사항, 복리후생, 접수방법 등 사람인식 필드. 공고 상세→지원 건 생성 연동(기존 application_case로 가져오기)이 CT 차별점. 목록/검색/상세 + 기업 마이페이지 공고 관리 + 관리자 승인 큐 | P1 | L |
| 이메일 액션 토큰 2계층 | REQUEST 상태기계+토큰 이력, 재발송 시 기존 무효화, flow_trace | **부분** — email_verification 단층(VERIFY/RESET_PW), 재발송 무효화 여부 불명확 | 2계층 전면 이식은 과설계. 갭만: 발송 시 동일 email+purpose 미사용 토큰 만료 처리, EMAIL_TOKEN_CLEANUP 정책(seed 존재) 실행 잡 연결. 이메일 변경 시 '저장 시점 재확인' 패턴은 이메일 변경 기능 도입 시 채택 | P2 | S |
| OAuth 로그인/연동/해제 | provider 3사, state CSRF, 남는 수단 검증 후 해제 | **있음** — user_social 3사 연동 | 해제 시 '남는 로그인 수단 검증'(비밀번호 또는 다른 소셜) 가드 확인·보강만 | P2 | S |
| 휴면 전환 배치 | 5분 스케줄러+SYSTEM_POLICY inactiveDays | **부분** — DORMANT_ACCOUNT 정책 seed+AdminPolicyController 즉시 실행 있음. 스케줄 자동 실행 연결 상태 확인 필요 | 정책 schedule_type 기반 @Scheduled 실행기 확인/보강 + 전환 시 user_status_history 기록 | P2 | S |
| 회원 관리 액션 | 상태 변경 의미론(ACTIVE 복구 시 차단 클리어+알림), 자기 자신 금지 | **있음(부분)** — status PATCH+이력+차단 목록 화면. 차단 해제 알림·자기 자신 가드 확인 필요 | BLOCKED→ACTIVE 시 '차단 해제' 알림 발행, 본인 계정 변경 금지 가드 추가. bulk 상태 변경은 §1 프레임워크 위에 | P2 | S |

---

## 7. 커뮤니티 상호작용 — 리액션/스크랩/구독/복수 닉네임

CT 실태: `ReactionType`은 LIKE·BOOKMARK 2종. 토글취소/동시성 흡수/self-like 방지/LIKE 알림은 이미 견고. 익명 변형·비추천 계열·스냅샷·구독·복수 닉네임 없음.

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 리액션 8종+익명 변형+알림+토글취소 | 추천/비추천/좋아요/싫어요 × 글/댓글, 익명 리액션 변형, 알림, 재클릭 취소 | **부분** — LIKE(글·댓글)+BOOKMARK(글) 토글, LIKE 알림, uk(user,target,type) 유니크 | **사용자 명시 요구.** ReactionType에 RECOMMEND/NOT_RECOMMEND/DISLIKE 추가(글·댓글 공통 4종×2대상=8종), `post_reaction`/`comment_reaction`에 `is_anonymous` 컬럼 추가. 배타 규칙 서비스 처리(추천↔비추천, 좋아요↔싫어요 상호 배타 — 같은 축 전환 시 기존 행 교체), 카운트 캐시 컬럼 4종화. 알림: 긍정 리액션만 발행(비추천/싫어요는 무알림), 익명 리액션은 actor 마스킹(sender_relation 재사용). 기존 토글·동시성 패턴 유지 | P1 | M |
| 즐겨찾기/스크랩(스냅샷) | 스크랩 시점 콘텐츠 보존 | **부분** — BOOKMARK 리액션+bookmark_count는 있으나 원본 삭제/수정 시 유실, 스크랩 목록 화면 없음 | **사용자 명시 요구.** `post_scrap`(user_id, post_id, snapshot_title, snapshot_content, snapshot_author_label, scrapped_at, origin_status) 신설 — 스크랩 시점 스냅샷 저장, 원본 삭제 시 "원본이 삭제된 글" 배지로 스냅샷 열람. 마이페이지 스크랩 목록(폴더/검색은 후속). 기존 BOOKMARK 리액션은 스크랩 생성의 트리거로 흡수(이중 저장 금지) | P1 | M |
| 글 구독 알림 | 글 단위 구독→새 댓글/상태 변경 알림 | 없음 — COMMENT_REPLY(내 댓글에 답글)만 | **사용자 명시 요구.** `post_subscription`(user_id, post_id, uq) + 댓글 생성 이벤트에서 구독자 팬아웃(작성자 자동 구독 옵션, 본인 행위 제외, 차단 관계 억제는 기존 notify가 처리). notification_preference 카테고리에 POST_SUBSCRIBE 추가 | P1 | S |
| 복수 닉네임 프로필 | 계정 1개에 표시용 닉네임 프로필 여러 개(글별 선택) | 없음 — users.name 단일 + is_anonymous 플래그뿐 | **사용자 명시 요구.** `user_nickname_profile`(user_id, nickname UNIQUE 전역, avatar, is_default, status) + community_post/comment에 `nickname_profile_id` 컬럼. 작성 폼에서 프로필 선택(익명 옵션과 병행). 제재/신고/차단은 **계정 단위**로 귀속(프로필은 표시 계층일 뿐 — 개인 차단 masked_label 패턴과 정합). 기존 글은 기본 프로필로 백필 | P1 | M |
| 활동 목록 공개범위(항목별·차단대상별) | 마이페이지 활동(작성 글/댓글/리액션 등) 항목 유형별 공개범위 + 관계/차단대상별 차등 | **부분** — `user_privacy_policy` 표면 카탈로그에 content.post/comment/reply·profile.* 존재(뷰어 기준 필터 집행 지점 문서화됨). '활동 목록' 단위 표면 없음 | 기존 3층 정책 모델 **확장**(신규 시스템 금지): 표면 키 `activity.posts`/`activity.comments`/`activity.reactions`/`activity.scraps` 추가 + 프로필 페이지 활동 탭 조회 시 뷰어 관계 평가. UI는 기존 고급 매트릭스에 카테고리 1개 추가 | P2 | M |

---

## 8. 채팅방 설정 (TT: 채팅 영역)

CT 실태: `collaboration_conversation`(DIRECT/GROUP/PUBLIC/PRIVATE, title/description/password_hash/max_members)+멤버 role(OWNER/MANAGER/MEMBER)+초대(익명 초대 포함)+개인 채팅방 차단까지 있음 — 기본기는 TT보다 낫다.

| 기능 | TT 메커니즘 요약 | CT 현재 상태 | 이식 설계 요점 | 우선순위 | 규모 |
| --- | --- | --- | --- | --- | --- |
| 채팅방 개설자 설정 전반 | 방 설정(공개/비공개, 비밀번호, 정원, 초대 권한, 공지, 강퇴/양도 등) 개설자 제어 | **부분** — 방 유형/비밀번호/정원/역할 3단/초대·수락 흐름 있음. 방 설정 **수정** UI·공지·강퇴/양도·초대 권한 위임(MANAGER에게 초대 허용 여부) 설정 미확인/부재 | **사용자 명시 요구.** conversation에 settings_json(공지 notice, 초대 가능 역할 inviteRole, 입장 승인제 joinApproval, 익명 발언 허용 allowAnonymous 등) 추가 + OWNER 전용 설정 시트, MANAGER 위임 범위, 강퇴(REMOVED 상태 기존재)·소유권 양도 API. 개인 차단(conversation_block)과 충돌 없게 표면 키 재사용 | P2 | M |

---

## 9. P1 구현 순서 제안

의존성: ①그리드 프레임워크와 ②권한 집행이 이후 모든 관리자 화면의 토대. ④기업 파이프라인이 ⑤공고 게시판의 선행. ⑥~⑨는 커뮤니티 묶음으로 병행 가능.

| 순서 | 항목 | 규모 | 의존 |
| --- | --- | --- | --- |
| 1 | 관리자 공통 그리드 프레임워크(AdminListRequest/PageResult + useAdminList/AdminDataGrid + export/bulk) | L | — (팀장 승인 선행) |
| 2 | 관리자 권한 집행 계층(실효권한 UNION 쿼리 + @RequirePermission 인터셉터 + /api/admin/me + 메뉴 가드) | M | 테이블 기존재 (팀장 승인 선행) |
| 3 | 신고→권한별 관리자 알림 + opt-out (기존 NEW_REPORT 팬아웃 교체) + 신고 처리 갭(BLOCK_AUTHOR·신고자 결과 알림) | S | 2 |
| 4 | 기업 계정 파이프라인(company_application + role COMPANY + role 변경 이력 + 신뢰등급 프로필) | M | 1(관리자 승인 그리드) |
| 5 | 채용공고 게시판(카테고리+사람인식 상세 필드 + 신뢰등급별 승인 정책 + 지원 건 연동) | L | 4 |
| 6 | 리액션 8종+익명 변형+알림+토글취소 | M | — |
| 7 | 즐겨찾기/스크랩 스냅샷 + 마이페이지 스크랩 목록 | M | 6(BOOKMARK 흡수) |
| 8 | 글 구독 알림 | S | 6 |
| 9 | 복수 닉네임 프로필(작성 표시 계층 + 백필) | M | 6~8 이전 착수도 가능하나 커뮤니티 변경 충돌 최소화 위해 후순 |
| 10 | 게시판 운영자 모드(adminViewMode + includeBlocked + 인라인 액션) | M | 2(권한 가드), 3 |

P2 착수 후보(P1 소진 시): 활동 목록 공개범위 → 채팅방 개설자 설정 → 광고 3플랫폼 → 감사 로그 상관관계(request_id/flow_trace_id)+활동 로그 인터셉터 → BLUR 점진 공개 → 로그인 잠금 정책 테이블 연동.

## 10. 공통 주의사항 (AGENTS.md)

- `schema.sql`, 라우팅(`routes.ts`), 공통 컴포넌트, 인증/권한, 알림 채널 계약은 **공통 영역 — 수정 전 팀장 승인 또는 팀 합의 필수.** 위 P1의 1·2번과 users role 확장, 리액션/스크랩 테이블 추가가 모두 해당된다.
- 커밋 메시지·PR 본문에 AI 도구 표기 금지, prefix 영어+본문 한국어.
- CT `job_posting`(지원 건 내부 공고 원문)과 신규 `company_job_posting`(기업 공고 게시판)은 이름이 유사하나 별개 도메인 — 네이밍·문서에서 혼동 방지 필수.
- TT 이식 함정 재확인: ${} 보간은 sortDir(ASC/DESC 강제)만, ALLOW 룰은 priority 정렬 유지, 차단 계정에 사이트 알림 무의미(이메일 중심), 상태 컬럼 길이 여유(varchar(40)).
