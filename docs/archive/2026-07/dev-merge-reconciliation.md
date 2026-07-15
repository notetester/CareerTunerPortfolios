# dev 병합 — 두 병행 구현 합체 결정 (2026-07-05)

> **보관 문서:** 2026-07-05 병합 시점의 선택 근거다. 현재 구조는 런타임 소스와 정본 문서를 확인한다.

내 브랜치(LEE-JEONG-GUCK-ANDROID-NATIVE-PLATFORM)와 origin/dev 가 같은 기능(광고·기업/채용·프로필/닉네임)을 독립 구현해 충돌했다.
"서로 장점만 합체" 원칙으로 아래와 같이 결정한다. 각 기능은 **더 완성·통합된 쪽을 base 로 채택**하고 상대의 명확한 장점만 이식한다.

## 결정 요약

| 기능 | 채택(base) | 폐기 | 상대에서 이식한 장점 |
| --- | --- | --- | --- |
| 광고 | 내 것 `com.careertuner.ads`(advertisement, IntersectionObserver, file_asset 업로드, weighted, 508줄 admin, mock) | dev `com.careertuner.ad`(admin_ad_campaign) | dev의 `ad_impression_log`(이벤트 로그) + `visible_to_plans_json`(플랜별 노출) + `creative_type`/`body` |
| 기업/채용 | 내 것 company/companyjobposting(role=COMPANY, 감사 이력, 추천 알림 엔진, 사람인식 필드) | dev `enterprise` 도메인·프런트 | dev의 커뮤니티 자동발행(RECOMMENDED_JOB 카테고리), max_active_posts, 신청 양식 필드 |
| 프로필/닉네임 | 내 것 정규화(user_nickname_profile 전역UNIQUE, conversation_member_profile, user_resume_detail) | dev user_chat_profile·user_profile JSON 프로필 | (없음 — 내 정규화가 전 항목 우위) |
| 채팅방 설정 | 내 것(정규화 permission/ban/invite_allow/audit 테이블) | dev member 직접컬럼·settings_json | dev의 join_policy/roomProfileRequired 개념(후속) |
| users 컬럼 | 내 것 login_id/phone/phone_verified | dev account_type/enterprise_trusted(내 role=COMPANY 유지로 불필요) | phone 길이 40 확대 |
| community 카테고리 | 내 것(후기 카테고리 — 추천 엔진 의존) | — | dev의 RECOMMENDED_JOB 카테고리 1종 추가 |
| build.gradle | 양쪽 다 keep | — | dev의 langchain4j http-client 충돌 exclude(런타임 픽스) |

## 근거
- 내 구현은 이미 알림·추천·감사·개인차단 코어와 정합돼 있고 PR base 라 churn 최소.
- dev 광고/기업은 완성형이나 내 것이 기능·완성도에서 우위(광고 admin 508줄 vs 124줄, 정규화 프로필 vs JSON). 상대의 데이터 자산(이벤트 로그·플랜별 노출·커뮤니티 자동발행)만 흡수.
- role: 내 company 도메인이 role=COMPANY 로 동작하므로 dev account_type=EMPLOYER 채택 시 재작성 비용이 큼 → role=COMPANY 유지, dev account_type/enterprise_trusted 는 소비처(enterprise) 폐기로 불필요.

## 빌드 정합 정리(컴파일러 기준 마무리)
채택본이 내 HEAD 이므로, dev 가 확장했던 죽은 표면(내 서비스가 읽지 않는 필드)은 HEAD 로 되돌려 정합을 맞췄다.

- `ChatbotController.save(...)` — dev 의 19-인자 `UserProfileRequest` 호출을 내 12-필드 레코드에 맞춰 복원(온보딩 저장 로직 자체는 dev 개선분 유지: 이탈질문 가드·재시작 화이트리스트·JobPostingService).
- `AuthServiceImpl` — dev 의 `.accountType("PERSONAL")`·`.enterpriseTrusted(false)` 빌더 호출 제거(User 도메인에 해당 필드 없음, role 기반 유지).
- collaboration DTO — `JoinConversationRequest`(1필드), `CreateConversationRequest`(5필드)를 HEAD 로 복원. dev 가 create/join 시점에 얹은 방설정 필드는 내 `createConversation`/`joinConversation`(HEAD)이 읽지 않는 죽은 표면이라 폐기. 방설정은 내 별도 `ConversationSettingsUpdateRequest` 흐름 유지.
- DB 패치 `20260703_company_ads_chat_profile.sql` 삭제 — dev 의 드롭 대상 스키마(account_type·enterprise_*·admin_ad_campaign·user_profile JSON·user_chat_profile) 생성 마이그레이션. 내 정규화 마이그레이션(`20260705b_ads.sql`·`20260705b_conversation_settings.sql`·`20260705b_profiles.sql`·`20260705_company_jobboard.sql`)이 대체.
- 유지된 dev 순증분: `admin_security_operations`(AdminSecurityOps 콘솔)·`company_search_cache`(기업 분석 캐시) — 소비 코드 생존 확인 후 존치.

## 검증
- backend `gradlew test` (AI_OLLAMA_FALLBACK_ENABLED=false) — BUILD SUCCESSFUL.
- frontend `npm run typecheck` — 통과.

## 팀 확인 요망(PR 본문에 명시)
- 기업/광고 기능에서 팀원의 병행 구현을 폐기하고 내 구현을 채택했다. 팀 정책상 반대면 되돌린다.
