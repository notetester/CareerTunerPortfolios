-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-30
--  깡통계정 온보딩 "그만"(거부) 영속 컬럼 추가.
--
--  배경:
--   빈 계정 온보딩(직무→기술→공고→…) 진행 중 "그만/취소/종료"를 입력해도
--   탈출할 길이 없어 사용자가 갇혔다(#1). 인메모리 onboardingStep 만 비우면
--   깡통계정(프로필 0 + 케이스 0)은 게이트(isBlankAccountForOnboarding)가
--   계속 true 라 다음 턴에 다시 온보딩으로 재진입한다.
--   → 대화(conversation) 단위로 "이 대화는 온보딩을 거부했다"를 DB 에 영속해
--     서버 재시작 후에도 재진입을 막는다. 권위 = DB(이 컬럼), 인메모리는 정리용.
--
--   - onboarding_declined_at : 온보딩을 "그만"으로 거부한 시각.
--                              NULL = 거부 안 함(기본). 값 있으면 재권유 안 함.
--                              (boolean 대신 시각 — 거부 시점까지 보존해 디버깅 유리)
--
--  ⚠️ MySQL 8 은 ADD COLUMN 에 IF NOT EXISTS 미지원 → 이 ALTER 는 한 번만 적용한다(재실행 시 1060 에러).
--  ⚠️ 기존 행은 DEFAULT NULL(거부 안 함)로 채워진다 — 회귀 없음.
--  ⚠️ 공유 DB 변경 → 로컬 dev DB 먼저 적용해 검증 후 운영 공유 DB 반영.
--  실행: mysql -h <host> -u <user> -p <db> < 20260630_f_chatbot_onboarding_declined.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

ALTER TABLE chatbot_conversation_memory
    ADD COLUMN onboarding_declined_at DATETIME NULL
        COMMENT '깡통계정 온보딩을 "그만"으로 거부한 시각. NULL=거부 안 함 → 이후 이 대화는 온보딩 재권유 안 함'
        AFTER title;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_conversation_memory;
-- SELECT conversation_id, user_id, onboarding_declined_at, updated_at
--   FROM chatbot_conversation_memory
--  WHERE onboarding_declined_at IS NOT NULL
--  ORDER BY updated_at DESC LIMIT 5;
