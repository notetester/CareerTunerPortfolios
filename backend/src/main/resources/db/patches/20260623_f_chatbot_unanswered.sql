-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-23
--  관리자 AI 상담 운영 패널 1단계: 챗봇이 FAQ로 답 못한 질문 수집 테이블.
--
--  배경:
--   챗봇 FAQ fast-path(ChatbotController.faqFastPath)가 임계 미달로
--   "관련 안내를 찾지 못했어요" 를 돌려준 순간의 사용자 원문 질문을 적재한다.
--   운영자가 "챗봇이 뭘 못 답하나"를 발견해 FAQ로 키워가기 위한 1차 데이터.
--   - 정상 미스(임계 미달/임베딩 FAQ 0건)만 기록. Ollama 장애는 기록 안 함.
--   - 같은 질문 반복은 행을 계속 append → 조회에서 question_norm 으로 빈도 집계.
--   - 임베딩 군집화는 3단계. 1단계는 정규화 정확매칭 GROUP BY 만.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 로컬 dev DB 는 먼저 적용해 검증.
--  ⚠️ 이 패치 미적용 시: 수집 훅은 best-effort(예외 삼킴)라 챗봇 응답은
--     정상 동작하지만 적재만 안 된다. 조회 API 는 테이블 부재로 실패한다.
--  실행: mysql -h <host> -u <user> -p <db> < 20260623_f_chatbot_unanswered.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_unanswered_question (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    question        VARCHAR(1000) NOT NULL COMMENT '질문 원문(verbatim)',
    question_norm   VARCHAR(255)  NOT NULL COMMENT '정규화 키(trim+소문자+공백제거, 255 초과 절단) — 빈도 집계용',
    top_similarity  DOUBLE        NULL     COMMENT 'FAQ 최고 유사도(임계 미달; 계산 불가 시 NULL)',
    user_id         BIGINT        NULL     COMMENT '로그인 유저 id, 비로그인 NULL',
    conversation_id BIGINT        NULL     COMMENT '발생 대화 id(있으면)',
    source          VARCHAR(20)   NOT NULL DEFAULT 'FAQ_FASTPATH' COMMENT '미스 발생 경로',
    status          VARCHAR(20)   NOT NULL DEFAULT 'NEW' COMMENT 'NEW/REVIEWED/CONVERTED/DISMISSED',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_cuq_norm (question_norm),
    INDEX idx_cuq_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_unanswered_question;
-- SELECT question, COUNT(*) AS frequency, MAX(created_at) AS last_seen
--   FROM chatbot_unanswered_question
--  WHERE status = 'NEW'
--  GROUP BY question_norm
--  ORDER BY frequency DESC, last_seen DESC;
