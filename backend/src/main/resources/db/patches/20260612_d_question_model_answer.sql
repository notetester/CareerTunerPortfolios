-- =====================================================================
--  D 담당(가상 면접) 스키마 마이그레이션 — 2026-06-12
--  interview_question 에 모범답안(답안지) 컬럼 추가.
--
--  배경:
--   "모범답안 보기"로 생성한 모범답안을 질문에 저장해두면, 이후 채점이 그 답안을
--   만점 기준 답안지로 재사용한다. 블라인드(모범답안 미표시)인 복습 테스트도
--   저장된 모범답안을 기준으로 채점되도록 하는 것이 목적이다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 컬럼 추가라 아래 가드형 ALTER 로 처리한다(재실행 안전).
--  실행: mysql -h <host> -u <user> -p <db> < 20260612_d_question_model_answer.sql
--
--  ✅ 적용 이력: 2026-06-12 운영 공유 DB(team1_db @ 54.116.80.214) 적용 완료
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

-- ── interview_question.model_answer 추가 ────────────────────────────
SET @has_model_answer := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'interview_question'
      AND COLUMN_NAME = 'model_answer'
);
SET @ddl_model_answer := IF(@has_model_answer = 0,
    'ALTER TABLE interview_question
        ADD COLUMN model_answer MEDIUMTEXT NULL AFTER question',    -- 모범답안(답안지), 채점 기준
    'DO 0');
PREPARE stmt FROM @ddl_model_answer;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW COLUMNS FROM interview_question LIKE 'model_answer';
