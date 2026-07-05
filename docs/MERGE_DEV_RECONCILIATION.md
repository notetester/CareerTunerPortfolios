# dev 병합 — 두 병행 구현 합체 결정 (2026-07-05)

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

## 팀 확인 요망(PR 본문에 명시)
- 기업/광고 기능에서 팀원의 병행 구현을 폐기하고 내 구현을 채택했다. 팀 정책상 반대면 되돌린다.
